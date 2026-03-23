package com.measim.service.infrastructure;

import com.measim.model.economy.ItemType;
import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.infrastructure.InfrastructureType;
import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Map;

public interface InfrastructureService {

    // Building (type must already be registered — created by GM evaluation)
    BuildResult build(String agentId, String typeId, HexCoord location, HexCoord connectTo, int currentTick);
    record BuildResult(boolean success, String reason, Infrastructure infrastructure) {}

    // Queries for economic pipeline
    Map<ItemType, Double> getAccessibleResources(HexCoord tile);
    double getExtractionMultiplier(HexCoord tile);
    double getProductionSpeedMultiplier(HexCoord tile);
    double getTradeCostMultiplier(HexCoord from, HexCoord to);
    double getPollutionReduction(HexCoord tile);
    double getRemediationBoost(HexCoord tile);

    // Maintenance tick
    void tickMaintenance(int currentTick);

    // Type queries (all types come from GM evaluation — no predefined catalog)
    void registerCustomType(InfrastructureType type);
    List<InfrastructureType> getAvailableTypes();
}
