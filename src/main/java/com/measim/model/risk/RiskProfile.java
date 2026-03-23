package com.measim.model.risk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A collection of risks attached to an entity, plus the state needed to evaluate them.
 *
 * TWO LAYERS OF RISK KNOWLEDGE:
 *   1. TRUE risk profile: The actual risks as set by the GM. The simulation engine uses this
 *      to determine what ACTUALLY happens. This is ground truth.
 *   2. PERCEIVED risk profile: What the agent THINKS the risks are. Agents estimate risks
 *      based on their experience, intelligence, and available information. They may
 *      underestimate or overestimate. This is what drives agent DECISIONS.
 *
 * The GM sets true risks. Agents build perceived risks through observation and experience.
 * The gap between perceived and true risk is where interesting behavior emerges —
 * overconfident agents take on too much risk, cautious agents miss opportunities.
 */
public class RiskProfile {

    private final String entityId;
    private final EntityType entityType;
    private final int creationTick;

    // TRUE risks: what the GM set. Used by the engine. Agents DON'T see this directly.
    private final List<Risk> trueRisks;

    // Entity state tracked for evolution model evaluation
    private double usageIntensity;
    private int lastMaintenanceTick;
    private double neighborRiskLoad;

    public RiskProfile(String entityId, EntityType entityType, int creationTick) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.creationTick = creationTick;
        this.trueRisks = new ArrayList<>();
        this.usageIntensity = 0;
        this.lastMaintenanceTick = creationTick;
        this.neighborRiskLoad = 0;
    }

    // True risk management (GM sets these)
    public void addTrueRisk(Risk risk) { trueRisks.add(risk); }
    public void removeTrueRisk(String riskId) { trueRisks.removeIf(r -> r.id().equals(riskId)); }
    public void replaceTrueRisk(Risk updated) {
        trueRisks.removeIf(r -> r.id().equals(updated.id()));
        trueRisks.add(updated);
    }
    public List<Risk> trueRisks() { return Collections.unmodifiableList(trueRisks); }

    // Entity state for evolution model
    public void recordMaintenance(int tick) { this.lastMaintenanceTick = tick; }
    public void setUsageIntensity(double usage) { this.usageIntensity = Math.max(0, Math.min(1, usage)); }
    public void setNeighborRiskLoad(double load) { this.neighborRiskLoad = Math.max(0, load); }

    public int ageTicks(int currentTick) { return currentTick - creationTick; }
    public int ticksSinceLastMaintenance(int currentTick) { return currentTick - lastMaintenanceTick; }

    public String entityId() { return entityId; }
    public EntityType entityType() { return entityType; }
    public int creationTick() { return creationTick; }
    public boolean hasRisks() { return !trueRisks.isEmpty(); }
    public double usageIntensity() { return usageIntensity; }
    public double neighborRiskLoad() { return neighborRiskLoad; }

    public enum EntityType {
        INFRASTRUCTURE,
        RESOURCE_NODE,
        PRODUCTION_CHAIN,
        AGENT,
        TILE,
        MARKET,
        WORLD
    }
}
