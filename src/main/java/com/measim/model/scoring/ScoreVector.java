package com.measim.model.scoring;

public class ScoreVector {

    private double environmentalFootprint = 1.0;
    private double commonsContribution = 0.5;
    private double laborDisplacement = 1.0;
    private double resourceConcentration = 1.0;
    private double economicProductivity = 0.0;

    public double environmentalFootprint() { return environmentalFootprint; }
    public double commonsContribution() { return commonsContribution; }
    public double laborDisplacement() { return laborDisplacement; }
    public double resourceConcentration() { return resourceConcentration; }
    public double economicProductivity() { return economicProductivity; }

    public void setEnvironmentalFootprint(double v) { this.environmentalFootprint = v; }
    public void setCommonsContribution(double v) { this.commonsContribution = v; }
    public void setLaborDisplacement(double v) { this.laborDisplacement = v; }
    public void setResourceConcentration(double v) { this.resourceConcentration = v; }
    public void setEconomicProductivity(double v) { this.economicProductivity = v; }
}
