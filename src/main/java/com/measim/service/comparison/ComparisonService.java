package com.measim.service.comparison;

import com.measim.dao.MetricsDao;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ComparisonService {
    void recordScenario(String scenarioName, List<MetricsDao.TickMetrics> metrics);
    ComparisonReport compare(String scenarioA, String scenarioB);
    void exportReport(ComparisonReport report, Path outputPath) throws IOException;

    record ComparisonReport(
            String scenarioA, String scenarioB,
            double giniDelta, double satisfactionDelta,
            double envHealthDelta, double avgCreditsDelta,
            int robotsDeltaFinal,
            List<TickComparison> tickByTick
    ) {}

    record TickComparison(int tick, double giniA, double giniB,
                          double satisfactionA, double satisfactionB,
                          double envA, double envB) {}
}
