package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.dao.RiskDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.MemoryEntry;
import com.measim.model.config.SimulationConfig;
import com.measim.model.risk.PerceivedRisk;
import com.measim.model.risk.RiskEvent;
import com.measim.model.world.Tile;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Phase 1: Agents observe their environment and update their risk perceptions.
 * Agents don't see TRUE risk — they build PERCEIVED risk from observation and experience.
 */
@Singleton
public class PerceptionPhase implements TickPhase {
    private final AgentDao agentDao;
    private final WorldDao worldDao;
    private final RiskDao riskDao;
    private final SimulationConfig config;

    @Inject
    public PerceptionPhase(AgentDao agentDao, WorldDao worldDao, RiskDao riskDao,
                            SimulationConfig config) {
        this.agentDao = agentDao;
        this.worldDao = worldDao;
        this.riskDao = riskDao;
        this.config = config;
    }

    @Override public String name() { return "Perception"; }
    @Override public int order() { return 1; }

    @Override
    public void execute(int currentTick) {
        // Get recent risk events for perception updates
        List<RiskEvent> recentEvents = riskDao.getRecentEvents(3, currentTick);

        for (Agent agent : agentDao.getAllAgents()) {
            Tile tile = worldDao.getTile(agent.state().location());
            if (tile == null) continue;

            // Standard observation
            agent.addMemory(new MemoryEntry(currentTick, "OBSERVATION",
                    String.format("At (%d,%d), terrain=%s, envHealth=%.2f, resources=%d",
                            tile.coord().q(), tile.coord().r(), tile.terrain(),
                            tile.environment().averageHealth(), tile.resources().size()),
                    0.3, null, 0));

            // Update risk perceptions from observed events within perception radius
            for (RiskEvent event : recentEvents) {
                // Agent can only perceive events within their perception radius
                // (we approximate location for non-infrastructure entities)
                boolean canObserve = isWithinPerception(agent, event, config.perceptionRadius());
                if (!canObserve) continue;

                // Update or create perceived risk for this entity
                var existing = riskDao.getAgentPerceptionOf(agent.id(), event.entityId());
                PerceivedRisk updated;
                if (existing.isPresent()) {
                    updated = existing.get().updatedWith(
                            event.severity() * 0.5, event.severity(),
                            "observation", currentTick);
                } else {
                    updated = PerceivedRisk.initialEstimate(
                            event.riskId(), event.entityId(), agent.id(),
                            agent.identity().riskTolerance(),
                            agent.identity().complianceDisposition(), currentTick);
                    // Then immediately update with what they observed
                    updated = updated.updatedWith(
                            event.severity() * 0.5, event.severity(),
                            "observation", currentTick);
                }
                riskDao.recordPerceivedRisk(updated);

                agent.addMemory(new MemoryEntry(currentTick, "RISK_OBSERVATION",
                        String.format("Observed: %s on %s (severity %.2f)",
                                event.riskName(), event.entityId(), event.severity()),
                        0.6, null, 0));
            }
        }
    }

    private boolean isWithinPerception(Agent agent, RiskEvent event, int radius) {
        // Simple approximation: check if any infrastructure/entity with this ID is nearby
        // In a full implementation, we'd look up the entity's location
        // For now, agents can observe all events (information spreads)
        return true; // TODO: proper spatial filtering
    }
}
