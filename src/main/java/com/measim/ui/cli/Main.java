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
            // Show launcher UI — user configures settings and presses Start
            launchWithUI(args);
        } else {
            // Headless or quick-start mode — run from config file
            SimulationConfig config = SimulationConfig.load(Path.of(configPath));
            runSimulation(config, compare, visualize, args);
        }
    }

    private static void launchWithUI(String[] args) {
        LauncherWindow.setOnLaunch(settings -> {
            System.out.printf("Launching: %d agents, %dx%d world, %d years, MEAS=%s, LLM=%s%n",
                    settings.agentCount(), settings.worldWidth(), settings.worldHeight(),
                    settings.totalYears(), settings.measEnabled(), settings.llmEnabled());

            new Thread(() -> {
                try {
                    String tempYaml = buildTempConfig(settings);
                    Path tempPath = Path.of("config/session.yaml");
                    java.nio.file.Files.writeString(tempPath, tempYaml);
                    SimulationConfig sessionConfig = SimulationConfig.load(tempPath);
                    runSimulation(sessionConfig, false, true, args);
                } catch (Exception e) {
                    System.err.println("Failed to start: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        });

        Application.launch(LauncherWindow.class, args);
    }

    private static void runSimulation(SimulationConfig config, boolean compare,
                                       boolean visualize, String[] args) {
        Injector injector = Guice.createInjector(
                new ConfigModule(config),
                new ServiceModule()
        );

        SimulationService simulation = injector.getInstance(SimulationService.class);

        if (compare) {
            simulation.runComparison();
        } else {
            simulation.run();
        }

        if (visualize) {
            SimulationViewer.setDependencies(
                    injector.getInstance(WorldDao.class),
                    injector.getInstance(AgentDao.class),
                    injector.getInstance(MetricsDao.class)
            );
            SimulationViewer.setCommunicationDao(injector.getInstance(CommunicationDao.class));
            Platform.runLater(() -> {
                try {
                    new SimulationViewer().start(new javafx.stage.Stage());
                } catch (Exception e) {
                    System.err.println("Visualizer failed: " + e.getMessage());
                }
            });
        }
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
                  maxAgentCallsPerTick: 50
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
                s.llmEnabled() ? s.apiKey() : "",
                s.model(), s.model(), s.model(),
                s.budgetUsd(),
                s.totalYears());
    }
}
