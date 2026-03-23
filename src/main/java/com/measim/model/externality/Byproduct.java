package com.measim.model.externality;

import com.measim.model.risk.RiskEvolutionModel;

/**
 * A byproduct/externality produced by an entity. Set by the GM when evaluating proposals.
 * Agents and the measurement system may or may not know about it.
 *
 * TRUE byproducts affect the world silently. PERCEIVED byproducts feed into EF scoring.
 * The gap between measured and actual is where gaming/ignorance happens:
 *   - A factory owner in 1950 didn't know chemicals cause cancer
 *   - Fracking's groundwater contamination wasn't understood initially
 *   - An Exploiter might intentionally hide byproducts from measurement
 *
 * Evolution: byproducts can increase with age (worn seals leak more),
 * usage intensity (overworked factories pollute more), and accumulate over time.
 */
public record Byproduct(
        String id,
        String name,
        String description,
        ByproductType type,
        ByproductVisibility visibility,
        double baseAmountPerTick,
        RiskEvolutionModel evolution,
        int diffusionRadius,
        double accumulationRate,
        boolean canBeRemediated
) {
    public enum ByproductType {
        AIR_POLLUTION,        // Emissions, particulates, greenhouse gases
        WATER_CONTAMINATION,  // Chemical runoff, thermal pollution
        SOIL_DEGRADATION,     // Heavy metals, chemical deposits
        NOISE,                // Affects agent satisfaction in radius
        WASTE,                // Solid waste that must be managed
        RADIATION,            // Invisible, long-lasting
        CHEMICAL,             // Toxic compounds, carcinogens
        THERMAL,              // Heat output affecting local climate
        ECOLOGICAL,           // Habitat destruction, biodiversity loss
        SOCIAL,               // Displacement, community disruption
        CUSTOM                // GM-defined
    }

    public enum ByproductVisibility {
        VISIBLE,    // Smoke, noise, obvious waste — agents see it immediately
        DELAYED,    // Groundwater contamination — effects appear later
        HIDDEN,     // Trace chemicals, radiation — requires measurement to detect
        CUMULATIVE  // Individually invisible, collectively dangerous (microplastics)
    }

    /**
     * Compute actual output this tick given entity state.
     * Uses same evolution model as risk — age, usage, maintenance affect output.
     */
    public double actualOutput(int ageTicks, double usageIntensity,
                                int ticksSinceMaintenance, double envHealth) {
        return evolution.evaluate(baseAmountPerTick, ageTicks, usageIntensity,
                ticksSinceMaintenance, envHealth, 0);
    }

    /**
     * Is this byproduct detectable by the measurement system at current level?
     * Visible: always. Delayed: after threshold ticks. Hidden: only with tech. Cumulative: when accumulated enough.
     */
    public boolean isDetectable(int ageTicks, double accumulatedAmount) {
        return switch (visibility) {
            case VISIBLE -> true;
            case DELAYED -> ageTicks > 12; // takes ~1 year to notice
            case HIDDEN -> false; // requires specific measurement tech
            case CUMULATIVE -> accumulatedAmount > baseAmountPerTick * 50; // threshold
        };
    }
}
