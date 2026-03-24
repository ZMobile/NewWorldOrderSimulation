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
    private final MetricsService metricsService;
    private final SnapshotService snapshotService;
    private final ComparisonService comparisonService;
    private final WorldDao worldDao;
    private final AgentDao agentDao;
    private final MarketDao marketDao;
    private final ProductionChainDao chainDao;
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
                                  MetricsService metricsService,
                                  SnapshotService snapshotService,
                                  ComparisonService comparisonService,
                                  WorldDao worldDao, AgentDao agentDao,
                                  MarketDao marketDao, ProductionChainDao chainDao,
                                  Set<TickPhase> phases) {
        this.config = config;
        this.eventBus = eventBus;
        this.worldGenerationService = worldGenerationService;
        this.agentSpawningService = agentSpawningService;
        this.gameMasterService = gameMasterService;
        this.governanceService = governanceService;
        this.infrastructureService = infrastructureService;
        this.propertyService = propertyService;
        this.metricsService = metricsService;
        this.snapshotService = snapshotService;
        this.comparisonService = comparisonService;
        this.worldDao = worldDao;
        this.agentDao = agentDao;
        this.marketDao = marketDao;
        this.chainDao = chainDao;
        this.phases = phases;
    }

    @Override
    public void initialize() {
        if (initialized) return;
        initialized = true;
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

        System.out.println("Spawning " + config.agentCount() + " agents...");
        agentSpawningService.spawnAgents();

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

        System.out.printf("Starting MeaSim: %d agents, %dx%d world, %d ticks (%d years), MEAS=%s%n",
                agentDao.getAgentCount(), config.worldWidth(), config.worldHeight(),
                totalTicks, config.totalYears(), config.measEnabled() ? "ON" : "OFF");

        for (int tick = 0; tick < totalTicks; tick++) {
            currentTick = tick + 1;
            eventBus.publish(new EventBus.TickStarted(currentTick));

            for (TickPhase phase : orderedPhases) phase.execute(currentTick);

            eventBus.publish(new EventBus.TickCompleted(currentTick));

            if (currentTick % config.ticksPerYear() == 0) {
                int year = currentTick / config.ticksPerYear();
                var m = metricsService.getLatest();
                if (m != null)
                    System.out.printf("  Year %d/%d | Gini: %.3f | Env: %.3f | Avg Credits: %.0f | Robots: %d%n",
                            year, config.totalYears(), m.giniCoefficient(),
                            m.environmentalHealth(), m.averageCredits(), m.totalRobots());
            }

            // Save snapshots
            if (config.snapshotInterval() > 0 && currentTick % config.snapshotInterval() == 0) {
                try {
                    snapshotService.saveSnapshot(currentTick, Path.of("output/snapshots"));
                } catch (IOException e) {
                    System.err.println("Snapshot failed: " + e.getMessage());
                }
            }
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
        System.out.println("=== Running MEAS scenario ===");
        run();
        comparisonService.recordScenario("MEAS", metricsService.getHistory());
        // Note: full comparison requires a second run with MEAS disabled (same seed)
        // which needs a fresh injector. For now, record the MEAS run.
        System.out.println("=== MEAS run recorded. Run again with meas.enabled=false for baseline. ===");
    }

    @Override
    public int currentTick() { return currentTick; }

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
