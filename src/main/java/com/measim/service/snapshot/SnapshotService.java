package com.measim.service.snapshot;

import java.io.IOException;
import java.nio.file.Path;

public interface SnapshotService {
    void saveSnapshot(int currentTick, Path outputDir) throws IOException;
    void loadSnapshot(Path snapshotFile) throws IOException;
}
