package com.measim.model.governance;

import com.measim.model.world.HexCoord;

public class Government {

    private final String id;
    private final String name;
    private final HexCoord regionMin;
    private final HexCoord regionMax;
    private double efWeight;
    private double ubiMultiplier;
    private double domainTwoStrictness;
    private String currentFormulaVersion;

    public Government(String id, String name, HexCoord regionMin, HexCoord regionMax,
                      double efWeight, double ubiMultiplier, double domainTwoStrictness) {
        this.id = id;
        this.name = name;
        this.regionMin = regionMin;
        this.regionMax = regionMax;
        this.efWeight = efWeight;
        this.ubiMultiplier = ubiMultiplier;
        this.domainTwoStrictness = domainTwoStrictness;
        this.currentFormulaVersion = "v0.1.0";
    }

    public boolean containsTile(HexCoord coord) {
        return coord.q() >= regionMin.q() && coord.q() <= regionMax.q()
                && coord.r() >= regionMin.r() && coord.r() <= regionMax.r();
    }

    public String id() { return id; }
    public String name() { return name; }
    public HexCoord regionMin() { return regionMin; }
    public HexCoord regionMax() { return regionMax; }
    public double efWeight() { return efWeight; }
    public void setEfWeight(double v) { this.efWeight = v; }
    public double ubiMultiplier() { return ubiMultiplier; }
    public void setUbiMultiplier(double v) { this.ubiMultiplier = v; }
    public double domainTwoStrictness() { return domainTwoStrictness; }
    public void setDomainTwoStrictness(double v) { this.domainTwoStrictness = v; }
    public String currentFormulaVersion() { return currentFormulaVersion; }
    public void setCurrentFormulaVersion(String v) { this.currentFormulaVersion = v; }
}
