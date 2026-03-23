package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.model.config.SimulationConfig;
import com.measim.model.event.EventBus;
import com.measim.service.economy.CreditFlowService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UbiDistributionPhase implements TickPhase {
    private final AgentDao agentDao;
    private final CreditFlowService creditFlowService;
    private final SimulationConfig config;
    private final EventBus eventBus;

    @Inject
    public UbiDistributionPhase(AgentDao agentDao, CreditFlowService creditFlowService,
                                 SimulationConfig config, EventBus eventBus) {
        this.agentDao = agentDao;
        this.creditFlowService = creditFlowService;
        this.config = config;
        this.eventBus = eventBus;
    }

    @Override public String name() { return "UBI Distribution"; }
    @Override public int order() { return 6; }

    @Override
    public void execute(int currentTick) {
        if (!config.measEnabled() || !config.ubiEnabled()) return;
        if (currentTick % config.ubiDistributionInterval() != 0) return;
        int eligible = agentDao.getAgentCount();
        double perCapita = creditFlowService.distributeUbi(eligible);
        if (perCapita > 0) {
            for (var agent : agentDao.getAllAgents()) agent.state().addCredits(perCapita);
            eventBus.publish(new EventBus.UbiDistributed(currentTick, perCapita, eligible));
        }
    }
}
