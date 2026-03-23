package com.measim.model.world;

public class TileEnvironment {

    private static final double NATURAL_RECOVERY_RATE = 0.01;
    private static final double BIODIVERSITY_RECOVERY_RATE = 0.005;

    private double soilQuality;
    private double airQuality;
    private double waterQuality;
    private double biodiversity;

    public TileEnvironment(double soilQuality, double airQuality, double waterQuality, double biodiversity) {
        this.soilQuality = clamp(soilQuality);
        this.airQuality = clamp(airQuality);
        this.waterQuality = clamp(waterQuality);
        this.biodiversity = clamp(biodiversity);
    }

    public TileEnvironment(TerrainType terrain) {
        this(terrain.baseSoilQuality(), 1.0, terrain.baseWaterQuality(), terrain.baseBiodiversity());
    }

    public void applyPollution(double amount) {
        soilQuality = clamp(soilQuality - amount * 0.3);
        airQuality = clamp(airQuality - amount * 0.4);
        waterQuality = clamp(waterQuality - amount * 0.2);
        biodiversity = clamp(biodiversity - amount * 0.1);
    }

    public void applyDiffusedPollution(double amount, int distance) {
        double falloff = amount / (1.0 + distance);
        applyPollution(falloff * 0.5);
    }

    public void naturalRecovery(boolean hasActivePollutionSource) {
        if (hasActivePollutionSource) return;
        soilQuality = clamp(soilQuality + NATURAL_RECOVERY_RATE);
        airQuality = clamp(airQuality + NATURAL_RECOVERY_RATE);
        waterQuality = clamp(waterQuality + NATURAL_RECOVERY_RATE);
        biodiversity = clamp(biodiversity + BIODIVERSITY_RECOVERY_RATE);
    }

    public double averageHealth() {
        return (soilQuality + airQuality + waterQuality + biodiversity) / 4.0;
    }

    public boolean isCrisis() { return averageHealth() < 0.3; }
    public double soilQuality() { return soilQuality; }
    public double airQuality() { return airQuality; }
    public double waterQuality() { return waterQuality; }
    public double biodiversity() { return biodiversity; }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
