package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.dao.MarketDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentAction;
import com.measim.model.agent.Archetype;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.*;
import com.measim.service.agent.AgentDecisionService;
import com.measim.service.llm.LlmService;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.PathfindingService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 2: Agent decisions.
 *
 * Two tiers:
 *   Tier 1 (all agents, every tick): Deterministic utility calculator. Fast.
 *   Tier 2 (eligible agents, LLM): Creative strategic reasoning. Replaces Tier 1 action.
 *
 * LLM escalation triggers when:
 *   - Agent has enough credits to take meaningful action (>100)
 *   - Trigger conditions met based on archetype and world state
 *   - LLM budget permits
 *   - Max N agent LLM calls per tick (configurable)
 */
@Singleton
public class DecisionPhase implements TickPhase {
    private final AgentDao agentDao;
    private final MarketDao marketDao;
    private final WorldDao worldDao;
    private final AgentDecisionService decisionService;
    private final LlmService llmService;
    private final com.measim.service.trade.TradeService tradeService;
    private final SimulationConfig config;
    private final Map<String, AgentAction> pendingActions = new HashMap<>();

    @Inject
    public DecisionPhase(AgentDao agentDao, MarketDao marketDao, WorldDao worldDao,
                          AgentDecisionService decisionService, LlmService llmService,
                          com.measim.service.trade.TradeService tradeService,
                          SimulationConfig config) {
        this.agentDao = agentDao;
        this.marketDao = marketDao;
        this.worldDao = worldDao;
        this.decisionService = decisionService;
        this.llmService = llmService;
        this.tradeService = tradeService;
        this.config = config;
    }

    @Override public String name() { return "Decision"; }
    @Override public int order() { return 20; }

    @Override
    public void execute(int currentTick) {
        pendingActions.clear();
        Map<ItemType, Double> prices = buildPriceSnapshot();

        // Tier 1: deterministic decisions for ALL agents
        long t1Start = System.currentTimeMillis();
        for (var agent : agentDao.getAllAgents()) {
            pendingActions.put(agent.id(), decisionService.decideStrategicAction(agent, prices, currentTick));
        }
        long t1Elapsed = System.currentTimeMillis() - t1Start;

        // Summarize Tier 1 action distribution
        Map<String, Integer> actionCounts = new java.util.LinkedHashMap<>();
        for (var action : pendingActions.values()) {
            String name = action.getClass().getSimpleName();
            actionCounts.merge(name, 1, Integer::sum);
        }
        System.out.printf("    [Decision] Tier 1 (%dms): %s%n", t1Elapsed, actionCounts);
        System.out.flush();

        // Tier 2: LLM escalation for eligible agents (replaces their Tier 1 action)
        if (llmService.isAvailable()) {
            List<Agent> escalationCandidates = agentDao.getAllAgents().stream()
                    .filter(a -> shouldEscalateToLlm(a, currentTick))
                    .toList();

            if (!escalationCandidates.isEmpty()) {
                int maxCalls = config.maxAgentCallsPerTick();
                List<Agent> toEscalate = escalationCandidates.size() > maxCalls
                        ? escalationCandidates.subList(0, maxCalls)
                        : escalationCandidates;

                System.out.printf("    [Decision] Escalating %d/%d agents to LLM...%n",
                        toEscalate.size(), agentDao.getAgentCount());
                System.out.flush();

                // Fire ALL LLM calls concurrently — no batching, no caps
                long llmStart = System.currentTimeMillis();
                var futures = toEscalate.stream()
                        .map(agent -> {
                            String spatial = buildSpatialContext(agent);
                            String decision = "Strategic decision for tick " + currentTick;
                            // Build trade context: pending offers + visible open offers
                            var incoming = tradeService.getIncomingOffers(agent.id());
                            var visible = tradeService.getVisibleOpenOffers(agent.id());
                            StringBuilder tradeSb = new StringBuilder();
                            if (!incoming.isEmpty()) {
                                tradeSb.append("Direct offers to you:\n");
                                for (var o : incoming) tradeSb.append("  ").append(o.id()).append(": ").append(o.summary()).append("\n");
                            }
                            if (!visible.isEmpty()) {
                                tradeSb.append("Open offers nearby:\n");
                                for (var o : visible) tradeSb.append("  ").append(o.id()).append(": ").append(o.summary()).append("\n");
                            }
                            String tradeCtx = tradeSb.isEmpty() ? "None" : tradeSb.toString();

                            return Map.entry(agent.id(),
                                    llmService.escalateDecision(agent, spatial, decision, currentTick, tradeCtx));
                        })
                        .toList();

                CompletableFuture.allOf(futures.stream()
                        .map(Map.Entry::getValue)
                        .toArray(CompletableFuture[]::new)).join();

                int llmActions = 0;
                for (var entry : futures) {
                    AgentAction llmAction = entry.getValue().join();
                    if (!(llmAction instanceof AgentAction.Idle)) {
                        pendingActions.put(entry.getKey(), llmAction);
                        llmActions++;
                    }
                }
                System.out.printf("    [Decision] LLM done (%dms): %d/%d produced actions%n",
                        System.currentTimeMillis() - llmStart, llmActions, toEscalate.size());
                System.out.flush();
            }
        }
    }

    public Map<String, AgentAction> pendingActions() { return Collections.unmodifiableMap(pendingActions); }

    /**
     * Should this agent's decision be escalated to LLM this tick?
     * Not every agent every tick — that would be too expensive.
     * Escalation happens for specific conditions per archetype.
     */
    private boolean shouldEscalateToLlm(Agent agent, int currentTick) {
        if (agent.state().credits() < 50) return false; // too broke to do anything interesting

        var profile = agent.identity();
        return switch (profile.archetype()) {
            // Creative archetypes escalate more often
            case ENTREPRENEUR, INNOVATOR -> currentTick % 3 == 0;
            case ARTISAN, PROVIDER -> currentTick % 4 == 0;

            // Strategic archetypes escalate periodically
            case POLITICIAN, ORGANIZER, REGULATOR -> currentTick % 6 == 0;
            case SPECULATOR, LANDLORD, ACCUMULATOR -> currentTick % 4 == 0 && agent.state().credits() > 200;

            // Adversarial archetypes escalate when conditions are right
            case EXPLOITER -> currentTick % 3 == 0;
            case OPTIMIZER -> currentTick % 4 == 0 && agent.state().credits() > 300;

            // Workers/homesteaders escalate less (they're more routine)
            case WORKER, HOMESTEADER -> currentTick % 6 == 0;

            // Others
            case COOPERATOR, PHILANTHROPIST -> currentTick % 6 == 0 && agent.state().credits() > 200;
            case AUTOMATOR -> currentTick % 4 == 0;
            case FREE_RIDER -> currentTick % 12 == 0; // rarely escalates
        };
    }

    private String buildSpatialContext(Agent agent) {
        var loc = agent.state().location();
        var tile = worldDao.getTile(loc);
        if (tile == null) return "Unknown location.";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("At (%d,%d), terrain: %s, env health: %.2f. ",
                loc.q(), loc.r(), tile.terrain(), tile.environment().averageHealth()));
        if (!tile.resources().isEmpty()) {
            sb.append("Resources: ");
            for (var res : tile.resources()) {
                if (!res.isDepleted()) sb.append(res.type()).append("(").append(String.format("%.0f", res.abundance())).append(") ");
            }
        }
        if (tile.isSettlementZone()) sb.append("Settlement zone. ");
        sb.append(String.format("Structures: %d.", tile.structureIds().size()));
        return sb.toString();
    }

    Map<ItemType, Double> buildPriceSnapshot() {
        Map<ItemType, Double> prices = new HashMap<>();
        for (ResourceType r : ResourceType.values()) prices.put(ItemType.of(r), 2.0);
        for (ProductType p : ProductType.values()) prices.put(ItemType.of(p), 10.0);
        for (var market : marketDao.getAllMarkets()) {
            for (ResourceType r : ResourceType.values()) {
                double p = market.getLastPrice(ItemType.of(r));
                if (p > 0) prices.put(ItemType.of(r), p);
            }
            for (ProductType pt : ProductType.values()) {
                double p = market.getLastPrice(ItemType.of(pt));
                if (p > 0) prices.put(ItemType.of(pt), p);
            }
        }
        return prices;
    }
}
