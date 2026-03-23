package com.measim.model.infrastructure;

import com.measim.model.world.TerrainType;

import java.util.Set;

/**
 * Constraints that govern WHERE and HOW infrastructure can be placed.
 * Fixed game rules set the outer bounds. The Game Master sets specifics
 * for custom types within those bounds.
 *
 * FIXED RULES (not overridable by GM):
 *   - Can't build on WATER tiles (unless infrastructure type explicitly allows it)
 *   - Total infrastructure footprint on a tile <= terrain capacity
 *   - More infrastructure on a tile = more environmental pressure
 *   - Point-to-point infrastructure range limited by maxRange on the type
 *   - Maintenance must be paid or infrastructure degrades and eventually fails
 *
 * GM-SETTABLE per infrastructure type:
 *   - Footprint (how much tile capacity it uses: 1-5)
 *   - Allowed terrains
 *   - Incompatible infrastructure types (can't coexist on same tile)
 *   - Environmental pressure (how much it degrades tile health per tick)
 *   - Whether it can be built on water
 */
public record InfrastructureConstraints(
        int footprint,
        Set<TerrainType> allowedTerrains,
        Set<String> incompatibleTypeIds,
        double environmentalPressurePerTick,
        boolean allowOnWater
) {
    // Fixed game rule: terrain capacity limits
    public static int terrainCapacity(TerrainType terrain) {
        return switch (terrain) {
            case GRASSLAND -> 8;
            case FOREST -> 4;
            case DESERT -> 6;
            case MOUNTAIN -> 3;
            case WETLAND -> 3;
            case TUNDRA -> 2;
            case WATER -> 1; // only special infrastructure (bridges, ports)
        };
    }

    // Fixed game rule: stacking diminishing returns
    public static double stackingEfficiency(int infrastructureCountOnTile) {
        if (infrastructureCountOnTile <= 1) return 1.0;
        if (infrastructureCountOnTile <= 3) return 0.9;
        if (infrastructureCountOnTile <= 5) return 0.75;
        return 0.6; // heavy congestion
    }

    // Default constraints for predefined types
    public static InfrastructureConstraints defaults() {
        return new InfrastructureConstraints(
                1,
                Set.of(TerrainType.GRASSLAND, TerrainType.FOREST, TerrainType.DESERT,
                        TerrainType.MOUNTAIN, TerrainType.WETLAND, TerrainType.TUNDRA),
                Set.of(),
                0.005,
                false
        );
    }
}
