package com.measim.service.trade;

import com.measim.dao.AgentDao;
import com.measim.dao.TradeDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.MemoryEntry;
import com.measim.model.economy.ItemType;
import com.measim.model.trade.TradeOffer;
import com.measim.service.communication.CommunicationService;
import com.measim.model.communication.Message;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class TradeServiceImpl implements TradeService {

    private final TradeDao tradeDao;
    private final AgentDao agentDao;
    private final CommunicationRangeService commRange;
    private final CommunicationService commService;
    private int nextOfferId = 1;

    @Inject
    public TradeServiceImpl(TradeDao tradeDao, AgentDao agentDao,
                             CommunicationRangeService commRange,
                             CommunicationService commService) {
        this.tradeDao = tradeDao;
        this.agentDao = agentDao;
        this.commRange = commRange;
        this.commService = commService;
    }

    @Override
    public String createOffer(String offererAgentId, String targetAgentId,
                               Map<ItemType, Integer> itemsOffered, Map<ItemType, Integer> itemsRequested,
                               double creditsOffered, double creditsRequested,
                               String message, int currentTick) {
        Agent offerer = agentDao.getAgent(offererAgentId);
        if (offerer == null) return null;

        // Validate offerer has what they're offering
        for (var entry : itemsOffered.entrySet()) {
            if (offerer.state().getInventoryCount(entry.getKey()) < entry.getValue()) return null;
        }
        if (creditsOffered > 0 && offerer.state().credits() < creditsOffered) return null;

        // If targeted, validate target exists and is in comm range
        if (targetAgentId != null) {
            Agent target = agentDao.getAgent(targetAgentId);
            if (target == null) return null;
            if (!commRange.canCommunicate(offerer, target)) return null;
        }

        String id = "trade_" + (nextOfferId++);
        TradeOffer offer = new TradeOffer(id, offererAgentId, targetAgentId,
                itemsOffered, itemsRequested, creditsOffered, creditsRequested,
                message, currentTick, currentTick + 3); // expire in 3 ticks

        tradeDao.addOffer(offer);

        // Log the offer as communication
        String target = targetAgentId != null ? targetAgentId : "OPEN";
        commService.logThought(offererAgentId,
                "Trade offer to " + target + ": " + offer.summary(),
                targetAgentId != null ? Message.Channel.AGENT_TO_AGENT : Message.Channel.BROADCAST,
                currentTick);

        return id;
    }

    @Override
    public boolean acceptOffer(String offerId, String acceptingAgentId, int currentTick) {
        TradeOffer offer = tradeDao.getOffer(offerId);
        if (offer == null || !offer.isPending()) return false;

        // If targeted, only the target can accept
        if (offer.targetAgentId() != null && !offer.targetAgentId().equals(acceptingAgentId)) return false;

        Agent offerer = agentDao.getAgent(offer.offererAgentId());
        Agent acceptor = agentDao.getAgent(acceptingAgentId);
        if (offerer == null || acceptor == null) return false;

        // Validate comm range
        if (!commRange.canCommunicate(offerer, acceptor)) return false;

        // Validate offerer still has offered items
        for (var entry : offer.itemsOffered().entrySet()) {
            if (offerer.state().getInventoryCount(entry.getKey()) < entry.getValue()) return false;
        }
        if (offer.creditsOffered() > 0 && offerer.state().credits() < offer.creditsOffered()) return false;

        // Validate acceptor has requested items
        for (var entry : offer.itemsRequested().entrySet()) {
            if (acceptor.state().getInventoryCount(entry.getKey()) < entry.getValue()) return false;
        }
        if (offer.creditsRequested() > 0 && acceptor.state().credits() < offer.creditsRequested()) return false;

        // Execute the swap atomically
        // Offerer gives items
        for (var entry : offer.itemsOffered().entrySet()) {
            offerer.state().removeFromInventory(entry.getKey(), entry.getValue());
            acceptor.state().addToInventory(entry.getKey(), entry.getValue());
        }
        // Acceptor gives items
        for (var entry : offer.itemsRequested().entrySet()) {
            acceptor.state().removeFromInventory(entry.getKey(), entry.getValue());
            offerer.state().addToInventory(entry.getKey(), entry.getValue());
        }
        // Credits
        if (offer.creditsOffered() > 0) {
            offerer.state().spendCredits(offer.creditsOffered());
            acceptor.state().addCredits(offer.creditsOffered());
        }
        if (offer.creditsRequested() > 0) {
            acceptor.state().spendCredits(offer.creditsRequested());
            offerer.state().addCredits(offer.creditsRequested());
        }

        offer.accept(acceptingAgentId);

        // Log the trade
        String tradeDesc = String.format("Trade completed: %s <-> %s: %s",
                offer.offererAgentId(), acceptingAgentId, offer.summary());
        commService.logThought(offer.offererAgentId(), tradeDesc,
                Message.Channel.AGENT_TO_AGENT, currentTick);
        commService.logThought(acceptingAgentId, "Accepted trade from " + offer.offererAgentId(),
                Message.Channel.AGENT_TO_AGENT, currentTick);

        // Memory for both agents
        offerer.addMemory(new MemoryEntry(currentTick, "TRADE", tradeDesc, 0.6, acceptingAgentId, 0));
        acceptor.addMemory(new MemoryEntry(currentTick, "TRADE",
                "Accepted trade from " + offer.offererAgentId(), 0.6, offer.offererAgentId(), 0));

        return true;
    }

    @Override
    public void rejectOffer(String offerId) {
        TradeOffer offer = tradeDao.getOffer(offerId);
        if (offer != null && offer.isPending()) offer.reject();
    }

    @Override
    public List<TradeOffer> getIncomingOffers(String agentId) {
        return tradeDao.getPendingOffersFor(agentId);
    }

    @Override
    public List<TradeOffer> getVisibleOpenOffers(String agentId) {
        Agent agent = agentDao.getAgent(agentId);
        if (agent == null) return List.of();
        int range = commRange.getEffectiveRange(agent);
        var loc = agent.state().location();
        return tradeDao.getOpenOffersAtTile(loc.q(), loc.r(), range);
    }

    @Override
    public void processTrades(int currentTick) {
        tradeDao.expireOldOffers(currentTick);
        // Clean up completed/expired offers older than 5 ticks
        for (TradeOffer offer : tradeDao.getAcceptedOffers()) {
            if (offer.createdTick() + 5 < currentTick) {
                tradeDao.removeOffer(offer.id());
            }
        }
    }
}
