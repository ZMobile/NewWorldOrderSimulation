package com.measim.service.llm;

import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentAction;
import com.measim.model.llm.LlmResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface LlmService {
    CompletableFuture<AgentAction> escalateDecision(Agent agent, String spatialContext,
                                                     String decisionContext, int currentTick);
    CompletableFuture<List<AgentAction>> batchEscalate(List<EscalationRequest> requests);
    CompletableFuture<LlmResponse> queryGameMaster(String systemPrompt, String userPrompt);
    boolean isAvailable();
    double totalSpent();
    double budgetRemaining();
    int totalCalls();

    record EscalationRequest(Agent agent, String spatialContext, String decisionContext, int currentTick) {}
}
