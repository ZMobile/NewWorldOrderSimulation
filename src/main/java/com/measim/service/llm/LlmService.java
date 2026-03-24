package com.measim.service.llm;

import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentAction;
import com.measim.model.llm.LlmRequest;
import com.measim.model.llm.LlmResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface LlmService {
    CompletableFuture<AgentAction> escalateDecision(Agent agent, String spatialContext,
                                                     String decisionContext, int currentTick);
    CompletableFuture<AgentAction> escalateDecision(Agent agent, String spatialContext,
                                                     String decisionContext, int currentTick,
                                                     String tradeOfferContext);
    CompletableFuture<List<AgentAction>> batchEscalate(List<EscalationRequest> requests);
    CompletableFuture<LlmResponse> queryGameMaster(String systemPrompt, String userPrompt);
    CompletableFuture<LlmResponse> queryGameMasterWithModel(String model, String systemPrompt, String userPrompt);

    /**
     * Run a multi-turn tool conversation with the GM.
     * The GM can call tools to inspect the simulation, and the toolHandler executes them.
     * Loops until the GM produces a final text response (end_turn).
     *
     * @param request Initial request with tool definitions
     * @param toolHandler Function: (toolName, toolInput) -> result string
     * @param maxTurns Safety cap on conversation turns
     * @return Final LlmResponse with aggregated text content
     */
    CompletableFuture<LlmResponse> runToolConversation(
            LlmRequest request,
            Function<LlmResponse.ToolUseBlock, String> toolHandler,
            int maxTurns);

    boolean isAvailable();
    double totalSpent();
    double budgetRemaining();
    int totalCalls();

    record EscalationRequest(Agent agent, String spatialContext, String decisionContext, int currentTick) {}
}
