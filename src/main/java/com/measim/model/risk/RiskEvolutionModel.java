package com.measim.model.risk;

/**
 * Determines how a risk's probability changes over time and circumstance.
 * The Game Master sets these PARAMETERS when it evaluates a proposal.
 * The deterministic engine EVALUATES using current world state — no LLM calls.
 *
 * Effective probability = baseProbability
 *     × ageFactor(age, agingRate, agingCurve)
 *     × usageFactor(usageIntensity, usageSensitivity)
 *     × maintenanceFactor(ticksSinceMaintenance, maintenanceSensitivity)
 *     × environmentFactor(envHealth, envSensitivity)
 *     × neighborFactor(neighborRiskLoad, neighborSensitivity)
 *
 * All factors default to 1.0 (neutral). GM only sets the ones relevant to this risk.
 */
public record RiskEvolutionModel(
        // Age: risk increases as entity gets older
        double agingRate,           // 0 = doesn't age, 0.01 = slow aging, 0.1 = rapid aging
        AgingCurve agingCurve,      // how probability scales with age

        // Usage: risk increases with heavy use
        double usageSensitivity,    // 0 = doesn't matter, 1.0 = linear with usage

        // Maintenance: risk increases when maintenance is missed
        double maintenanceSensitivity,  // how fast risk grows without maintenance

        // Environment: risk responds to tile environmental health
        double environmentSensitivity,  // 0 = immune, 1.0 = highly sensitive

        // Neighbors: risk affected by nearby entity risk loads
        double neighborSensitivity      // 0 = isolated, 1.0 = highly connected
) {
    public enum AgingCurve {
        LINEAR,       // probability increases linearly with age
        EXPONENTIAL,  // probability increases exponentially (bathtub curve early life)
        LOGARITHMIC,  // fast initial increase, then plateaus
        STEP          // stable until threshold, then jumps
    }

    /**
     * Compute the age factor given entity age in ticks.
     */
    public double ageFactor(int ageTicks) {
        if (agingRate <= 0) return 1.0;
        double ageYears = ageTicks / 12.0; // ticks per year
        return switch (agingCurve) {
            case LINEAR -> 1.0 + agingRate * ageYears;
            case EXPONENTIAL -> Math.exp(agingRate * ageYears);
            case LOGARITHMIC -> 1.0 + agingRate * Math.log1p(ageYears);
            case STEP -> ageYears > (1.0 / agingRate) ? 3.0 : 1.0;
        };
    }

    /**
     * Compute usage factor. usageIntensity is 0-1 (fraction of capacity being used).
     */
    public double usageFactor(double usageIntensity) {
        if (usageSensitivity <= 0) return 1.0;
        return 1.0 + usageSensitivity * Math.pow(usageIntensity, 1.5);
    }

    /**
     * Compute maintenance factor. ticksSinceMaintenance is how long since last upkeep.
     */
    public double maintenanceFactor(int ticksSinceMaintenance) {
        if (maintenanceSensitivity <= 0) return 1.0;
        return 1.0 + maintenanceSensitivity * Math.sqrt(ticksSinceMaintenance);
    }

    /**
     * Compute environment factor. envHealth is 0-1 tile environmental quality.
     */
    public double environmentFactor(double envHealth) {
        if (environmentSensitivity <= 0) return 1.0;
        // Lower env health → higher risk
        return 1.0 + environmentSensitivity * Math.max(0, 1.0 - envHealth);
    }

    /**
     * Compute neighbor factor. neighborRiskLoad is sum of severity of active risks nearby.
     */
    public double neighborFactor(double neighborRiskLoad) {
        if (neighborSensitivity <= 0) return 1.0;
        return 1.0 + neighborSensitivity * neighborRiskLoad * 0.1;
    }

    /**
     * Full probability calculation.
     */
    public double evaluate(double baseProbability, int ageTicks, double usageIntensity,
                           int ticksSinceMaintenance, double envHealth, double neighborRiskLoad) {
        return Math.min(0.95, baseProbability
                * ageFactor(ageTicks)
                * usageFactor(usageIntensity)
                * maintenanceFactor(ticksSinceMaintenance)
                * environmentFactor(envHealth)
                * neighborFactor(neighborRiskLoad));
    }

    public static RiskEvolutionModel none() {
        return new RiskEvolutionModel(0, AgingCurve.LINEAR, 0, 0, 0, 0);
    }

    public static RiskEvolutionModel standard() {
        return new RiskEvolutionModel(0.02, AgingCurve.LINEAR, 0.3, 0.5, 0.3, 0.1);
    }
}
