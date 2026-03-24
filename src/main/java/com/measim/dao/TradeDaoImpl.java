package com.measim.dao;

import com.measim.model.trade.TradeOffer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class TradeDaoImpl implements TradeDao {

    private final ConcurrentHashMap<String, TradeOffer> offers = new ConcurrentHashMap<>();
    private final AgentDao agentDao;

    @Inject
    public TradeDaoImpl(AgentDao agentDao) {
        this.agentDao = agentDao;
    }

    @Override
    public void addOffer(TradeOffer offer) {
        offers.put(offer.id(), offer);
    }

    @Override
    public List<TradeOffer> getPendingOffersFor(String agentId) {
        return offers.values().stream()
                .filter(TradeOffer::isPending)
                .filter(o -> agentId.equals(o.targetAgentId()))
                .toList();
    }

    @Override
    public List<TradeOffer> getOpenOffersAtTile(int q, int r, int radius) {
        return offers.values().stream()
                .filter(TradeOffer::isPending)
                .filter(TradeOffer::isOpen)
                .filter(o -> {
                    var agent = agentDao.getAgent(o.offererAgentId());
                    if (agent == null) return false;
                    var loc = agent.state().location();
                    return Math.abs(loc.q() - q) + Math.abs(loc.r() - r) <= radius * 2;
                })
                .toList();
    }

    @Override
    public List<TradeOffer> getOffersByAgent(String agentId) {
        return offers.values().stream()
                .filter(o -> o.offererAgentId().equals(agentId))
                .toList();
    }

    @Override
    public TradeOffer getOffer(String offerId) {
        return offers.get(offerId);
    }

    @Override
    public void expireOldOffers(int currentTick) {
        offers.values().stream()
                .filter(TradeOffer::isPending)
                .filter(o -> o.isExpired(currentTick))
                .forEach(TradeOffer::expire);
    }

    @Override
    public List<TradeOffer> getAcceptedOffers() {
        return offers.values().stream()
                .filter(o -> o.status() == TradeOffer.Status.ACCEPTED)
                .toList();
    }

    @Override
    public void removeOffer(String offerId) {
        offers.remove(offerId);
    }
}
