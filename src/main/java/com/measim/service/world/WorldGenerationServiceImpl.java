package com.measim.service.world;

import com.measim.dao.WorldDao;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.ResourceType;
import com.measim.model.world.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class WorldGenerationServiceImpl implements WorldGenerationService {

    private final WorldDao worldDao;
    private final SimulationConfig config;

    @Inject
    public WorldGenerationServiceImpl(WorldDao worldDao, SimulationConfig config) {
        this.worldDao = worldDao;
        this.config = config;
    }

    @Override
    public void generateWorld() {
        int width = config.worldWidth();
        int height = config.worldHeight();
        long seed = config.seed();
        Random random = new Random(seed);

        HexGrid grid = new HexGrid(width, height);

        PerlinNoise elevationNoise = new PerlinNoise(seed);
        PerlinNoise moistureNoise = new PerlinNoise(seed + 1);
        PerlinNoise temperatureNoise = new PerlinNoise(seed + 2);

        for (int q = 0; q < width; q++) {
            for (int r = 0; r < height; r++) {
                double scale = 0.03;
                double elevation = elevationNoise.octaveNoise(q * scale, r * scale, config.noiseOctaves(), config.noisePersistence());
                double moisture = moistureNoise.octaveNoise(q * scale, r * scale, config.noiseOctaves(), config.noisePersistence());
                double temperature = temperatureNoise.octaveNoise(q * scale * 0.7, r * scale * 0.7, config.noiseOctaves() - 1, config.noisePersistence());

                TerrainType terrain = classifyTerrain(elevation, moisture, temperature);
                grid.setTile(q, r, new Tile(new HexCoord(q, r), terrain));
            }
        }

        placeResources(grid, random);
        placeSettlements(grid);
        worldDao.initialize(grid);
    }

    private TerrainType classifyTerrain(double elevation, double moisture, double temperature) {
        double e = (elevation + 1.0) / 2.0;
        double m = (moisture + 1.0) / 2.0;
        double t = (temperature + 1.0) / 2.0;

        if (e < 0.30) return TerrainType.WATER;
        if (e > 0.75) return TerrainType.MOUNTAIN;
        if (t < 0.25) return TerrainType.TUNDRA;
        if (m < 0.30) return TerrainType.DESERT;
        if (m > 0.65 && e < 0.45) return TerrainType.WETLAND;
        if (m > 0.50) return TerrainType.FOREST;
        return TerrainType.GRASSLAND;
    }

    private void placeResources(HexGrid grid, Random random) {
        for (Tile tile : grid.getAllTiles()) {
            if (random.nextDouble() > config.resourceDensity()) continue;
            for (ResourcePlacement p : getResourcesForTerrain(tile.terrain())) {
                if (random.nextDouble() < p.probability) {
                    double abundance = p.minAbundance +
                            random.nextGaussian() * (p.maxAbundance - p.minAbundance) * 0.3
                            + (p.maxAbundance - p.minAbundance) * 0.5;
                    abundance = Math.max(p.minAbundance, Math.min(p.maxAbundance, abundance));
                    double regenRate = p.regenerates ? abundance * 0.05 : 0;
                    tile.addResource(new ResourceNode(p.type, tile.coord(), abundance, regenRate));
                }
            }
        }
    }

    private List<ResourcePlacement> getResourcesForTerrain(TerrainType terrain) {
        return switch (terrain) {
            case MOUNTAIN -> List.of(
                    new ResourcePlacement(ResourceType.MINERAL, 0.6, 3, 10, false),
                    new ResourcePlacement(ResourceType.ENERGY, 0.2, 2, 6, false));
            case DESERT -> List.of(
                    new ResourcePlacement(ResourceType.ENERGY, 0.5, 4, 10, true),
                    new ResourcePlacement(ResourceType.MINERAL, 0.2, 2, 5, false));
            case GRASSLAND -> List.of(
                    new ResourcePlacement(ResourceType.FOOD_LAND, 0.7, 4, 10, true),
                    new ResourcePlacement(ResourceType.ENERGY, 0.2, 2, 6, true));
            case FOREST -> List.of(
                    new ResourcePlacement(ResourceType.TIMBER, 0.8, 3, 8, true),
                    new ResourcePlacement(ResourceType.FOOD_LAND, 0.2, 1, 4, true));
            case WATER -> List.of(
                    new ResourcePlacement(ResourceType.WATER_RESOURCE, 0.6, 5, 10, true),
                    new ResourcePlacement(ResourceType.ENERGY, 0.15, 3, 7, true));
            case WETLAND -> List.of(
                    new ResourcePlacement(ResourceType.WATER_RESOURCE, 0.7, 4, 9, true),
                    new ResourcePlacement(ResourceType.FOOD_LAND, 0.4, 2, 6, true));
            case TUNDRA -> List.of(new ResourcePlacement(ResourceType.MINERAL, 0.15, 1, 4, false));
        };
    }

    private void placeSettlements(HexGrid grid) {
        List<Tile> candidates = grid.getAllTiles().stream()
                .filter(t -> t.terrain().isPassable())
                .filter(t -> t.terrain() != TerrainType.TUNDRA).toList();

        record ScoredTile(Tile tile, double score) {}
        List<ScoredTile> scored = new ArrayList<>();
        for (Tile candidate : candidates) {
            List<Tile> nearby = grid.getTilesInRange(candidate.coord(), 5);
            Set<ResourceType> types = new HashSet<>();
            int total = 0;
            for (Tile n : nearby) { for (var res : n.resources()) { types.add(res.type()); total++; } }
            scored.add(new ScoredTile(candidate, types.size() * 10.0 + total));
        }
        scored.sort(Comparator.comparingDouble(ScoredTile::score).reversed());

        int target = Math.max(4, (grid.width() * grid.height()) / 2000);
        List<HexCoord> placed = new ArrayList<>();
        for (ScoredTile st : scored) {
            if (placed.size() >= target) break;
            if (placed.stream().anyMatch(p -> st.tile.coord().distanceTo(p) < 15)) continue;
            for (Tile t : grid.getTilesInRange(st.tile.coord(), 3))
                if (t.terrain().isPassable()) t.setSettlementZone(true);
            placed.add(st.tile.coord());
        }
    }

    private record ResourcePlacement(ResourceType type, double probability,
                                     double minAbundance, double maxAbundance, boolean regenerates) {}
}
