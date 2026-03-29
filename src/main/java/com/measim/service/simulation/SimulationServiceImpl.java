package com.measim.service.simulation;

import com.measim.dao.*;
import com.measim.model.config.SimulationConfig;
import com.measim.model.event.EventBus;
import com.measim.service.agent.AgentSpawningService;
import com.measim.service.gamemaster.GameMasterService;
import com.measim.service.governance.GovernanceService;
import com.measim.service.comparison.ComparisonService;
import com.measim.service.infrastructure.InfrastructureService;
import com.measim.service.metrics.MetricsService;
import com.measim.service.property.PropertyService;
import com.measim.service.reserve.ReserveService;
import com.measim.service.snapshot.SnapshotService;
import com.measim.service.world.WorldGenerationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Singleton
public class SimulationServiceImpl implements SimulationService {

    private final SimulationConfig config;
    private final EventBus eventBus;
    private final WorldGenerationService worldGenerationService;
    private final AgentSpawningService agentSpawningService;
    private final GameMasterService gameMasterService;
    private final GovernanceService governanceService;
    private final InfrastructureService infrastructureService;
    private final PropertyService propertyService;
    private final ReserveService reserveService;
    private final MetricsService metricsService;
    private final SnapshotService snapshotService;
    private final ComparisonService comparisonService;
    private final WorldDao worldDao;
    private final AgentDao agentDao;
    private final MarketDao marketDao;
    private final ProductionChainDao chainDao;
    private final InfrastructureDao infrastructureDao;
    private final Set<TickPhase> phases;
    private int currentTick = 0;
    private boolean initialized = false;
    private boolean hasRun = false;

    @Inject
    public SimulationServiceImpl(SimulationConfig config, EventBus eventBus,
                                  WorldGenerationService worldGenerationService,
                                  AgentSpawningService agentSpawningService,
                                  GameMasterService gameMasterService,
                                  GovernanceService governanceService,
                                  InfrastructureService infrastructureService,
                                  PropertyService propertyService,
                                  ReserveService reserveService,
                                  MetricsService metricsService,
                                  SnapshotService snapshotService,
                                  ComparisonService comparisonService,
                                  WorldDao worldDao, AgentDao agentDao,
                                  MarketDao marketDao, ProductionChainDao chainDao,
                                  InfrastructureDao infrastructureDao,
                                  Set<TickPhase> phases) {
        this.config = config;
        this.eventBus = eventBus;
        this.worldGenerationService = worldGenerationService;
        this.agentSpawningService = agentSpawningService;
        this.gameMasterService = gameMasterService;
        this.governanceService = governanceService;
        this.infrastructureService = infrastructureService;
        this.propertyService = propertyService;
        this.reserveService = reserveService;
        this.metricsService = metricsService;
        this.snapshotService = snapshotService;
        this.comparisonService = comparisonService;
        this.worldDao = worldDao;
        this.agentDao = agentDao;
        this.marketDao = marketDao;
        this.chainDao = chainDao;
        this.infrastructureDao = infrastructureDao;
        this.phases = phases;
    }

    @Override
    public void initialize() {
        if (initialized) return;
        initialized = true;
        // Clear stale output from previous runs
        try {
            java.nio.file.Path outputDir = java.nio.file.Path.of("output");
            if (java.nio.file.Files.exists(outputDir)) {
                for (String file : new String[]{"metrics.csv", "metrics_live.csv", "communication_log.json"}) {
                    java.nio.file.Files.deleteIfExists(outputDir.resolve(file));
                }
                java.nio.file.Path snapshotDir = outputDir.resolve("snapshots");
                if (java.nio.file.Files.exists(snapshotDir)) {
                    try (var files = java.nio.file.Files.list(snapshotDir)) {
                        files.forEach(f -> { try { java.nio.file.Files.delete(f); } catch (Exception ignored) {} });
                    }
                }
            }
        } catch (Exception e) { /* non-critical */ }

        System.out.println("Generating world...");
        worldGenerationService.generateWorld();

        // Register default production chains
        com.measim.model.economy.ProductionChain.createBase("basic_construction", "Basic Construction",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.MINERAL), 10,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.ENERGY), 5),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.CONSTRUCTION), 3),
                2.0, 1);
        registerDefaultChains();

        // Create markets at settlements
        var settlements = worldDao.getSettlementZones();
        java.util.Set<String> marketIds = new java.util.HashSet<>();
        for (var t : settlements) {
            String id = "market_" + t.coord().q() + "_" + t.coord().r();
            if (marketIds.add(id)) marketDao.getOrCreateMarket(id);
        }
        if (marketDao.getAllMarkets().isEmpty()) marketDao.getOrCreateMarket("default");

        // Initialize tech tree and governance
        // Initialize systems
        gameMasterService.initializeBaseTechTree();
        governanceService.initializeGovernments();
        propertyService.initializePropertySystem();
        reserveService.initializeReserve();

        System.out.println("Spawning " + config.agentCount() + " agents...");
        agentSpawningService.spawnAgents();

        // Optional: spawn communal gathering points at settlement cluster centers
        // These give base communication range (+3) and marketplace effect.
        // Represents a natural clearing/village square — not a building.
        if (config.communalGatheringPoints()) {
            spawnGatheringPoints(settlements);
        }

        System.out.printf("World ready: %d tiles, %d settlements, %d markets, %d governments%n",
                worldDao.getAllTiles().size(), settlements.size(), marketDao.getAllMarkets().size(),
                config.governments().size());
    }

    @Override
    public void run() {
        if (hasRun) return;
        hasRun = true;
        initialize();
        int totalTicks = config.totalTicks();
        List<TickPhase> orderedPhases = phases.stream()
                .sorted(Comparator.comparingInt(TickPhase::order)).toList();

        System.out.printf("Starting MeritSim: %d agents, %dx%d world, %d ticks (%d years), MERIT=%s%n",
                agentDao.getAgentCount(), config.worldWidth(), config.worldHeight(),
                totalTicks, config.totalYears(), config.measEnabled() ? "ON" : "OFF");

        System.out.printf("LLM available: %s%n", config.hasApiKey() ? "YES" : "NO (deterministic mode)");
        System.out.flush();

        for (int tick = 0; tick < totalTicks; tick++) {
            currentTick = tick + 1;
            int month = ((currentTick - 1) % config.ticksPerYear()) + 1;
            int year = ((currentTick - 1) / config.ticksPerYear()) + 1;
            System.out.printf("--- Tick %d (Year %d, Month %d) ---%n", currentTick, year, month);
            System.out.flush();

            eventBus.publish(new EventBus.TickStarted(currentTick));

            for (TickPhase phase : orderedPhases) {
                long phaseStart = System.currentTimeMillis();
                System.out.printf("  > %s...%n", phase.name());
                System.out.flush();
                phase.execute(currentTick);
                long elapsed = System.currentTimeMillis() - phaseStart;
                System.out.printf("  [%s] %.1fs%n", phase.name(), elapsed / 1000.0);
                System.out.flush();
            }

            eventBus.publish(new EventBus.TickCompleted(currentTick));

            if (currentTick % config.ticksPerYear() == 0) {
                int yr = currentTick / config.ticksPerYear();
                var m = metricsService.getLatest();
                if (m != null)
                    System.out.printf("  Year %d/%d | Gini: %.3f | Env: %.3f | Avg Credits: %.0f | Robots: %d | Reserve: %.1f%%%n",
                            yr, config.totalYears(), m.giniCoefficient(),
                            m.environmentalHealth(), m.averageCredits(), m.totalRobots(),
                            reserveService.reserveRatio() * 100);

                // GM manages reserve yearly (Opus call)
                reserveService.gmManageReserve(currentTick);
            }

            // Save snapshots + comm log periodically
            if (config.snapshotInterval() > 0 && currentTick % config.snapshotInterval() == 0) {
                try {
                    snapshotService.saveSnapshot(currentTick, Path.of("output/snapshots"));
                } catch (IOException e) {
                    System.err.println("Snapshot failed: " + e.getMessage());
                }
            }
            // Export comm log every tick so it's always available for inspection
            try {
                snapshotService.exportFullCommunicationLog(Path.of("output/communication_log.json"));
            } catch (IOException e) { /* non-critical */ }
        }

        System.out.println("Simulation complete.");
        try {
            metricsService.exportCsv(Path.of("output/metrics.csv"));
            snapshotService.exportFullCommunicationLog(Path.of("output/communication_log.json"));
            System.out.println("Metrics exported to output/metrics.csv");
            System.out.println("Communication log exported to output/communication_log.json");
        } catch (IOException e) {
            System.err.println("Failed to export metrics: " + e.getMessage());
        }
    }

    @Override
    public void runComparison() {
        System.out.println("=== Running MERIT scenario ===");
        run();
        comparisonService.recordScenario("MERIT", metricsService.getHistory());
        // Note: full comparison requires a second run with MERIT disabled (same seed)
        // which needs a fresh injector. For now, record the MERIT run.
        System.out.println("=== MERIT run recorded. Run again with meas.enabled=false for baseline. ===");
    }

    @Override
    public int currentTick() { return currentTick; }

    private void spawnGatheringPoints(java.util.List<com.measim.model.world.Tile> settlements) {
        // Find cluster centers (settlement zones are placed with 15-tile spacing)
        // Pick one tile per cluster to place a gathering point
        java.util.Set<com.measim.model.world.HexCoord> placed = new java.util.HashSet<>();
        for (var tile : settlements) {
            if (placed.stream().anyMatch(p -> tile.coord().distanceTo(p) < 10)) continue;
            placed.add(tile.coord());

            // Create a communal gathering point — public infrastructure, no owner
            var gatheringType = com.measim.model.infrastructure.InfrastructureType.predefined(
                    "gathering_" + tile.coord().q() + "_" + tile.coord().r(),
                    "Village Gathering Point",
                    "A natural clearing where agents meet, trade, and communicate. Not a building.",
                    com.measim.model.infrastructure.InfrastructureType.ConnectionMode.AREA_OF_EFFECT,
                    java.util.List.of(
                            new com.measim.model.infrastructure.InfrastructureEffect(
                                    com.measim.model.infrastructure.InfrastructureEffect.EffectType.COMMUNICATION_RANGE, 5.0, null),
                            new com.measim.model.infrastructure.InfrastructureEffect(
                                    com.measim.model.infrastructure.InfrastructureEffect.EffectType.MARKETPLACE, 1.0, null)
                    ),
                    0, 0.1, // no construction cost, minimal maintenance
                    5, 10   // range 5, capacity 10
            );

            infrastructureDao.registerType(gatheringType);
            var infra = new com.measim.model.infrastructure.Infrastructure(
                    gatheringType.id(), gatheringType, "PUBLIC", tile.coord(), null, 0);
            infrastructureDao.place(infra);
        }
        System.out.printf("Placed %d communal gathering points at settlement clusters.%n", placed.size());
    }

    private void registerDefaultChains() {
        var RT = com.measim.model.economy.ResourceType.class;
        var PT = com.measim.model.economy.ProductType.class;
        var I = com.measim.model.economy.ItemType.class;
        var PC = com.measim.model.economy.ProductionChain.class;

        chainDao.register(com.measim.model.economy.ProductionChain.createBase("basic_construction", "Basic Construction",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.MINERAL), 10,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.ENERGY), 5),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.CONSTRUCTION), 3), 2.0, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("basic_farming", "Basic Farming",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.FOOD_LAND), 1,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.WATER_RESOURCE), 2),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.FOOD), 8), 0.5, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("basic_technology", "Basic Technology",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.MINERAL), 3,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.ENERGY), 8),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.TECHNOLOGY), 2), 1.5, 2));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("luxury_manufacturing", "Luxury Manufacturing",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.MINERAL), 5,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.ENERGY), 10,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.TECHNOLOGY), 2),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.LUXURY), 1), 3.0, 2));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("basic_goods", "Basic Goods Production",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.TIMBER), 4,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.MINERAL), 2),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.BASIC_GOODS), 5), 1.0, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("medicine_production", "Medicine Production",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.FOOD_LAND), 2,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.WATER_RESOURCE), 3,
                        com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.TECHNOLOGY), 1),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.MEDICINE), 2), 0.8, 2));

        // Single-resource chains: let agents on any terrain produce something
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("foraging", "Foraging",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.FOOD_LAND), 2),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.FOOD), 3), 0.2, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("timber_crafting", "Timber Crafting",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.TIMBER), 3),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.BASIC_GOODS), 2), 0.3, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("mineral_refining", "Mineral Refining",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.MINERAL), 4),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.CONSTRUCTION), 1), 1.0, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("water_purification", "Water Purification",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.WATER_RESOURCE), 3),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.MEDICINE), 1), 0.1, 1));
        chainDao.register(com.measim.model.economy.ProductionChain.createBase("energy_tech", "Energy Tech",
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ResourceType.ENERGY), 5),
                java.util.Map.of(com.measim.model.economy.ItemType.of(com.measim.model.economy.ProductType.TECHNOLOGY), 1), 0.5, 1));
    }
}
