package com.measim.model.risk;

import java.util.List;

/**
 * A specific thing that could go wrong with an entity.
 * Set by the Game Master when it evaluates ANY proposal — infrastructure, research,
 * novel actions, production chains. The GM sets the parameters; the deterministic
 * engine evaluates probability each tick using the RiskEvolutionModel.
 *
 * Probability evolves based on age, usage, maintenance, environment, and neighbors.
 * Zero LLM calls for probability evaluation. GM only called when risk triggers.
 *
 * Examples:
 *   - Aqueduct: base 0.02/tick, ages linearly at 0.03, high maintenance sensitivity
 *   - Food stockpile: base 0.05/tick, high environment sensitivity (heat spoils food)
 *   - Mining facility: base 0.005/tick, exponential aging, cascades to nearby on failure
 *   - Agent business: base 0.03/tick, high usage sensitivity under overwork
 *   - Tile earthquake: base 0.001/tick, step aging (builds up then releases)
 */
public record Risk(
        String id,
        String name,
        String description,
        RiskCategory category,
        double baseProbabilityPerTick,
        RiskEvolutionModel evolution,
        double minSeverity,
        double maxSeverity,
        List<ConsequenceType> possibleConsequences,
        boolean canCascade,
        int cascadeRadius
) {
    public enum RiskCategory {
        STRUCTURAL,       // Physical failure (collapse, leak, break)
        ENVIRONMENTAL,    // Pollution, contamination, ecological damage
        ECONOMIC,         // Cost overrun, market disruption, cash flow
        OPERATIONAL,      // Slowdown, inefficiency, quality degradation
        CATASTROPHIC,     // Total loss, explosion, cascading failure
        NATURAL,          // Earthquake, flood, drought, disease
        TECHNOLOGICAL,    // Obsolescence, unforeseen side effects
        SOCIAL            // Unrest, strike, sabotage
    }

    public enum ConsequenceType {
        PROPERTY_DEGRADATION,
        DESTRUCTION,
        COST_SPIKE,
        ENVIRONMENTAL_DAMAGE,
        PRODUCTION_HALT,
        RESOURCE_LOSS,
        HEALTH_IMPACT,
        CASCADE_TRIGGER,
        QUALITY_REDUCTION,
        MARKET_DISRUPTION,
        CUSTOM
    }

    /**
     * Compute effective probability given current entity state.
     * Pure deterministic — no LLM.
     */
    public double effectiveProbability(int ageTicks, double usageIntensity,
                                       int ticksSinceMaintenance, double envHealth,
                                       double neighborRiskLoad) {
        return evolution.evaluate(baseProbabilityPerTick, ageTicks, usageIntensity,
                ticksSinceMaintenance, envHealth, neighborRiskLoad);
    }
}
