package com.measim.service.llm;

import com.measim.dao.LlmDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentAction;
import com.measim.model.config.SimulationConfig;
import com.measim.model.llm.LlmRequest;
import com.measim.model.llm.LlmResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Singleton
public class LlmServiceImpl implements LlmService {

    private final LlmDao llmDao;
    private final SimulationConfig config;

    @Inject
    public LlmServiceImpl(LlmDao llmDao, SimulationConfig config) {
        this.llmDao = llmDao;
        this.config = config;
    }

    @Override
    public CompletableFuture<AgentAction> escalateDecision(Agent agent, String spatialContext,
                                                            String decisionContext, int currentTick) {
        return escalateDecision(agent, spatialContext, decisionContext, currentTick, "None");
    }

    @Override
    public CompletableFuture<AgentAction> escalateDecision(Agent agent, String spatialContext,
                                                            String decisionContext, int currentTick,
                                                            String tradeOfferContext) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new AgentAction.Idle());
        }

        String model = selectModel(agent);
        String systemPrompt = ArchetypePrompts.systemPrompt(agent);
        String userPrompt = ArchetypePrompts.userPrompt(agent, spatialContext, decisionContext,
                currentTick, tradeOfferContext);

        LlmRequest request = LlmRequest.agentDecision(model, systemPrompt, userPrompt);

        return llmDao.sendRequest(request)
                .thenApply(response -> LlmResponseParser.parseAgentAction(response.content()));
    }

    @Override
    public CompletableFuture<List<AgentAction>> batchEscalate(List<EscalationRequest> requests) {
        if (!isAvailable() || requests.isEmpty()) {
            return CompletableFuture.completedFuture(
                    requests.stream().map(r -> (AgentAction) new AgentAction.Idle()).toList());
        }

        Map<String, List<EscalationRequest>> grouped = new LinkedHashMap<>();
        for (EscalationRequest req : requests) {
            String key = req.agent().identity().archetype().name();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
        }

        List<CompletableFuture<AgentAction>> futures = new ArrayList<>();
        for (var group : grouped.values()) {
            EscalationRequest representative = group.getFirst();
            CompletableFuture<AgentAction> future = escalateDecision(
                    representative.agent(), representative.spatialContext(),
                    representative.decisionContext(), representative.currentTick());
            for (int i = 0; i < group.size(); i++) {
                futures.add(future);
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    @Override
    public CompletableFuture<LlmResponse> queryGameMaster(String systemPrompt, String userPrompt) {
        return queryGameMasterWithModel(config.gameMasterModel(), systemPrompt, userPrompt);
    }

    @Override
    public CompletableFuture<LlmResponse> queryGameMasterWithModel(String model, String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(LlmResponse.empty());
        }
        LlmRequest request = LlmRequest.gameMaster(model, systemPrompt, userPrompt);
        return llmDao.sendRequest(request);
    }

    @Override
    public CompletableFuture<LlmResponse> runToolConversation(
            LlmRequest request,
            Function<LlmResponse.ToolUseBlock, String> toolHandler,
            int maxTurns) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(LlmResponse.empty());
        }
        return runToolLoop(request, toolHandler, maxTurns, 0, 0.0);
    }

    /**
     * Recursive async loop: send request, if tool_use -> execute tools -> continue.
     * Aggregates cost across all turns.
     */
    private CompletableFuture<LlmResponse> runToolLoop(
            LlmRequest request,
            Function<LlmResponse.ToolUseBlock, String> toolHandler,
            int turnsRemaining,
            int totalInputTokens,
            double totalCost) {

        return llmDao.sendRequest(request).thenCompose(response -> {
            int newInputTokens = totalInputTokens + response.inputTokens();
            double newCost = totalCost + response.costUsd();

            // If no tool use, out of turns, or budget low, return the final response
            if (!response.hasToolUse() || turnsRemaining <= 0 || llmDao.budgetRemaining() < 0.10) {
                if (llmDao.budgetRemaining() < 0.10 && response.hasToolUse()) {
                    System.out.println("        [GM Tool] Budget low, forcing final response");
                }
                return CompletableFuture.completedFuture(new LlmResponse(
                        response.content(), newInputTokens, response.outputTokens(),
                        newCost, response.model(), false,
                        response.stopReason(), response.toolUseBlocks()));
            }

            // Execute each tool call and collect results
            List<LlmRequest.ContentBlock> toolResults = new ArrayList<>();
            for (var toolCall : response.toolUseBlocks()) {
                System.out.printf("        [GM Tool] %s(%s)%n", toolCall.name(),
                        toolCall.input().toString().substring(0, Math.min(80, toolCall.input().toString().length())));
                System.out.flush();
                try {
                    String result = toolHandler.apply(toolCall);
                    toolResults.add(LlmRequest.ContentBlock.toolResult(toolCall.id(), result));
                } catch (Exception e) {
                    toolResults.add(LlmRequest.ContentBlock.toolResult(
                            toolCall.id(), "Error: " + e.getMessage()));
                }
            }

            // Build next turn: append assistant's tool_use + our tool_results
            List<LlmRequest.Message> newMessages = new ArrayList<>(request.messages());
            newMessages.add(LlmRequest.Message.assistantToolUse(response.toAssistantContentBlocks()));
            newMessages.add(LlmRequest.Message.toolResults(toolResults));

            LlmRequest nextRequest = request.withMessages(newMessages);
            return runToolLoop(nextRequest, toolHandler, turnsRemaining - 1, newInputTokens, newCost);
        });
    }

    @Override public boolean isAvailable() { return llmDao.isAvailable(); }
    @Override public double totalSpent() { return llmDao.totalSpent(); }
    @Override public double budgetRemaining() { return llmDao.budgetRemaining(); }
    @Override public int totalCalls() { return llmDao.totalCalls(); }

    private String selectModel(Agent agent) {
        return config.agentModel();
    }
}
