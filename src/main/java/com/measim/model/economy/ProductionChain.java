package com.measim.model.economy;

import java.util.Map;
import java.util.Set;

public record ProductionChain(
        String id,
        String name,
        Map<ItemType, Integer> inputs,
        Map<ItemType, Integer> outputs,
        double pollutionOutput,
        int productionTimeTicks,
        Set<String> prerequisiteTechs,
        boolean isDiscovered
) {
    public static ProductionChain createBase(String id, String name,
                                             Map<ItemType, Integer> inputs,
                                             Map<ItemType, Integer> outputs,
                                             double pollutionOutput,
                                             int productionTimeTicks) {
        return new ProductionChain(id, name, inputs, outputs, pollutionOutput,
                productionTimeTicks, Set.of(), true);
    }
}
