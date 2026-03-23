package com.measim.model.infrastructure;

import java.util.List;
import java.util.Map;

/**
 * Defines what a kind of infrastructure IS — its blueprint.
 * Predefined types exist at world start. The Game Master can create new ones
 * with custom properties, just like it can create new resources and production chains.
 *
 * Fixed game rules that ALL infrastructure obeys:
 *   - Must satisfy placement constraints (terrain, capacity, compatibility)
 *   - Has a construction cost in credits
 *   - Has a per-tick maintenance cost (unpaid = degradation)
 *   - Has a capacity limit (units of resource per tick it can move)
 *   - Has a max range in hex distance
 *   - Condition degrades without maintenance; destroyed at 0
 *   - Stacking on a tile gives diminishing returns
 *   - All infrastructure adds environmental pressure to its tile
 */
public record InfrastructureType(
        String id,
        String name,
        String description,
        ConnectionMode connectionMode,
        List<InfrastructureEffect> effects,
        InfrastructureConstraints constraints,
        double constructionCost,
        double maintenanceCostPerTick,
        int maxRange,
        double capacity,
        Map<String, Double> customProperties,
        boolean isCustom
) {
    public enum ConnectionMode {
        POINT_TO_POINT,  // Connects two specific tiles (pipeline, road, power line)
        AREA_OF_EFFECT,  // Affects all tiles within range (remediation plant, broadcast tower)
        TILE_LOCAL        // Only affects the tile it's on (facility upgrade, warehouse)
    }

    public static InfrastructureType predefined(String id, String name, String description,
                                                 ConnectionMode mode, List<InfrastructureEffect> effects,
                                                 double constructionCost, double maintenance,
                                                 int maxRange, double capacity) {
        return new InfrastructureType(id, name, description, mode, effects,
                InfrastructureConstraints.defaults(),
                constructionCost, maintenance, maxRange, capacity, Map.of(), false);
    }
}
