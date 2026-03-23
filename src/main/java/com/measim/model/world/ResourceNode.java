package com.measim.model.world;

import com.measim.model.economy.ResourceType;

public class ResourceNode {

    private final ResourceType type;
    private final HexCoord location;
    private double abundance;
    private final double maxAbundance;
    private final double regenerationRate;

    public ResourceNode(ResourceType type, HexCoord location, double abundance, double regenerationRate) {
        this.type = type;
        this.location = location;
        this.abundance = abundance;
        this.maxAbundance = abundance;
        this.regenerationRate = regenerationRate;
    }

    public double extract(double amount) {
        double extracted = Math.min(amount, abundance);
        abundance -= extracted;
        return extracted;
    }

    public void regenerate() {
        if (regenerationRate > 0 && abundance < maxAbundance) {
            abundance = Math.min(maxAbundance, abundance + regenerationRate);
        }
    }

    public ResourceType type() { return type; }
    public HexCoord location() { return location; }
    public double abundance() { return abundance; }
    public double maxAbundance() { return maxAbundance; }
    public boolean isDepleted() { return abundance <= 0; }
}
