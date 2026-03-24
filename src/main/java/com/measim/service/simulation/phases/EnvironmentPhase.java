package com.measim.service.simulation.phases;

import com.measim.dao.WorldDao;
import com.measim.model.event.EventBus;
import com.measim.service.externality.ExternalityService;
import com.measim.service.infrastructure.InfrastructureService;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.EnvironmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EnvironmentPhase implements TickPhase {
    private final EnvironmentService environmentService;
    private final InfrastructureService infrastructureService;
    private final ExternalityService externalityService;
    private final WorldDao worldDao;
    private final EventBus eventBus;

    @Inject
    public EnvironmentPhase(EnvironmentService environmentService,
                             InfrastructureService infrastructureService,
                             ExternalityService externalityService,
                             WorldDao worldDao, EventBus eventBus) {
        this.environmentService = environmentService;
        this.infrastructureService = infrastructureService;
        this.externalityService = externalityService;
        this.worldDao = worldDao;
        this.eventBus = eventBus;
    }

    @Override public String name() { return "Environment"; }
    @Override public int order() { return 90; }

    @Override
    public void execute(int currentTick) {
        // Natural environment recovery
        environmentService.tickRecovery();

        // Resource regeneration and tile history tracking
        for (var tile : worldDao.getAllTiles()) {
            for (var resource : tile.resources()) resource.regenerate();
            if (tile.hasActiveProduction()) tile.history().recordInfrastructureTick();
            else tile.history().tickIdle();
        }

        // Check construction completion for infrastructure under construction
        for (var infra : worldDao.getAllTiles().stream()
                .flatMap(t -> t.structureIds().stream())
                .distinct().toList()) {
            // Construction checks handled inside tickMaintenance
        }

        // Infrastructure maintenance: owners pay upkeep or infra degrades
        infrastructureService.tickMaintenance(currentTick);

        // Process externalities: true byproducts applied to world, measurable ones tracked for EF scoring
        externalityService.processExternalities(currentTick);

        // Check for environmental crises
        long crisisTiles = environmentService.crisisTileCount();
        if (crisisTiles > 0)
            eventBus.publish(new EventBus.EnvironmentalCrisis(currentTick, (int) crisisTiles));
    }
}
