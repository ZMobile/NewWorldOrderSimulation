package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.dao.MetricsDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.MemoryEntry;
import com.measim.model.config.SimulationConfig;
import com.measim.model.gamemaster.WorldEvent;
import com.measim.service.gamemaster.GameMasterService;
import com.measim.service.gamemaster.GameMasterService.WorldState;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.EnvironmentService;
import com.measim.service.economy.CreditFlowService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 9: The Game Master's turn.
 *
 * Each tick the GM:
 * 1. Processes ready research proposals (existing)
 * 2. Adjudicates novel agent actions from all archetypes
 * 3. Potentially generates spontaneous world events based on world state
 * 4. Periodically audits world coherence (every 12 ticks / yearly)
 *
 * Every archetype has a pathway to GM interaction via novel actions.
 * The GM maintains the simulation's narrative and mechanical coherence.
 */
@Singleton
public class EventPhase implements TickPhase {

    private final SimulationConfig config;
    private final GameMasterService gameMasterService;
    private final AgentDao agentDao;
    private final MetricsDao metricsDao;
    private final EnvironmentService environmentService;
    private final CreditFlowService creditFlowService;

    @Inject
    public EventPhase(SimulationConfig config, GameMasterService gameMasterService,
                       AgentDao agentDao, MetricsDao metricsDao,
                       EnvironmentService environmentService, CreditFlowService creditFlowService) {
        this.config = config;
        this.gameMasterService = gameMasterService;
        this.agentDao = agentDao;
        this.metricsDao = metricsDao;
        this.environmentService = environmentService;
        this.creditFlowService = creditFlowService;
    }

    @Override public String name() { return "Events"; }
    @Override public int order() { return 10; }

    @Override
    public void execute(int currentTick) {
        List<WorldEvent> allEvents = new ArrayList<>();

        // 1. Process ready research proposals
        var discoveries = gameMasterService.processReadyProposals(currentTick);
        for (var discovery : discoveries) {
            System.out.printf("    [Discovery] %s (cat %d) by %s%n",
                    discovery.name(), discovery.category().level(), discovery.discovererId());
        }

        // 2. Adjudicate novel agent actions (from all archetypes)
        var novelEvents = gameMasterService.adjudicateNovelActions(currentTick);
        allEvents.addAll(novelEvents);
        for (var event : novelEvents) {
            applyEventToAgents(event, currentTick);
            System.out.printf("    [Novel Action] %s: %s%n", event.name(), event.description());
        }

        // 3. Generate spontaneous world events (GM's initiative)
        WorldState worldState = buildWorldState(currentTick);
        var spontaneousEvents = gameMasterService.generateWorldEvents(currentTick, worldState);
        allEvents.addAll(spontaneousEvents);
        for (var event : spontaneousEvents) {
            applyWorldEvent(event, currentTick);
            System.out.printf("    [World Event] %s (severity %.1f): %s%n",
                    event.name(), event.severity(), event.description());
        }

        // 4. World coherence audit (yearly)
        if (currentTick % config.ticksPerYear() == 0) {
            var corrections = gameMasterService.auditWorldCoherence(currentTick, worldState);
            allEvents.addAll(corrections);
            for (var correction : corrections) {
                applyCoherenceCorrection(correction, currentTick);
                System.out.printf("    [Coherence] %s: %s%n", correction.name(), correction.description());
            }
        }

        // Broadcast events to all agents' memory
        if (!allEvents.isEmpty()) {
            String eventSummary = allEvents.stream()
                    .map(e -> e.name() + ": " + e.description())
                    .reduce((a, b) -> a + "; " + b).orElse("");
            for (Agent agent : agentDao.getAllAgents()) {
                agent.addMemory(new MemoryEntry(currentTick, "WORLD_EVENT",
                        eventSummary.substring(0, Math.min(200, eventSummary.length())),
                        0.7, null, 0));
            }
        }
    }

    private WorldState buildWorldState(int currentTick) {
        var latest = metricsDao.getLatest();
        List<String> recentEvents = List.of(); // GM maintains its own log
        if (latest != null) {
            return new WorldState(
                    latest.environmentalHealth(), latest.giniCoefficient(),
                    latest.satisfactionMean(), latest.averageCredits(),
                    latest.agentCount(), latest.totalRobots(),
                    gameMasterService.discoveryCount(), currentTick,
                    config.ticksPerYear(), environmentService.crisisTileCount(),
                    creditFlowService.ubiPool(), recentEvents);
        }
        return new WorldState(0.8, 0.3, 0.5, 1000,
                agentDao.getAgentCount(), 0, 0, currentTick,
                config.ticksPerYear(), 0, 0, recentEvents);
    }

    private void applyEventToAgents(WorldEvent event, int currentTick) {
        Object agentId = event.parameters().get("agentId");
        if (agentId != null) {
            Agent agent = agentDao.getAgent(agentId.toString());
            if (agent != null) {
                Object creditChange = event.parameters().get("creditChange");
                if (creditChange instanceof Number n) {
                    double change = n.doubleValue();
                    if (change > 0) agent.state().addCredits(change);
                    else agent.state().spendCredits(Math.abs(change));
                }
                Object satisfactionChange = event.parameters().get("satisfactionChange");
                if (satisfactionChange instanceof Number n) {
                    agent.state().setSatisfaction(agent.state().satisfaction() + n.doubleValue());
                }
            }
        }
    }

    private void applyWorldEvent(WorldEvent event, int currentTick) {
        switch (event.type()) {
            case ENVIRONMENTAL_DISASTER -> {
                // Reduce environmental health in affected area
                for (var coord : event.affectedTiles()) {
                    environmentService.applyProductionPollution(coord, event.severity() * 5);
                }
                // Also affect all agents' satisfaction
                for (Agent agent : agentDao.getAllAgents()) {
                    agent.state().setSatisfaction(agent.state().satisfaction() - event.severity() * 0.1);
                }
            }
            case SOCIAL_UNREST -> {
                // Reduce satisfaction broadly
                for (Agent agent : agentDao.getAllAgents()) {
                    agent.state().setSatisfaction(agent.state().satisfaction() - event.severity() * 0.15);
                }
            }
            case MARKET_BOOM -> {
                // Give all agents a small credit boost
                for (Agent agent : agentDao.getAllAgents()) {
                    agent.state().addCredits(event.severity() * 50);
                }
            }
            case MARKET_CRASH -> {
                // Remove credits from wealthy agents
                for (Agent agent : agentDao.getAllAgents()) {
                    if (agent.state().credits() > 2000) {
                        agent.state().spendCredits(agent.state().credits() * event.severity() * 0.1);
                    }
                }
            }
            case MIGRATION_WAVE -> {
                // Satisfaction boost for low-satisfaction agents (they're "hopeful")
                for (Agent agent : agentDao.getAllAgents()) {
                    if (agent.state().satisfaction() < 0.3) {
                        agent.state().setSatisfaction(agent.state().satisfaction() + 0.05);
                    }
                }
            }
            default -> {} // Other events are informational / handled via parameters
        }
    }

    private void applyCoherenceCorrection(WorldEvent correction, int currentTick) {
        Object satisfactionDelta = correction.parameters().get("satisfactionDelta");
        if (satisfactionDelta instanceof Number n) {
            for (Agent agent : agentDao.getAllAgents()) {
                agent.state().setSatisfaction(agent.state().satisfaction() + n.doubleValue());
            }
        }
    }
}
