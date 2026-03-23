package com.measim.ui.cli;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.measim.model.config.SimulationConfig;
import com.measim.model.event.EventBus;

public class ConfigModule extends AbstractModule {

    private final SimulationConfig config;

    public ConfigModule(SimulationConfig config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        bind(SimulationConfig.class).toInstance(config);
        bind(EventBus.class).in(Singleton.class);
    }
}
