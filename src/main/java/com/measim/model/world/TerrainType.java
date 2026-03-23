package com.measim.model.world;

public enum TerrainType {
    GRASSLAND(1.0, 0.8, 0.7, 0.6),
    MOUNTAIN(3.0, 0.3, 0.4, 0.4),
    DESERT(1.5, 0.2, 0.3, 0.2),
    WATER(999.0, 0.0, 0.9, 0.8),
    FOREST(2.0, 0.6, 0.8, 0.9),
    TUNDRA(2.5, 0.2, 0.5, 0.3),
    WETLAND(2.5, 0.5, 0.6, 0.7);

    private final double movementCost;
    private final double baseSoilQuality;
    private final double baseWaterQuality;
    private final double baseBiodiversity;

    TerrainType(double movementCost, double baseSoilQuality, double baseWaterQuality, double baseBiodiversity) {
        this.movementCost = movementCost;
        this.baseSoilQuality = baseSoilQuality;
        this.baseWaterQuality = baseWaterQuality;
        this.baseBiodiversity = baseBiodiversity;
    }

    public double movementCost() { return movementCost; }
    public double baseSoilQuality() { return baseSoilQuality; }
    public double baseWaterQuality() { return baseWaterQuality; }
    public double baseBiodiversity() { return baseBiodiversity; }
    public boolean isPassable() { return this != WATER; }
}
