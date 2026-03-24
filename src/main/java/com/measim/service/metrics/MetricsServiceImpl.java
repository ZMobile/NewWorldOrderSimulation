package com.measim.service.metrics;

import com.measim.dao.*;
import com.measim.model.agent.Agent;
import com.measim.service.economy.CreditFlowService;
import com.measim.service.world.EnvironmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Singleton
public class MetricsServiceImpl implements MetricsService {

    private final AgentDao agentDao;
    private final MetricsDao metricsDao;
    private final EnvironmentService environmentService;
    private final CreditFlowService creditFlowService;
    private final MarketDao marketDao;

    @Inject
    public MetricsServiceImpl(AgentDao agentDao, MetricsDao metricsDao,
                               EnvironmentService environmentService,
                               CreditFlowService creditFlowService, MarketDao marketDao) {
        this.agentDao = agentDao;
        this.metricsDao = metricsDao;
        this.environmentService = environmentService;
        this.creditFlowService = creditFlowService;
        this.marketDao = marketDao;
    }

    @Override
    public void collectTick(int currentTick) {
        List<Agent> agents = agentDao.getAllAgents();
        List<Double> wealths = agents.stream().map(a -> a.state().credits()).toList();
        double gini = computeGini(wealths);

        DoubleSummaryStatistics satisfaction = agents.stream()
                .mapToDouble(a -> a.state().satisfaction()).summaryStatistics();
        double envHealth = environmentService.averageEnvironmentalHealth();
        int totalRobots = agents.stream().mapToInt(a -> a.state().ownedRobots()).sum();
        double avgCredits = agents.stream().mapToDouble(a -> a.state().credits()).average().orElse(0);
        long totalTx = marketDao.getAllMarkets().stream()
                .mapToLong(m -> m.transactionHistory().size()).sum();

        var metrics = new MetricsDao.TickMetrics(
                currentTick, gini, satisfaction.getAverage(),
                satisfaction.getMax() - satisfaction.getMin(),
                envHealth, creditFlowService.ubiPool(), totalRobots, avgCredits,
                totalTx, agents.size());
        metricsDao.record(metrics);

        // Live write: append to file every tick so data is available immediately
        try {
            Path livePath = Path.of("output/metrics_live.csv");
            if (currentTick == 1) {
                Files.createDirectories(livePath.getParent());
                Files.writeString(livePath,
                        "tick,gini,satisfaction_mean,satisfaction_spread,env_health,ubi_pool,total_robots,avg_credits,total_transactions,agent_count\n");
            }
            Files.writeString(livePath,
                    String.format("%d,%.4f,%.4f,%.4f,%.4f,%.2f,%d,%.2f,%d,%d%n",
                            metrics.tick(), metrics.giniCoefficient(), metrics.satisfactionMean(),
                            metrics.satisfactionSpread(), metrics.environmentalHealth(),
                            metrics.ubiPoolSize(), metrics.totalRobots(), metrics.averageCredits(),
                            metrics.totalTransactions(), metrics.agentCount()),
                    java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
        } catch (Exception e) {
            // Don't crash the simulation for a metrics write failure
        }
    }

    @Override
    public void exportCsv(Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("tick,gini,satisfaction_mean,satisfaction_spread,env_health,");
            writer.write("ubi_pool,total_robots,avg_credits,total_transactions,agent_count");
            writer.newLine();
            for (MetricsDao.TickMetrics m : metricsDao.getHistory()) {
                writer.write(String.format("%d,%.4f,%.4f,%.4f,%.4f,%.2f,%d,%.2f,%d,%d",
                        m.tick(), m.giniCoefficient(), m.satisfactionMean(),
                        m.satisfactionSpread(), m.environmentalHealth(),
                        m.ubiPoolSize(), m.totalRobots(), m.averageCredits(),
                        m.totalTransactions(), m.agentCount()));
                writer.newLine();
            }
        }
    }

    @Override public MetricsDao.TickMetrics getLatest() { return metricsDao.getLatest(); }
    @Override public List<MetricsDao.TickMetrics> getHistory() { return metricsDao.getHistory(); }

    static double computeGini(List<Double> values) {
        if (values == null || values.size() <= 1) return 0.0;
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        double sumDiffs = 0, sum = 0;
        for (int i = 0; i < n; i++) {
            sum += sorted.get(i);
            for (int j = 0; j < n; j++) sumDiffs += Math.abs(sorted.get(i) - sorted.get(j));
        }
        return sum == 0 ? 0.0 : sumDiffs / (2.0 * n * sum);
    }
}
