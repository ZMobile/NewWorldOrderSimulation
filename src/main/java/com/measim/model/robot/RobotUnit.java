package com.measim.model.robot;

public record RobotUnit(
        String id, String ownerId, double efficiency,
        double energyCostPerTick, int acquisitionTick
) {}
