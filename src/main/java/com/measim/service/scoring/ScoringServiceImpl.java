package com.measim.service.scoring;

import com.measim.dao.AuditDao;
import com.measim.model.scoring.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ScoringServiceImpl implements ScoringService {

    private final AuditDao auditDao;
    private final SectorBaseline sectorBaseline = new SectorBaseline();

    @Inject
    public ScoringServiceImpl(AuditDao auditDao) { this.auditDao = auditDao; }

    @Override
    public ModifierSet computeModifiers(ScoreVector score, SectorBaseline baseline) {
        return new ModifierSet(
                computeEfModifier(score.environmentalFootprint()),
                computeCcModifier(score.commonsContribution()),
                computeRcModifier(score.resourceConcentration()),
                computeLdDiversion(score.laborDisplacement())
        );
    }

    @Override
    public void updateScoreVector(String agentId, ScoreVector score, SectorBaseline baseline,
                                  double totalEmissions, double revenue, int humanEmployees,
                                  double accumulatedCredits, double commonsScore, int tick) {
        double sectorEmissions = baseline.medianEmissionsPerRevenue();
        double agentEmissions = revenue > 0 ? totalEmissions / revenue : 0;
        double efRatio = sectorEmissions > 0 ? agentEmissions / sectorEmissions : 1.0;
        audit(agentId, "EF", score.environmentalFootprint(), efRatio, tick);
        score.setEnvironmentalFootprint(efRatio);

        audit(agentId, "CC", score.commonsContribution(), commonsScore, tick);
        score.setCommonsContribution(commonsScore);

        double medianRevPerEmp = baseline.medianRevenuePerEmployee();
        double agentRevPerEmp = humanEmployees > 0 ? revenue / humanEmployees : revenue;
        double ldRatio = medianRevPerEmp > 0 ? agentRevPerEmp / medianRevPerEmp : 1.0;
        audit(agentId, "LD", score.laborDisplacement(), ldRatio, tick);
        score.setLaborDisplacement(ldRatio);

        double rcRatio = baseline.medianWealth() > 0 ? accumulatedCredits / baseline.medianWealth() : 1.0;
        audit(agentId, "RC", score.resourceConcentration(), rcRatio, tick);
        score.setResourceConcentration(rcRatio);

        audit(agentId, "EP", score.economicProductivity(), revenue, tick);
        score.setEconomicProductivity(revenue);
    }

    @Override
    public void updateBaseline(SectorBaseline baseline, List<SectorBaseline.ActorData> actorData) {
        if (actorData.isEmpty()) return;

        List<Double> emissions = actorData.stream().filter(a -> a.revenue() > 0)
                .map(a -> a.totalEmissions() / a.revenue()).sorted().toList();
        if (!emissions.isEmpty()) baseline.setMedianEmissionsPerRevenue(median(emissions));

        List<Double> revPerEmp = actorData.stream().filter(a -> a.humanEmployees() > 0)
                .map(a -> a.revenue() / a.humanEmployees()).sorted().toList();
        if (!revPerEmp.isEmpty()) baseline.setMedianRevenuePerEmployee(median(revPerEmp));

        List<Double> wealths = actorData.stream().map(SectorBaseline.ActorData::credits).sorted().toList();
        if (!wealths.isEmpty()) baseline.setMedianWealth(median(wealths));
    }

    @Override
    public SectorBaseline getSectorBaseline() { return sectorBaseline; }

    // --- Modifier functions (exact formulas from MEAS spec) ---

    double computeEfModifier(double r) {
        if (r <= 0.5) return 1.10;
        if (r <= 0.8) return 1.05;
        if (r <= 1.0) return 1.00;
        if (r <= 1.5) return 0.90;
        if (r <= 2.0) return 0.75;
        return 0.60;
    }

    double computeCcModifier(double c) {
        if (c >= 0.9) return 1.08;
        if (c >= 0.7) return 1.04;
        if (c >= 0.3) return 1.00;
        return 0.97;
    }

    double computeLdDiversion(double d) {
        double rate;
        if (d <= 1.0) rate = 0.0;
        else if (d <= 2.0) rate = 0.02 * (d - 1.0);
        else if (d <= 5.0) rate = 0.02 + 0.03 * (d - 2.0);
        else rate = 0.11 + 0.04 * (d - 5.0);
        return Math.min(rate, 0.25);
    }

    double computeRcModifier(double k) {
        if (k <= 10) return 1.00;
        if (k <= 100) return 1.00 - 0.001 * (k - 10);
        if (k <= 1000) return 0.91 - 0.0005 * (k - 100);
        return Math.max(0.46 - 0.0001 * (k - 1000), 0.30);
    }

    private void audit(String agentId, String axis, double prev, double next, int tick) {
        auditDao.append(new AuditEntry(tick, agentId, axis, prev, next,
                FormulaVersion.INITIAL.version(), String.format("%s: %.4f → %.4f", axis, prev, next)));
    }

    private double median(List<Double> sorted) {
        int n = sorted.size();
        if (n % 2 == 0) return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return sorted.get(n / 2);
    }
}
