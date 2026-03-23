package com.measim.model.gamemaster;

import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Map;

/**
 * A Game Master-generated world event that affects the simulation.
 * The GM is the DM — these are the narrative/mechanical interventions it can make.
 */
public record WorldEvent(
        String id,
        WorldEventType type,
        String name,
        String description,
        int tick,
        double severity,
        List<HexCoord> affectedTiles,
        Map<String, Object> parameters
) {
    public enum WorldEventType {
        // Spontaneous world changes
        RESOURCE_DISCOVERY,          // New resource node appears
        RESOURCE_DEPLETION,          // Existing resource accelerated depletion
        ENVIRONMENTAL_DISASTER,      // Localized environmental collapse
        ENVIRONMENTAL_RECOVERY,      // Unexpected ecosystem rebound

        // Economic shocks
        MARKET_BOOM,                 // Demand surge for specific goods
        MARKET_CRASH,                // Price collapse in a sector
        SUPPLY_CHAIN_DISRUPTION,     // Production chains temporarily disrupted

        // Technology & innovation
        TECH_BREAKTHROUGH,           // Spontaneous tech advancement (not agent-driven)
        TECH_OBSOLESCENCE,           // Existing technology becomes less effective

        // Social & political
        POPULATION_GROWTH,           // New agents enter the simulation
        MIGRATION_WAVE,              // Mass agent relocation pressure
        SOCIAL_UNREST,               // Low satisfaction triggers collective action
        COOPERATION_EMERGENCE,       // Agents spontaneously form beneficial networks

        // Agent-specific novel actions (GM adjudicates)
        NOVEL_BUSINESS_MODEL,        // Agent proposes something outside existing chains
        EXPLOITATION_ATTEMPT,        // Agent tries to game the system in a novel way
        PHILANTHROPIC_PROJECT,       // Large-scale public works
        POLITICAL_MANEUVER,          // Coalition building, governance capture attempts
        ARTISTIC_CREATION,           // Artisan creates unique cultural value

        // World coherence
        COHERENCE_CORRECTION         // GM corrects an inconsistency or imbalance
    }
}
