package com.measim.model.service;

import com.measim.model.infrastructure.InfrastructureEffect;
import com.measim.model.risk.Risk;
import com.measim.model.externality.Byproduct;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blueprint for a service an agent can operate.
 * Created when the GM evaluates an agent's service proposal.
 * Like InfrastructureType, these are not predefined — agents propose, GM evaluates.
 *
 * A service is distinct from a production chain (which transforms physical resources):
 *   - A production chain: MINERAL + ENERGY → CONSTRUCTION (physical transformation)
 *   - A service: takes credits as input, produces an EFFECT on consumers (intangible value)
 *
 * Services can:
 *   - Require infrastructure to operate (bank needs a building, logistics needs roads)
 *   - Have capacity limits (how many clients per tick)
 *   - Have quality that affects the benefit to consumers
 *   - Compete with other services of the same category
 *   - Have risks (bank run, malpractice, service failure)
 *   - Have byproducts (data collection, social displacement, noise)
 *   - Generate revenue for the operator and costs for the consumer
 */
public record AgentServiceType(
        String id,
        String name,
        String description,
        ServiceCategory category,
        double setupCost,
        double operatingCostPerTick,
        double pricePerUse,
        int capacityPerTick,
        double qualityScore,
        Set<String> requiredInfrastructureTypes,
        List<ServiceEffect> effects,
        List<Risk> risks,
        List<Byproduct> byproducts,
        Map<String, Double> customProperties
) {
    /**
     * Broad categories — but specific services within are always GM-created.
     * The category helps the engine route consumers to the right services.
     */
    public enum ServiceCategory {
        FINANCIAL,      // Banking, lending, insurance, investment
        LOGISTICS,      // Transport, shipping, supply chain management
        HEALTHCARE,     // Medical treatment, wellness, rehabilitation
        EDUCATION,      // Training, skill development, research access
        LEGAL,          // Contracts, dispute resolution, compliance
        SECURITY,       // Protection, insurance against theft/damage
        INFORMATION,    // Market data, intelligence, consulting
        ENTERTAINMENT,  // Culture, recreation (satisfaction boost)
        MAINTENANCE,    // Repair, upkeep for infrastructure and equipment
        GOVERNANCE,     // Administrative, regulatory, arbitration
        CUSTOM          // GM-defined, doesn't fit other categories
    }

    /**
     * What consuming this service does for the consumer.
     * GM sets these when evaluating the proposal.
     */
    public record ServiceEffect(
            EffectTarget target,
            String property,
            double magnitude,
            int durationTicks
    ) {
        public enum EffectTarget {
            CONSUMER_CREDITS,       // Lending: gives credits now, costs more later
            CONSUMER_SATISFACTION,  // Entertainment, healthcare: boosts satisfaction
            CONSUMER_SKILL,         // Education: improves agent capability
            CONSUMER_RISK,          // Insurance: reduces consumer's risk exposure
            CONSUMER_PRODUCTION,    // Consulting: boosts production efficiency
            CONSUMER_TRADE,         // Logistics: reduces trade costs
            CONSUMER_INFORMATION,   // Intelligence: improves market knowledge
            INFRASTRUCTURE,         // Maintenance: restores infrastructure condition
            GOVERNANCE_INFLUENCE,   // Lobbying: affects governance outcomes
            CUSTOM                  // GM-defined
        }
    }
}
