package com.measim.service.economy;

import com.measim.model.economy.ItemType;
import com.measim.model.economy.ProductionChain;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class ProductionServiceImpl implements ProductionService {

    @Override
    public ProductionResult execute(ProductionChain chain, Map<ItemType, Integer> availableInventory,
                                    double robotEfficiencyMultiplier) {
        for (var entry : chain.inputs().entrySet()) {
            int available = availableInventory.getOrDefault(entry.getKey(), 0);
            if (available < entry.getValue())
                return new ProductionResult(false,
                        "Insufficient " + entry.getKey().id() + ": need " + entry.getValue() + ", have " + available,
                        Map.of(), 0);
        }
        if (!chain.isDiscovered())
            return new ProductionResult(false, "Technology not yet discovered", Map.of(), 0);

        for (var entry : chain.inputs().entrySet())
            availableInventory.merge(entry.getKey(), -entry.getValue(), Integer::sum);

        Map<ItemType, Integer> produced = new HashMap<>();
        for (var entry : chain.outputs().entrySet()) {
            int amount = (int) Math.floor(entry.getValue() * robotEfficiencyMultiplier);
            produced.put(entry.getKey(), Math.max(1, amount));
        }
        return new ProductionResult(true, null, produced, chain.pollutionOutput());
    }
}
