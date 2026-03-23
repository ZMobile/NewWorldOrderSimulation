package com.measim.service;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.measim.dao.DaoModule;
import com.measim.service.agent.*;
import com.measim.service.communication.*;
import com.measim.service.economy.*;
import com.measim.service.gamemaster.*;
import com.measim.service.comparison.*;
import com.measim.service.governance.*;
import com.measim.service.infrastructure.*;
import com.measim.service.risk.*;
import com.measim.service.llm.*;
import com.measim.service.snapshot.*;
import com.measim.service.metrics.*;
import com.measim.service.scoring.*;
import com.measim.service.simulation.*;
import com.measim.service.simulation.phases.*;
import com.measim.service.world.*;

public class ServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DaoModule());

        // World services
        bind(WorldGenerationService.class).to(WorldGenerationServiceImpl.class).in(Singleton.class);
        bind(EnvironmentService.class).to(EnvironmentServiceImpl.class).in(Singleton.class);
        bind(PathfindingService.class).to(PathfindingServiceImpl.class).in(Singleton.class);

        // Economy services
        bind(ProductionService.class).to(ProductionServiceImpl.class).in(Singleton.class);
        bind(CreditFlowService.class).to(CreditFlowServiceImpl.class).in(Singleton.class);

        // Scoring services
        bind(ScoringService.class).to(ScoringServiceImpl.class).in(Singleton.class);

        // Agent services
        bind(AgentSpawningService.class).to(AgentSpawningServiceImpl.class).in(Singleton.class);
        bind(AgentDecisionService.class).to(AgentDecisionServiceImpl.class).in(Singleton.class);

        // LLM services
        bind(LlmService.class).to(LlmServiceImpl.class).in(Singleton.class);

        // Game Master services
        bind(GameMasterService.class).to(GameMasterServiceImpl.class).in(Singleton.class);

        // Communication services
        bind(CommunicationService.class).to(CommunicationServiceImpl.class).in(Singleton.class);

        // Infrastructure services
        bind(InfrastructureService.class).to(InfrastructureServiceImpl.class).in(Singleton.class);

        // Risk services
        bind(RiskService.class).to(RiskServiceImpl.class).in(Singleton.class);

        // Governance services
        bind(GovernanceService.class).to(GovernanceServiceImpl.class).in(Singleton.class);

        // Metrics services
        bind(MetricsService.class).to(MetricsServiceImpl.class).in(Singleton.class);

        // Snapshot and comparison services
        bind(SnapshotService.class).to(SnapshotServiceImpl.class).in(Singleton.class);
        bind(ComparisonService.class).to(ComparisonServiceImpl.class).in(Singleton.class);

        // Simulation service
        bind(SimulationService.class).to(SimulationServiceImpl.class).in(Singleton.class);

        // Bind tick phases as a Set<TickPhase>
        Multibinder<TickPhase> phaseBinder = Multibinder.newSetBinder(binder(), TickPhase.class);
        phaseBinder.addBinding().to(PerceptionPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(DecisionPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(ActionExecutionPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(MarketResolutionPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(ScoringPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(UbiDistributionPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(GovernancePhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(EnvironmentPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(RiskPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(EventPhase.class).in(Singleton.class);
        phaseBinder.addBinding().to(MeasurementPhase.class).in(Singleton.class);
    }
}
