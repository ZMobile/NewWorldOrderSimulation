package com.measim.service.comparison;

import com.measim.dao.MetricsDao;
import jakarta.inject.Singleton;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Singleton
public class ComparisonServiceImpl implements ComparisonService {

    private final Map<String, List<MetricsDao.TickMetrics>> scenarios = new LinkedHashMap<>();

    @Override
    public void recordScenario(String scenarioName, List<MetricsDao.TickMetrics> metrics) {
        scenarios.put(scenarioName, new ArrayList<>(metrics));
    }

    @Override
    public ComparisonReport compare(String scenarioA, String scenarioB) {
        List<MetricsDao.TickMetrics> metricsA = scenarios.getOrDefault(scenarioA, List.of());
        List<MetricsDao.TickMetrics> metricsB = scenarios.getOrDefault(scenarioB, List.of());

        if (metricsA.isEmpty() || metricsB.isEmpty()) {
            return new ComparisonReport(scenarioA, scenarioB, 0, 0, 0, 0, 0, List.of());
        }

        MetricsDao.TickMetrics lastA = metricsA.getLast();
        MetricsDao.TickMetrics lastB = metricsB.getLast();

        List<TickComparison> tickByTick = new ArrayList<>();
        int minTicks = Math.min(metricsA.size(), metricsB.size());
        for (int i = 0; i < minTicks; i++) {
            var a = metricsA.get(i);
            var b = metricsB.get(i);
            tickByTick.add(new TickComparison(a.tick(),
                    a.giniCoefficient(), b.giniCoefficient(),
                    a.satisfactionMean(), b.satisfactionMean(),
                    a.environmentalHealth(), b.environmentalHealth()));
        }

        return new ComparisonReport(
                scenarioA, scenarioB,
                lastA.giniCoefficient() - lastB.giniCoefficient(),
                lastA.satisfactionMean() - lastB.satisfactionMean(),
                lastA.environmentalHealth() - lastB.environmentalHealth(),
                lastA.averageCredits() - lastB.averageCredits(),
                lastA.totalRobots() - lastB.totalRobots(),
                tickByTick);
    }

    @Override
    public void exportReport(ComparisonReport report, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(outputPath)) {
            w.write("=== Comparison Report ===\n");
            w.write(String.format("Scenario A: %s | Scenario B: %s%n", report.scenarioA(), report.scenarioB()));
            w.write(String.format("Gini delta (A-B): %+.4f%n", report.giniDelta()));
            w.write(String.format("Satisfaction delta: %+.4f%n", report.satisfactionDelta()));
            w.write(String.format("Env health delta: %+.4f%n", report.envHealthDelta()));
            w.write(String.format("Avg credits delta: %+.2f%n", report.avgCreditsDelta()));
            w.write(String.format("Robot count delta: %+d%n%n", report.robotsDeltaFinal()));

            w.write("tick,gini_a,gini_b,satisfaction_a,satisfaction_b,env_a,env_b\n");
            for (var tc : report.tickByTick()) {
                w.write(String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        tc.tick(), tc.giniA(), tc.giniB(),
                        tc.satisfactionA(), tc.satisfactionB(),
                        tc.envA(), tc.envB()));
            }
        }
    }
}
