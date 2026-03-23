package com.measim.service.economy;

import com.measim.model.economy.ItemType;
import com.measim.model.economy.ProductionChain;

import java.util.Map;

public interface ProductionService {
    ProductionResult execute(ProductionChain chain, Map<ItemType, Integer> availableInventory,
                             double robotEfficiencyMultiplier);

    record ProductionResult(boolean success, String reason,
                            Map<ItemType, Integer> produced, double pollutionGenerated) {}
}
