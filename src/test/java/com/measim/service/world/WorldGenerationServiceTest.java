package com.measim.service.world;

import com.measim.dao.WorldDaoImpl;
import com.measim.model.config.SimulationConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldGenerationServiceTest {

    private WorldGenerationServiceImpl createService(long seed, int width, int height) {
        SimulationConfig config = new SimulationConfig();
        // Use reflection-free approach: load from defaults and override via test config
        // For now, use defaults with small world
        var dao = new WorldDaoImpl();
        return new WorldGenerationServiceImpl(dao, config) {
            // Override would require config changes; test with defaults
        };
    }

    @Test void generatesAndInitializesDao() {
        var dao = new WorldDaoImpl();
        var config = SimulationConfig.load(java.nio.file.Path.of("config/default.yaml"));
        var service = new WorldGenerationServiceImpl(dao, config);
        service.generateWorld();

        assertNotNull(dao.getGrid());
        assertEquals(config.worldWidth(), dao.getGrid().width());
        assertEquals(config.worldHeight(), dao.getGrid().height());
    }

    @Test void allTilesHaveTerrain() {
        var dao = new WorldDaoImpl();
        var config = SimulationConfig.load(java.nio.file.Path.of("config/default.yaml"));
        var service = new WorldGenerationServiceImpl(dao, config);
        service.generateWorld();

        for (var tile : dao.getAllTiles()) assertNotNull(tile.terrain());
    }

    @Test void deterministicWithSameSeed() {
        var dao1 = new WorldDaoImpl();
        var dao2 = new WorldDaoImpl();
        var config = SimulationConfig.load(java.nio.file.Path.of("config/default.yaml"));
        new WorldGenerationServiceImpl(dao1, config).generateWorld();
        new WorldGenerationServiceImpl(dao2, config).generateWorld();

        var grid1 = dao1.getGrid();
        var grid2 = dao2.getGrid();
        for (int q = 0; q < grid1.width(); q++)
            for (int r = 0; r < grid1.height(); r++)
                assertEquals(grid1.getTile(q, r).terrain(), grid2.getTile(q, r).terrain());
    }

    @Test void hasSettlementZones() {
        var dao = new WorldDaoImpl();
        var config = SimulationConfig.load(java.nio.file.Path.of("config/default.yaml"));
        new WorldGenerationServiceImpl(dao, config).generateWorld();
        assertFalse(dao.getSettlementZones().isEmpty());
    }

    @Test void hasResources() {
        var dao = new WorldDaoImpl();
        var config = SimulationConfig.load(java.nio.file.Path.of("config/default.yaml"));
        new WorldGenerationServiceImpl(dao, config).generateWorld();
        long resourceTiles = dao.getAllTiles().stream().filter(t -> !t.resources().isEmpty()).count();
        assertTrue(resourceTiles > 0);
    }
}
