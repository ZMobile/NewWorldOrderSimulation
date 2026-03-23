package com.measim.service.simulation;

public interface TickPhase {
    String name();
    void execute(int currentTick);
    int order();
}
