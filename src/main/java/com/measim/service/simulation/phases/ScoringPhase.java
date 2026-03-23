package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.model.config.SimulationConfig;
import com.measim.model.scoring.SectorBaseline;
import com.measim.service.scoring.ScoringService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ScoringPhase implements TickPhase {
    private final AgentDao agentDao;
    private final ScoringService scoringService;
    private final SimulationConfig config;

    @Inject
    public ScoringPhase(AgentDao agentDao, ScoringService scoringService, SimulationConfig config) {
        this.agentDao = agentDao;
        this.scoringService = scoringService;
        this.config = config;
    }

    @Override public String name() { return "Scoring"; }
    @Override public int order() { return 5; }

    @Override
    public void execute(int currentTick) {
        if (!config.measEnabled()) return;
        var baseline = scoringService.getSectorBaseline();
        var actorData = agentDao.getAllAgents().stream()
                .map(a -> new SectorBaseline.ActorData(a.id(), a.state().totalEmissions(),
                        a.state().totalRevenue(), a.state().humanEmployees(),
                        a.state().credits(), a.state().commonsScore())).toList();
        scoringService.updateBaseline(baseline, actorData);
        for (var agent : agentDao.getAllAgents()) {
            scoringService.updateScoreVector(agent.id(), agent.state().scoreVector(), baseline,
                    agent.state().totalEmissions(), agent.state().totalRevenue(),
                    agent.state().humanEmployees(), agent.state().credits(),
                    agent.state().commonsScore(), currentTick);
            agent.state().setModifiers(scoringService.computeModifiers(agent.state().scoreVector(), baseline));
        }
    }
}
