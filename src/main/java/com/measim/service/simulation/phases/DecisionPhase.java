package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.dao.MarketDao;
import com.measim.model.agent.AgentAction;
import com.measim.model.economy.*;
import com.measim.service.agent.AgentDecisionService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class DecisionPhase implements TickPhase {
    private final AgentDao agentDao;
    private final MarketDao marketDao;
    private final AgentDecisionService decisionService;
    private final Map<String, AgentAction> pendingActions = new HashMap<>();

    @Inject
    public DecisionPhase(AgentDao agentDao, MarketDao marketDao, AgentDecisionService decisionService) {
        this.agentDao = agentDao;
        this.marketDao = marketDao;
        this.decisionService = decisionService;
    }

    @Override public String name() { return "Decision"; }
    @Override public int order() { return 20; }

    @Override
    public void execute(int currentTick) {
        pendingActions.clear();
        Map<ItemType, Double> prices = buildPriceSnapshot();
        for (var agent : agentDao.getAllAgents())
            pendingActions.put(agent.id(), decisionService.decideStrategicAction(agent, prices, currentTick));
    }

    public Map<String, AgentAction> pendingActions() { return Collections.unmodifiableMap(pendingActions); }

    Map<ItemType, Double> buildPriceSnapshot() {
        Map<ItemType, Double> prices = new HashMap<>();
        for (ResourceType r : ResourceType.values()) prices.put(ItemType.of(r), 2.0);
        for (ProductType p : ProductType.values()) prices.put(ItemType.of(p), 10.0);
        // Override with market data
        for (var market : marketDao.getAllMarkets()) {
            for (ResourceType r : ResourceType.values()) {
                double p = market.getLastPrice(ItemType.of(r));
                if (p > 0) prices.put(ItemType.of(r), p);
            }
            for (ProductType pt : ProductType.values()) {
                double p = market.getLastPrice(ItemType.of(pt));
                if (p > 0) prices.put(ItemType.of(pt), p);
            }
        }
        return prices;
    }
}
