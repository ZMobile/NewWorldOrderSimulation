package com.measim.model.scoring;

/**
 * Holds sector baseline statistics. Computation is done by ScoringService.
 */
public class SectorBaseline {

    private double medianEmissionsPerRevenue = 1.0;
    private double medianRevenuePerEmployee = 1000.0;
    private double medianWealth = 1000.0;

    public double medianEmissionsPerRevenue() { return medianEmissionsPerRevenue; }
    public double medianRevenuePerEmployee() { return medianRevenuePerEmployee; }
    public double medianWealth() { return medianWealth; }

    public void setMedianEmissionsPerRevenue(double v) { this.medianEmissionsPerRevenue = v; }
    public void setMedianRevenuePerEmployee(double v) { this.medianRevenuePerEmployee = v; }
    public void setMedianWealth(double v) { this.medianWealth = Math.max(1.0, v); }

    public record ActorData(String id, double totalEmissions, double revenue,
                            int humanEmployees, double credits, double commonsScore) {}
}
