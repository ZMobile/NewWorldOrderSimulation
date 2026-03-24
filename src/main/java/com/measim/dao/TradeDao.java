package com.measim.dao;

import com.measim.model.trade.TradeOffer;

import java.util.List;

public interface TradeDao {
    void addOffer(TradeOffer offer);
    List<TradeOffer> getPendingOffersFor(String agentId);
    List<TradeOffer> getOpenOffersAtTile(int q, int r, int radius);
    List<TradeOffer> getOffersByAgent(String agentId);
    TradeOffer getOffer(String offerId);
    void expireOldOffers(int currentTick);
    List<TradeOffer> getAcceptedOffers();
    void removeOffer(String offerId);
}
