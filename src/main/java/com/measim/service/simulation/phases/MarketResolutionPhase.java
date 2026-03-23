package com.measim.service.simulation.phases;

import com.measim.dao.AgentDao;
import com.measim.dao.MarketDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.MemoryEntry;
import com.measim.model.config.SimulationConfig;
import com.measim.model.scoring.ModifierSet;
import com.measim.service.economy.CreditFlowService;
import com.measim.service.simulation.TickPhase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MarketResolutionPhase implements TickPhase {
    private final MarketDao marketDao;
    private final AgentDao agentDao;
    private final CreditFlowService creditFlowService;
    private final SimulationConfig config;

    @Inject
    public MarketResolutionPhase(MarketDao marketDao, AgentDao agentDao,
                                  CreditFlowService creditFlowService, SimulationConfig config) {
        this.marketDao = marketDao;
        this.agentDao = agentDao;
        this.creditFlowService = creditFlowService;
        this.config = config;
    }

    @Override public String name() { return "Market Resolution"; }
    @Override public int order() { return 4; }

    @Override
    public void execute(int currentTick) {
        for (var market : marketDao.getAllMarkets()) {
            for (var trade : market.resolveAll()) {
                Agent seller = agentDao.getAgent(trade.sellerId());
                Agent buyer = agentDao.getAgent(trade.buyerId());
                if (seller == null || buyer == null) continue;

                ModifierSet mods = config.measEnabled() ? seller.state().modifiers() : ModifierSet.NEUTRAL;
                var tx = creditFlowService.applyModifiers(trade, mods, config.measEnabled(), currentTick);

                buyer.state().spendCredits(tx.baseValue());
                seller.state().addCredits(tx.netCreditsToSeller());
                seller.state().addRevenue(tx.baseValue());
                buyer.state().addToInventory(tx.itemType(), tx.quantity());

                marketDao.recordTransaction(market.settlementId(), tx);
                seller.addMemory(new MemoryEntry(currentTick, "TRANSACTION",
                        String.format("Sold %d %s for %.1f (net: %.1f)",
                                tx.quantity(), tx.itemType().id(), tx.baseValue(), tx.netCreditsToSeller()),
                        0.5, tx.buyerId(), tx.netCreditsToSeller()));
            }
        }
    }
}
