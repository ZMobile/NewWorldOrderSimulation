package com.measim.model.infrastructure;

/**
 * A single effect that infrastructure produces.
 * One infrastructure can have multiple effects.
 *
 * Game rules constrain magnitudes:
 *   - RESOURCE_TRANSPORT: capacity in units/tick, max based on infrastructure capacity
 *   - TRADE_COST_REDUCTION: 0.0-0.8 (can't reduce to zero)
 *   - POLLUTION_REDUCTION: 0.0-0.5 per tick on affected tiles
 *   - EXTRACTION_BOOST: 1.0-3.0 multiplier
 *   - PRODUCTION_SPEED_BOOST: 1.0-2.0 multiplier
 *   - ENVIRONMENTAL_REMEDIATION: 0.0-0.1 health recovery boost per tick
 *   - CUSTOM: Game Master defines, but magnitude bounded by game rules
 */
public record InfrastructureEffect(
        EffectType type,
        double magnitude,
        String targetResourceId  // null for non-resource-specific effects
) {
    public enum EffectType {
        RESOURCE_TRANSPORT,         // Moves resources from source to destination tile
        TRADE_COST_REDUCTION,       // Reduces cost of trade between connected tiles
        POLLUTION_REDUCTION,        // Reduces pollution on affected tiles
        EXTRACTION_BOOST,           // Multiplier on resource extraction rate
        PRODUCTION_SPEED_BOOST,     // Multiplier on production speed
        ENVIRONMENTAL_REMEDIATION,  // Boosts natural recovery rate
        STORAGE_CAPACITY,           // Increases how much a tile can stockpile
        CUSTOM                      // Game Master defined effect
    }

    // Game rule bounds
    public static final double MAX_TRADE_COST_REDUCTION = 0.8;
    public static final double MAX_POLLUTION_REDUCTION = 0.5;
    public static final double MAX_EXTRACTION_BOOST = 3.0;
    public static final double MAX_PRODUCTION_BOOST = 2.0;
    public static final double MAX_REMEDIATION_BOOST = 0.1;

    public InfrastructureEffect {
        // Enforce game rules on magnitude
        magnitude = switch (type) {
            case TRADE_COST_REDUCTION -> Math.min(magnitude, MAX_TRADE_COST_REDUCTION);
            case POLLUTION_REDUCTION -> Math.min(magnitude, MAX_POLLUTION_REDUCTION);
            case EXTRACTION_BOOST -> Math.max(1.0, Math.min(magnitude, MAX_EXTRACTION_BOOST));
            case PRODUCTION_SPEED_BOOST -> Math.max(1.0, Math.min(magnitude, MAX_PRODUCTION_BOOST));
            case ENVIRONMENTAL_REMEDIATION -> Math.min(magnitude, MAX_REMEDIATION_BOOST);
            default -> magnitude;
        };
    }
}
