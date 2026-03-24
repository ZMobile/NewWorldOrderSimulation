package com.measim.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.measim.model.config.SimulationConfig;
import com.measim.model.llm.LlmRequest;
import com.measim.model.llm.LlmResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

@Singleton
public class LlmDaoImpl implements LlmDao {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Map<String, Double> COST_PER_INPUT_TOKEN = Map.of(
            "claude-sonnet-4-6", 3.0 / 1_000_000,
            "claude-sonnet-4-20250514", 3.0 / 1_000_000,
            "claude-opus-4-6", 15.0 / 1_000_000
    );
    private static final Map<String, Double> COST_PER_OUTPUT_TOKEN = Map.of(
            "claude-sonnet-4-6", 15.0 / 1_000_000,
            "claude-sonnet-4-20250514", 15.0 / 1_000_000,
            "claude-opus-4-6", 75.0 / 1_000_000
    );

    private final SimulationConfig config;
    private final com.measim.service.llm.BudgetPauseHandler budgetPauseHandler;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final ExecutorService httpExecutor;
    private final ScheduledExecutorService retryScheduler;
    private final DoubleAdder totalSpent = new DoubleAdder();
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger activeCalls = new AtomicInteger(0);
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Inject
    public LlmDaoImpl(SimulationConfig config, com.measim.service.llm.BudgetPauseHandler budgetPauseHandler) {
        this.config = config;
        this.budgetPauseHandler = budgetPauseHandler;
        // Dedicated thread pool for HTTP — avoids ForkJoinPool starvation
        this.httpExecutor = Executors.newFixedThreadPool(100,
                r -> { Thread t = new Thread(r, "llm-http"); t.setDaemon(true); return t; });
        this.retryScheduler = Executors.newScheduledThreadPool(2,
                r -> { Thread t = new Thread(r, "llm-retry"); t.setDaemon(true); return t; });
        // Force HTTP/1.1 to avoid HTTP/2 "too many concurrent streams" limit.
        // With 100+ concurrent requests (agent decisions + GM tool conversations),
        // HTTP/2 multiplexing hits the server's per-connection stream cap.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .executor(httpExecutor)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return config.hasApiKey();
    }

    @Override
    public CompletableFuture<LlmResponse> sendRequest(LlmRequest request) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(
                    new LlmResponse("[LLM unavailable - no API key configured]",
                            0, 0, 0, request.model(), false));
        }

        // Check cache
        String cacheKey = buildCacheKey(request);
        if (config.cacheEnabled()) {
            CachedResponse cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return CompletableFuture.completedFuture(
                        LlmResponse.cached(cached.content, request.model()));
            }
        }

        // Check budget — pause simulation if exhausted, let user reload
        if (!canAfford(request)) {
            boolean retry = budgetPauseHandler.pauseForBudget(totalSpent.sum(), config.totalBudgetUsd());
            if (retry && canAfford(request)) {
                // User added budget — retry the same request
                return sendRequest(request);
            }
            // User cancelled or still can't afford — fall back to deterministic
            return CompletableFuture.completedFuture(
                    new LlmResponse("[LLM budget exhausted]", 0, 0, 0, request.model(), false));
        }

        int callNum = totalCalls.get() + 1;
        return callApi(request).thenApply(response -> {
            totalSpent.add(response.costUsd());
            int n = totalCalls.incrementAndGet();
            if (n % 10 == 0 || n <= 3) {
                System.out.printf("      [LLM] Call #%d (%s) - $%.4f this call, $%.2f total, %d active%n",
                        n, request.model(), response.costUsd(), totalSpent.sum(), activeCalls.get());
                System.out.flush();
            }

            if (config.cacheEnabled()) {
                cache.put(cacheKey, new CachedResponse(response.content(), System.currentTimeMillis()));
            }
            return response;
        });
    }

    @Override
    public CompletableFuture<List<LlmResponse>> sendBatch(List<LlmRequest> requests) {
        List<CompletableFuture<LlmResponse>> futures = requests.stream()
                .map(this::sendRequest)
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    @Override
    public boolean canAfford(LlmRequest request) {
        double estimatedCost = estimateCost(request);
        return totalSpent.sum() + estimatedCost <= config.totalBudgetUsd();
    }

    @Override public double totalSpent() { return totalSpent.sum(); }
    @Override public double budgetRemaining() { return config.totalBudgetUsd() - totalSpent.sum(); }
    @Override public int totalCalls() { return totalCalls.get(); }

    private CompletableFuture<LlmResponse> callApi(LlmRequest request) {
        return callApiWithRetry(request, 3);
    }

    private CompletableFuture<LlmResponse> callApiWithRetry(LlmRequest request, int retriesLeft) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", request.model());
            body.put("max_tokens", request.maxTokens());
            body.put("temperature", request.temperature());

            if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
                body.put("system", request.systemPrompt());
            }

            // Serialize tools if present
            if (request.hasTools()) {
                ArrayNode toolsNode = body.putArray("tools");
                for (var tool : request.tools()) {
                    ObjectNode toolNode = toolsNode.addObject();
                    toolNode.put("name", tool.name());
                    toolNode.put("description", tool.description());
                    toolNode.set("input_schema", mapper.valueToTree(tool.inputSchema()));
                }
            }

            // Serialize messages with content blocks
            ArrayNode messages = body.putArray("messages");
            for (LlmRequest.Message msg : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.role());
                serializeContentBlocks(msgNode, msg.contentBlocks());
            }

            String jsonBody = mapper.writeValueAsString(body);
            int active = activeCalls.incrementAndGet();

            // Scale timeout with request size — tool conversations accumulate large contexts
            int timeoutSecs = request.hasTools() ? 90 : 60;

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl() + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(timeoutSecs))
                    .build();

            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(httpResponse -> {
                        activeCalls.decrementAndGet();
                        int status = httpResponse.statusCode();
                        // Retry on rate limit (429) or server error (5xx)
                        if ((status == 429 || status >= 500) && retriesLeft > 0) {
                            int delay = status == 429 ? 5000 : 2000;
                            System.out.printf("      [LLM] %d error, retrying in %ds (%d retries left)%n",
                                    status, delay / 1000, retriesLeft);
                            // Non-blocking delay using scheduled executor
                            CompletableFuture<LlmResponse> delayed = new CompletableFuture<>();
                            retryScheduler.schedule(() ->
                                    callApiWithRetry(request, retriesLeft - 1)
                                            .thenAccept(delayed::complete)
                                            .exceptionally(ex -> { delayed.completeExceptionally(ex); return null; }),
                                    delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                            return delayed;
                        }
                        return CompletableFuture.completedFuture(parseResponse(httpResponse, request.model()));
                    })
                    .exceptionally(ex -> {
                        activeCalls.decrementAndGet();
                        if (retriesLeft > 0) {
                            System.out.printf("      [LLM] Network error, retrying (%d left): %s%n",
                                    retriesLeft, ex.getMessage());
                            // Non-blocking retry via scheduled executor
                            try {
                                CompletableFuture<LlmResponse> delayed = new CompletableFuture<>();
                                retryScheduler.schedule(() ->
                                        callApiWithRetry(request, retriesLeft - 1)
                                                .thenAccept(delayed::complete)
                                                .exceptionally(ex2 -> { delayed.completeExceptionally(ex2); return null; }),
                                        2000, java.util.concurrent.TimeUnit.MILLISECONDS);
                                return delayed.join(); // OK here: runs on httpExecutor, not ForkJoinPool
                            } catch (Exception e2) {
                                // fallthrough
                            }
                        }
                        return new LlmResponse("[LLM error after retries: " + ex.getMessage() + "]",
                                0, 0, 0, request.model(), false);
                    });

        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    new LlmResponse("[LLM error: " + e.getMessage() + "]",
                            0, 0, 0, request.model(), false));
        }
    }

    /**
     * Serialize content blocks into a message node.
     * Simple text-only messages use "content": "string".
     * Mixed content (tool_use, tool_result) uses "content": [array of blocks].
     */
    private void serializeContentBlocks(ObjectNode msgNode, List<LlmRequest.ContentBlock> blocks) {
        boolean hasNonText = blocks.stream().anyMatch(b -> b.type() != LlmRequest.ContentBlock.Type.TEXT);
        if (!hasNonText && blocks.size() == 1) {
            // Simple string content
            msgNode.put("content", blocks.getFirst().text());
        } else {
            // Array of content blocks
            ArrayNode contentArray = msgNode.putArray("content");
            for (var block : blocks) {
                ObjectNode blockNode = contentArray.addObject();
                switch (block.type()) {
                    case TEXT -> {
                        blockNode.put("type", "text");
                        blockNode.put("text", block.text());
                    }
                    case TOOL_USE -> {
                        blockNode.put("type", "tool_use");
                        blockNode.put("id", block.toolUseId());
                        blockNode.put("name", block.toolName());
                        blockNode.set("input", mapper.valueToTree(block.toolInput()));
                    }
                    case TOOL_RESULT -> {
                        blockNode.put("type", "tool_result");
                        blockNode.put("tool_use_id", block.toolUseId());
                        blockNode.put("content", block.toolResult());
                    }
                }
            }
        }
    }

    private LlmResponse parseResponse(HttpResponse<String> httpResponse, String model) {
        try {
            if (httpResponse.statusCode() != 200) {
                return new LlmResponse("[API error " + httpResponse.statusCode() + ": " +
                        httpResponse.body().substring(0, Math.min(200, httpResponse.body().length())) + "]",
                        0, 0, 0, model, false);
            }

            JsonNode root = mapper.readTree(httpResponse.body());
            String stopReason = root.path("stop_reason").asText("end_turn");

            // Parse all content blocks
            String textContent = "";
            List<LlmResponse.ToolUseBlock> toolBlocks = new ArrayList<>();
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    String type = block.path("type").asText();
                    if ("text".equals(type)) {
                        textContent = block.path("text").asText();
                    } else if ("tool_use".equals(type)) {
                        String id = block.path("id").asText();
                        String name = block.path("name").asText();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> input = mapper.convertValue(block.get("input"), Map.class);
                        toolBlocks.add(new LlmResponse.ToolUseBlock(id, name, input));
                    }
                }
            }

            JsonNode usage = root.get("usage");
            int inputTokens = usage != null ? usage.path("input_tokens").asInt(0) : 0;
            int outputTokens = usage != null ? usage.path("output_tokens").asInt(0) : 0;

            double costIn = inputTokens * COST_PER_INPUT_TOKEN.getOrDefault(model, 3.0 / 1_000_000);
            double costOut = outputTokens * COST_PER_OUTPUT_TOKEN.getOrDefault(model, 15.0 / 1_000_000);

            return new LlmResponse(textContent, inputTokens, outputTokens, costIn + costOut,
                    model, false, stopReason, toolBlocks);
        } catch (Exception e) {
            return new LlmResponse("[Parse error: " + e.getMessage() + "]", 0, 0, 0, model, false);
        }
    }

    private double estimateCost(LlmRequest request) {
        // Base estimates per call
        int estInput, estOutput;
        if (request.purpose() == LlmRequest.RequestPurpose.GAME_MASTER_ADJUDICATION) {
            estInput = 2000;
            estOutput = 1000;
        } else {
            estInput = 500;
            estOutput = 300;
        }

        // Tool conversations cost more: each turn's context grows as tool results accumulate.
        // Estimate ~4 turns average, with growing input per turn: turns 1-4 input roughly 1x,2x,3x,4x.
        // Total input ~ 10x base, total output ~ 4x base.
        if (request.hasTools()) {
            estInput *= 4;  // average across expected turns (context grows each turn)
            estOutput *= 3; // multiple output turns
        }

        // Account for actual message size if this is a continuation (later turns)
        int messageChars = request.messages().stream()
                .mapToInt(m -> m.content().length())
                .sum();
        int actualInputTokens = Math.max(estInput, messageChars / 4); // ~4 chars per token

        double costIn = actualInputTokens * COST_PER_INPUT_TOKEN.getOrDefault(request.model(), 3.0 / 1_000_000);
        double costOut = estOutput * COST_PER_OUTPUT_TOKEN.getOrDefault(request.model(), 15.0 / 1_000_000);
        return costIn + costOut;
    }

    private String buildCacheKey(LlmRequest request) {
        return request.model() + "|" + request.systemPrompt() + "|" +
                request.messages().stream().map(LlmRequest.Message::content)
                        .reduce("", (a, b) -> a + "|" + b);
    }

    private record CachedResponse(String content, long timestampMs) {
        boolean isExpired() {
            // Cache entries live for 5 minutes (configurable via cacheTtlTicks in real usage)
            return System.currentTimeMillis() - timestampMs > 300_000;
        }
    }
}
