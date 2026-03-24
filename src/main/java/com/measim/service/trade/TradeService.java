package com.measim.service.trade;

import com.measim.model.economy.ItemType;
import com.measim.model.trade.TradeOffer;

import java.util.List;
import java.util.Map;

public interface TradeService {
    /**
     * Create a trade offer. Validates offerer has the items/credits.
     * @return the offer ID, or null if validation fails
     */
    String createOffer(String offererAgentId, String targetAgentId,
                       Map<ItemType, Integer> itemsOffered, Map<ItemType, Integer> itemsRequested,
                       double creditsOffered, double creditsRequested,
                       String message, int currentTick);

    /** Accept a pending offer. Validates both parties have items, are in comm range. */
    boolean acceptOffer(String offerId, String acceptingAgentId, int currentTick);

    /** Reject a pending offer. */
    void rejectOffer(String offerId);

    /** Get pending offers directed at this agent. */
    List<TradeOffer> getIncomingOffers(String agentId);

    /** Get open offers visible to this agent (within comm range). */
    List<TradeOffer> getVisibleOpenOffers(String agentId);

    /** Expire old offers and execute accepted trades. Called each tick. */
    void processTrades(int currentTick);
}
