package com.measim.service.simulation.phases;

import com.measim.dao.WorldDao;
import com.measim.model.event.EventBus;
import com.measim.service.infrastructure.InfrastructureService;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.EnvironmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EnvironmentPhase implements TickPhase {
    private final EnvironmentService environmentService;
    private final InfrastructureService infrastructureService;
    private final WorldDao worldDao;
    private final EventBus eventBus;

    @Inject
    public EnvironmentPhase(EnvironmentService environmentService,
                             InfrastructureService infrastructureService,
                             WorldDao worldDao, EventBus eventBus) {
        this.environmentService = environmentService;
        this.infrastructureService = infrastructureService;
        this.worldDao = worldDao;
        this.eventBus = eventBus;
    }

    @Override public String name() { return "Environment"; }
    @Override public int order() { return 90; }

    @Override
    public void execute(int currentTick) {
        // Natural environment recovery
        environmentService.tickRecovery();

        // Resource regeneration
        for (var tile : worldDao.getAllTiles())
            for (var resource : tile.resources()) resource.regenerate();

        // Infrastructure maintenance: owners pay upkeep or infra degrades
        infrastructureService.tickMaintenance(currentTick);

        // Check for environmental crises
        long crisisTiles = environmentService.crisisTileCount();
        if (crisisTiles > 0)
            eventBus.publish(new EventBus.EnvironmentalCrisis(currentTick, (int) crisisTiles));
    }
}
