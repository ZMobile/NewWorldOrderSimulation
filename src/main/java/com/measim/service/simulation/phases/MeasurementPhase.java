package com.measim.service.simulation.phases;

import com.measim.model.config.SimulationConfig;
import com.measim.service.metrics.MetricsService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MeasurementPhase implements TickPhase {
    private final MetricsService metricsService;
    private final SimulationConfig config;

    @Inject
    public MeasurementPhase(MetricsService metricsService, SimulationConfig config) {
        this.metricsService = metricsService;
        this.config = config;
    }

    @Override public String name() { return "Measurement"; }
    @Override public int order() { return 11; }

    @Override
    public void execute(int currentTick) {
        if (currentTick % config.metricsInterval() == 0) metricsService.collectTick(currentTick);
    }
}
