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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final DoubleAdder totalSpent = new DoubleAdder();
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Inject
    public LlmDaoImpl(SimulationConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
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

        // Check budget
        if (!canAfford(request)) {
            return CompletableFuture.completedFuture(
                    new LlmResponse("[LLM budget exhausted]", 0, 0, 0, request.model(), false));
        }

        int callNum = totalCalls.get() + 1;
        return callApi(request).thenApply(response -> {
            totalSpent.add(response.costUsd());
            int n = totalCalls.incrementAndGet();
            if (n % 10 == 0 || n <= 3) {
                System.out.printf("      [LLM] Call #%d (%s) — $%.4f this call, $%.2f total%n",
                        n, request.model(), response.costUsd(), totalSpent.sum());
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

            ArrayNode messages = body.putArray("messages");
            for (LlmRequest.Message msg : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content());
            }

            String jsonBody = mapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiBaseUrl() + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenCompose(httpResponse -> {
                        int status = httpResponse.statusCode();
                        // Retry on rate limit (429) or server error (5xx)
                        if ((status == 429 || status >= 500) && retriesLeft > 0) {
                            int delay = status == 429 ? 5000 : 2000;
                            System.out.printf("      [LLM] %d error, retrying in %ds (%d retries left)%n",
                                    status, delay / 1000, retriesLeft);
                            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                            return callApiWithRetry(request, retriesLeft - 1);
                        }
                        return CompletableFuture.completedFuture(parseResponse(httpResponse, request.model()));
                    })
                    .exceptionally(ex -> {
                        if (retriesLeft > 0) {
                            System.out.printf("      [LLM] Network error, retrying (%d left): %s%n",
                                    retriesLeft, ex.getMessage());
                            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                            return callApiWithRetry(request, retriesLeft - 1).join();
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

    private LlmResponse parseResponse(HttpResponse<String> httpResponse, String model) {
        try {
            if (httpResponse.statusCode() != 200) {
                return new LlmResponse("[API error " + httpResponse.statusCode() + ": " +
                        httpResponse.body().substring(0, Math.min(200, httpResponse.body().length())) + "]",
                        0, 0, 0, model, false);
            }

            JsonNode root = mapper.readTree(httpResponse.body());
            String content = "";
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode block : contentArray) {
                    if ("text".equals(block.path("type").asText())) {
                        content = block.path("text").asText();
                        break;
                    }
                }
            }

            JsonNode usage = root.get("usage");
            int inputTokens = usage != null ? usage.path("input_tokens").asInt(0) : 0;
            int outputTokens = usage != null ? usage.path("output_tokens").asInt(0) : 0;

            double costIn = inputTokens * COST_PER_INPUT_TOKEN.getOrDefault(model, 3.0 / 1_000_000);
            double costOut = outputTokens * COST_PER_OUTPUT_TOKEN.getOrDefault(model, 15.0 / 1_000_000);

            return new LlmResponse(content, inputTokens, outputTokens, costIn + costOut, model, false);
        } catch (Exception e) {
            return new LlmResponse("[Parse error: " + e.getMessage() + "]", 0, 0, 0, model, false);
        }
    }

    private double estimateCost(LlmRequest request) {
        // Rough estimate: ~500 input tokens, ~300 output tokens for agent decisions
        int estInput = request.purpose() == LlmRequest.RequestPurpose.GAME_MASTER_ADJUDICATION ? 2000 : 500;
        int estOutput = request.purpose() == LlmRequest.RequestPurpose.GAME_MASTER_ADJUDICATION ? 1000 : 300;
        double costIn = estInput * COST_PER_INPUT_TOKEN.getOrDefault(request.model(), 3.0 / 1_000_000);
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
