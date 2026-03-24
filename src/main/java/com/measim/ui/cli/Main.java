package com.measim.ui.cli;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.measim.dao.AgentDao;
import com.measim.dao.MetricsDao;
import com.measim.dao.WorldDao;
import com.measim.model.config.SimulationConfig;
import com.measim.service.ServiceModule;
import com.measim.service.simulation.SimulationService;
import com.measim.ui.fx.SimulationViewer;
import javafx.application.Application;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        String configPath = "config/default.yaml";
        boolean compare = false;
        boolean visualize = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = args[++i];
                case "--compare" -> compare = true;
                case "--visualize", "--gui", "-v" -> visualize = true;
            }
        }

        SimulationConfig config = SimulationConfig.load(Path.of(configPath));

        Injector injector = Guice.createInjector(
                new ConfigModule(config),
                new ServiceModule()
        );

        SimulationService simulation = injector.getInstance(SimulationService.class);

        if (compare) {
            simulation.runComparison();
        } else {
            // Run the simulation headless first
            simulation.run();
        }

        if (visualize) {
            // Launch JavaFX viewer with the simulation state
            SimulationViewer.setDependencies(
                    injector.getInstance(WorldDao.class),
                    injector.getInstance(AgentDao.class),
                    injector.getInstance(MetricsDao.class)
            );
            Application.launch(SimulationViewer.class, args);
        }
    }
}
