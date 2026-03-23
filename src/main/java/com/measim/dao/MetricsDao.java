package com.measim.dao;

import java.util.List;

public interface MetricsDao {
    void record(TickMetrics metrics);
    List<TickMetrics> getHistory();
    TickMetrics getLatest();

    record TickMetrics(
            int tick, double giniCoefficient, double satisfactionMean,
            double satisfactionSpread, double environmentalHealth,
            double ubiPoolSize, int totalRobots, double averageCredits,
            long totalTransactions, int agentCount
    ) {}
}
