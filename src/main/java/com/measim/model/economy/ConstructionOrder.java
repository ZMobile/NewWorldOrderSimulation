package com.measim.model.economy;

import java.util.Map;

/**
 * A construction order detailing what resources come from where and at what cost.
 * The GM (Sonnet) sets this when evaluating infrastructure proposals.
 *
 * Resources can come from three sources:
 *   1. Agent's own inventory (free — they already have it)
 *   2. Market purchase (market rate — agent buys from other agents)
 *   3. Reserve supply (premium rate — GM-set markup, depletes reserve)
 *
 * The reserve will NOT cover 100% of any resource — agents must participate
 * in the economy. The GM decides the split based on:
 *   - What the agent already has
 *   - What's available on the market
 *   - What the reserve has in stock
 *   - Policy on reserve dependency (avoid over-reliance)
 */
public record ConstructionOrder(
        Map<String, Integer> totalRequired,       // resource → quantity needed
        Map<String, Integer> fromAgent,           // resource → quantity from agent inventory
        Map<String, Integer> fromMarket,          // resource → quantity agent must buy
        Map<String, Integer> fromReserve,         // resource → quantity supplied by reserve
        Map<String, Double> reservePremiumRates,  // resource → credits per unit (premium rate)
        double robotLaborCost,                    // credits for reserve robot construction
        int constructionTimeTicks,                // GM-set build time
        double totalCreditCost                    // total credits agent pays
) {
    public double reserveResourceCost() {
        double cost = 0;
        for (var entry : fromReserve.entrySet()) {
            cost += entry.getValue() * reservePremiumRates.getOrDefault(entry.getKey(), 0.0);
        }
        return cost;
    }
}
