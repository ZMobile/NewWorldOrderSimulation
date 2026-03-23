package com.measim.service.simulation;

public interface SimulationService {
    void initialize();
    void run();
    void runComparison();
    int currentTick();
}
