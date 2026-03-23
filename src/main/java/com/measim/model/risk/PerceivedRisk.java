package com.measim.model.risk;

/**
 * An agent's ESTIMATE of a risk. Built from observation, experience, and information sharing.
 * May be more or less accurate than the true risk.
 *
 * How agents build perceived risk:
 *   - They observe risk events that happen nearby (adds to perceived risk)
 *   - They hear about risks from other agents (communication)
 *   - Higher compliance/intelligence agents estimate more accurately
 *   - Optimistic agents (high risk tolerance) underestimate
 *   - Cautious agents (low risk tolerance) overestimate
 *   - Experience with an entity improves accuracy over time
 *
 * The gap between perceived and true risk drives behavior:
 *   - Exploiter underestimates risk → takes on dangerous ventures → sometimes gets burned
 *   - Regulator overestimates risk → proposes overly cautious regulations
 *   - Entrepreneur calibrates risk vs reward → balances well
 */
public record PerceivedRisk(
        String riskId,
        String entityId,
        String perceiverAgentId,
        double perceivedProbability,
        double perceivedSeverity,
        double confidence,        // 0-1: how sure the agent is of their estimate
        int lastUpdatedTick,
        String source             // "observation", "communication", "experience", "intuition"
) {
    /**
     * Update perceived risk based on new information.
     * Blends old estimate with new observation, weighted by confidence.
     */
    public PerceivedRisk updatedWith(double observedProbability, double observedSeverity,
                                      String newSource, int tick) {
        double blendWeight = 0.3; // new info weight
        double newProb = perceivedProbability * (1 - blendWeight) + observedProbability * blendWeight;
        double newSev = perceivedSeverity * (1 - blendWeight) + observedSeverity * blendWeight;
        double newConf = Math.min(1.0, confidence + 0.1); // confidence grows with more data
        return new PerceivedRisk(riskId, entityId, perceiverAgentId,
                newProb, newSev, newConf, tick, newSource);
    }

    /**
     * Create an initial estimate biased by agent personality.
     */
    public static PerceivedRisk initialEstimate(String riskId, String entityId,
                                                  String agentId, double riskTolerance,
                                                  double complianceDisposition, int tick) {
        // High risk tolerance → underestimate probability
        // High compliance → overestimate probability (cautious)
        double biasFactor = 0.5 + complianceDisposition * 0.5 - riskTolerance * 0.3;
        double estimatedProb = 0.05 * biasFactor; // rough baseline guess
        double estimatedSev = 0.3 * biasFactor;
        return new PerceivedRisk(riskId, entityId, agentId,
                estimatedProb, estimatedSev, 0.2, tick, "intuition");
    }
}
