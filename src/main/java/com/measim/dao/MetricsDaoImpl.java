package com.measim.dao;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class MetricsDaoImpl implements MetricsDao {

    private final List<TickMetrics> history = new ArrayList<>();

    @Override
    public void record(TickMetrics metrics) { history.add(metrics); }

    @Override
    public List<TickMetrics> getHistory() { return Collections.unmodifiableList(history); }

    @Override
    public TickMetrics getLatest() { return history.isEmpty() ? null : history.getLast(); }
}
