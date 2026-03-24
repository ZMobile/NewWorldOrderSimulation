package com.measim.ui.cli;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.measim.dao.AgentDao;
import com.measim.dao.CommunicationDao;
import com.measim.dao.MetricsDao;
import com.measim.dao.WorldDao;
import com.measim.model.config.SimulationConfig;
import com.measim.service.ServiceModule;
import com.measim.service.simulation.SimulationService;
import com.measim.ui.fx.LauncherWindow;
import com.measim.ui.fx.SimulationViewer;
import javafx.application.Application;
import javafx.application.Platform;

import java.nio.file.Path;

public class Main {

    // Shared state between launcher callback and visualizer
    private static Injector sharedInjector;

    public static void main(String[] args) {
        String configPath = "config/default.yaml";
        boolean compare = false;
        boolean visualize = false;
        boolean quick = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = args[++i];
                case "--compare" -> compare = true;
                case "--visualize", "--gui", "-v" -> visualize = true;
                case "--quick", "-q" -> quick = true;
            }
        }

        if (visualize && !quick) {
            launchWithUI(args);
        } else if (visualize) {
            // Quick-start: initialize, open viewer, run in background
            SimulationConfig config = SimulationConfig.load(Path.of(configPath));
            launchQuickVisualizer(config, compare, args);
        } else {
            // Headless
            SimulationConfig config = SimulationConfig.load(Path.of(configPath));
            runHeadless(config, compare);
        }
    }

    private static void launchWithUI(String[] args) {
        LauncherWindow.setOnLaunch(settings -> {
            System.out.printf("Launching: %d agents, %dx%d world, %d years, MEAS=%s, LLM=%s%n",
                    settings.agentCount(), settings.worldWidth(), settings.worldHeight(),
                    settings.totalYears(), settings.measEnabled(), settings.llmEnabled());

            try {
                String tempYaml = buildTempConfig(settings);
                Path tempPath = Path.of("config/session.yaml");
                java.nio.file.Files.writeString(tempPath, tempYaml);
                SimulationConfig config = SimulationConfig.load(tempPath);

                // Create injector and initialize world (fast — no LLM calls)
                sharedInjector = Guice.createInjector(new ConfigModule(config), new ServiceModule());
                SimulationService sim = sharedInjector.getInstance(SimulationService.class);
                sim.initialize();

                // Set viewer dependencies
                SimulationViewer.setDependencies(
                        sharedInjector.getInstance(WorldDao.class),
                        sharedInjector.getInstance(AgentDao.class),
                        sharedInjector.getInstance(MetricsDao.class));
                SimulationViewer.setCommunicationDao(sharedInjector.getInstance(CommunicationDao.class));

                // Open viewer immediately on FX thread
                Platform.runLater(() -> {
                    try {
                        new SimulationViewer().start(new javafx.stage.Stage());
                    } catch (Exception e) {
                        System.err.println("Visualizer failed: " + e.getMessage());
                    }
                });

                // Run simulation ticks in background
                new Thread(() -> {
                    sim.run(); // This now skips initialize() since it's already done
                    System.out.println("Simulation complete. Visualizer remains open.");
                }).start();

            } catch (Exception e) {
                System.err.println("Failed to start: " + e.getMessage());
                e.printStackTrace();
            }
        });

        Application.launch(LauncherWindow.class, args);
    }

    private static void launchQuickVisualizer(SimulationConfig config, boolean compare, String[] args) {
        Injector injector = Guice.createInjector(new ConfigModule(config), new ServiceModule());
        SimulationService sim = injector.getInstance(SimulationService.class);
        sim.initialize();

        SimulationViewer.setDependencies(
                injector.getInstance(WorldDao.class),
                injector.getInstance(AgentDao.class),
                injector.getInstance(MetricsDao.class));
        SimulationViewer.setCommunicationDao(injector.getInstance(CommunicationDao.class));

        // Run sim in background, launch viewer on main thread
        new Thread(() -> {
            if (compare) sim.runComparison();
            else sim.run();
            System.out.println("Simulation complete.");
        }).start();

        Application.launch(SimulationViewer.class, args);
    }

    private static void runHeadless(SimulationConfig config, boolean compare) {
        Injector injector = Guice.createInjector(new ConfigModule(config), new ServiceModule());
        SimulationService sim = injector.getInstance(SimulationService.class);
        if (compare) sim.runComparison();
        else sim.run();
    }

    private static String resolveApiKey(LauncherWindow.LaunchSettings s) {
        if (!s.llmEnabled()) return "";
        // Use the key from the text field if provided
        if (s.apiKey() != null && !s.apiKey().isEmpty()) return s.apiKey();
        // Fall back to environment variable
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            System.out.println("API key resolved from ANTHROPIC_API_KEY environment variable.");
            return envKey;
        }
        System.out.println("WARNING: LLM enabled but no API key found. Will use deterministic fallback.");
        return "";
    }

    private static String buildTempConfig(LauncherWindow.LaunchSettings s) {
        return String.format("""
                world:
                  seed: %d
                  width: %d
                  height: %d
                  resourceDensity: 0.3
                  terrainNoise:
                    octaves: 6
                    persistence: 0.5

                agents:
                  count: %d
                  perceptionRadius: 7

                robots:
                  initialCost: 10000
                  costDecayRate: 0.05
                  initialEfficiency: 1.2
                  efficiencyGrowthRate: 0.03

                meas:
                  enabled: %s
                  formulaVersion: "v0.1.0"
                  ubiEnabled: %s
                  ubiAdequacyTarget: 0.85
                  baseTransactionTax: 0.005

                llm:
                  provider: "anthropic"
                  apiKey: "%s"
                  apiBaseUrl: "https://api.anthropic.com"
                  agentModel: "%s"
                  complexModel: "%s"
                  gameMasterModel: "%s"
                  maxAgentCallsPerTick: 9999
                  maxGameMasterCallsPerTick: 20
                  totalBudgetUsd: %.1f
                  cacheEnabled: true
                  cacheTtlTicks: 5

                simulation:
                  ticksPerYear: 12
                  totalYears: %d
                  snapshotInterval: 12
                  metricsInterval: 1
                """,
                s.seed(), s.worldWidth(), s.worldHeight(),
                s.agentCount(),
                s.measEnabled(), s.measEnabled(),
                resolveApiKey(s),
                s.model(), s.model(), s.model(),
                s.budgetUsd(),
                s.totalYears());
    }
}
