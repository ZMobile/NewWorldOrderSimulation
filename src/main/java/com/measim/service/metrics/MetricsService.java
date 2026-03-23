package com.measim.service.metrics;

import com.measim.dao.MetricsDao;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface MetricsService {
    void collectTick(int currentTick);
    void exportCsv(Path outputPath) throws IOException;
    MetricsDao.TickMetrics getLatest();
    List<MetricsDao.TickMetrics> getHistory();
}
