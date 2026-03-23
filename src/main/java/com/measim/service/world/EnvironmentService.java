package com.measim.service.world;

import com.measim.model.world.HexCoord;

public interface EnvironmentService {
    void applyProductionPollution(HexCoord source, double pollutionAmount);
    void tickRecovery();
    double averageEnvironmentalHealth();
    double regionHealth(HexCoord center, int radius);
    long crisisTileCount();
}
