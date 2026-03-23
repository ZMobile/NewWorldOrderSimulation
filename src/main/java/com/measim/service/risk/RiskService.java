package com.measim.service.risk;

import com.measim.model.risk.RiskEvent;
import com.measim.model.risk.RiskProfile;
import com.measim.model.world.HexCoord;

import java.util.List;

public interface RiskService {

    /**
     * Evaluate all risk profiles using evolution model. Deterministic probability.
     * GM only called for triggered risks (to adjudicate specific consequences).
     */
    List<RiskEvent> evaluateRisks(int currentTick);

    /**
     * Apply consequences of triggered risk events to the simulation.
     */
    void applyConsequences(List<RiskEvent> events, int currentTick);

    /**
     * After consequences applied, propagate cascading effects to neighbors.
     * Updates neighbor risk loads and can trigger chain reactions.
     */
    void propagateCascades(List<RiskEvent> events, int currentTick);

    /**
     * Register a risk profile for an entity.
     */
    void registerProfile(RiskProfile profile);

    /**
     * Update entity state used by evolution model (usage intensity, maintenance).
     */
    void updateEntityState(String entityId, double usageIntensity, int currentTick, boolean maintained);

    /**
     * Recalculate neighbor risk loads for all profiles near a location.
     * Called after significant changes (new build, risk event, etc.)
     */
    void recalculateNeighborLoads(HexCoord epicenter, int radius, int currentTick);
}
