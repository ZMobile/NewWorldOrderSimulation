package com.measim.model.gamemaster;

import com.measim.model.economy.ItemType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record DiscoverySpec(
        String id,
        String name,
        String description,
        DiscoveryCategory category,
        Map<ItemType, Integer> inputs,
        Map<ItemType, Integer> outputs,
        double pollutionOutput,
        int productionTimeTicks,
        Set<String> prerequisiteTechs,
        InfrastructureEffectType effectType,
        double effectMagnitude,
        String discovererId,
        int discoveryTick,
        boolean published
) {
    public record SpawnCondition(String terrainType, double probability) {}
}
