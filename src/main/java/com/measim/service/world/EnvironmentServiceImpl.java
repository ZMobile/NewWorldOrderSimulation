package com.measim.service.world;

import com.measim.dao.WorldDao;
import com.measim.model.world.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EnvironmentServiceImpl implements EnvironmentService {

    private static final double DIFFUSION_RATE = 0.3;
    private final WorldDao worldDao;

    @Inject
    public EnvironmentServiceImpl(WorldDao worldDao) { this.worldDao = worldDao; }

    @Override
    public void applyProductionPollution(HexCoord source, double pollutionAmount) {
        Tile sourceTile = worldDao.getTile(source);
        if (sourceTile == null) return;
        sourceTile.environment().applyPollution(pollutionAmount * 0.1);
        for (int radius = 1; radius <= 3; radius++) {
            for (HexCoord neighbor : source.ring(radius)) {
                Tile tile = worldDao.getTile(neighbor);
                if (tile != null)
                    tile.environment().applyDiffusedPollution(pollutionAmount * 0.1 * DIFFUSION_RATE, radius);
            }
        }
    }

    @Override
    public void tickRecovery() {
        for (Tile tile : worldDao.getAllTiles())
            tile.environment().naturalRecovery(tile.hasActiveProduction());
    }

    @Override
    public double averageEnvironmentalHealth() {
        return worldDao.getAllTiles().stream()
                .mapToDouble(t -> t.environment().averageHealth()).average().orElse(1.0);
    }

    @Override
    public double regionHealth(HexCoord center, int radius) {
        return worldDao.getTilesInRange(center, radius).stream()
                .mapToDouble(t -> t.environment().averageHealth()).average().orElse(1.0);
    }

    @Override
    public long crisisTileCount() {
        return worldDao.getAllTiles().stream().filter(t -> t.environment().isCrisis()).count();
    }
}
