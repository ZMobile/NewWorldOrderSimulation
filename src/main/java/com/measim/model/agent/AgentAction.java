package com.measim.model.agent;

import com.measim.model.economy.ItemType;
import com.measim.model.world.HexCoord;

public sealed interface AgentAction {
    record Idle() implements AgentAction {}
    record Move(HexCoord destination) implements AgentAction {}
    record Produce(String productionChainId) implements AgentAction {}
    record PlaceBuyOrder(ItemType itemType, int quantity, double maxPrice) implements AgentAction {}
    record PlaceSellOrder(ItemType itemType, int quantity, double minPrice) implements AgentAction {}
    record CreateBusiness(String productionChainId, HexCoord location) implements AgentAction {}
    record PurchaseRobot() implements AgentAction {}
    record InvestResearch(String direction, double creditInvestment) implements AgentAction {}
    record ContributeCommons(String description, double creditInvestment) implements AgentAction {}
    record ProposeGovernance(String proposal) implements AgentAction {}
    record Vote(String proposalId, boolean inFavor) implements AgentAction {}
    record Migrate(String targetGovernmentId) implements AgentAction {}
    record ExtractResource(HexCoord tile, ItemType resourceType, double amount) implements AgentAction {}
    record BuildInfrastructure(String typeId, HexCoord location, HexCoord connectTo) implements AgentAction {}
    record CreateService(String name, String description, String category, HexCoord location, double budget) implements AgentAction {}
    record ConsumeService(String serviceInstanceId) implements AgentAction {}

    /**
     * Free-form action: agent describes in natural language what they want to do.
     * The GM translates this into game mechanics (modifying existing entities,
     * creating new ones, combining multiple mechanical effects).
     *
     * Used for LLM-escalated strategic decisions where hardcoded action types
     * are too rigid. The deterministic pipeline still handles routine actions.
     */
    record FreeFormAction(String description, double creditBudget) implements AgentAction {}

    /** Offer a trade to a specific agent or as an open offer. */
    record OfferTrade(String targetAgentId, java.util.Map<com.measim.model.economy.ItemType, Integer> itemsOffered,
                      java.util.Map<com.measim.model.economy.ItemType, Integer> itemsRequested,
                      double creditsOffered, double creditsRequested, String message) implements AgentAction {}

    /** Accept a pending trade offer. */
    record AcceptTrade(String offerId) implements AgentAction {}

    /** Reject a pending trade offer. */
    record RejectTrade(String offerId) implements AgentAction {}

    /** Send a private message to a specific agent within comm range. */
    record SendMessage(String targetAgentId, String content) implements AgentAction {}

    /** Broadcast a message to all agents at the current tile/gathering point. */
    record BroadcastMessage(String content) implements AgentAction {}
}
