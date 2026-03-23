package com.measim.ui.cli;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.measim.model.config.SimulationConfig;
import com.measim.service.ServiceModule;
import com.measim.service.simulation.SimulationService;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        String configPath = "config/default.yaml";
        boolean compare = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = args[++i];
                case "--compare" -> compare = true;
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
            simulation.run();
        }
    }
}
