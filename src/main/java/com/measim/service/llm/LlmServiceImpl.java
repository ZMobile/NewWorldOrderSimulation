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
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(new AgentAction.Idle());
        }

        String model = selectModel(agent);
        String systemPrompt = ArchetypePrompts.systemPrompt(agent);
        String userPrompt = ArchetypePrompts.userPrompt(agent, spatialContext, decisionContext, currentTick);

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

        // Group by archetype for batching — same-archetype agents with similar state share one call
        Map<String, List<EscalationRequest>> grouped = new LinkedHashMap<>();
        for (EscalationRequest req : requests) {
            String key = req.agent().identity().archetype().name();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
        }

        List<CompletableFuture<AgentAction>> futures = new ArrayList<>();
        for (var group : grouped.values()) {
            // For each group, make one call and apply to all agents in group
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

    @Override public boolean isAvailable() { return llmDao.isAvailable(); }
    @Override public double totalSpent() { return llmDao.totalSpent(); }
    @Override public double budgetRemaining() { return llmDao.budgetRemaining(); }
    @Override public int totalCalls() { return llmDao.totalCalls(); }

    private String selectModel(Agent agent) {
        // All agents use Sonnet. Opus reserved for GM coherence audit + world events only.
        return config.agentModel();
    }
}
