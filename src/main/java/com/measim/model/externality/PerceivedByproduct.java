package com.measim.model.externality;

/**
 * What an agent THINKS the externalities of an entity are.
 * Built from observation (visible smoke, nearby crop failures) and communication.
 *
 * Agents may:
 *   - Not know about hidden byproducts at all (ignorance)
 *   - Underestimate visible byproducts (optimism / low compliance)
 *   - Overestimate after seeing a disaster (recency bias)
 *   - Share information accurately or with distortion
 */
public record PerceivedByproduct(
        String entityId,
        String perceiverAgentId,
        Byproduct.ByproductType perceivedType,
        double perceivedAmount,
        double confidence,
        int lastUpdatedTick,
        String source
) {
    public PerceivedByproduct updatedWith(double observedAmount, String newSource, int tick) {
        double blendWeight = 0.3;
        double newAmount = perceivedAmount * (1 - blendWeight) + observedAmount * blendWeight;
        double newConf = Math.min(1.0, confidence + 0.1);
        return new PerceivedByproduct(entityId, perceiverAgentId, perceivedType,
                newAmount, newConf, tick, newSource);
    }

    public static PerceivedByproduct initialEstimate(String entityId, String agentId,
                                                       Byproduct.ByproductType type,
                                                       double riskTolerance, int tick) {
        // Optimistic agents underestimate, cautious agents overestimate
        double estimate = 0.5 * (1.0 - riskTolerance * 0.5);
        return new PerceivedByproduct(entityId, agentId, type, estimate, 0.2, tick, "intuition");
    }
}
