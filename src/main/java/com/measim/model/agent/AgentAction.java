package com.measim.model.agent;

import com.measim.model.world.HexCoord;

public sealed interface AgentAction {
    record Idle() implements AgentAction {}
    record Move(HexCoord destination) implements AgentAction {}
    record PurchaseRobot() implements AgentAction {}
    record InvestResearch(String direction, double creditInvestment) implements AgentAction {}
    record ContributeCommons(String description, double creditInvestment) implements AgentAction {}
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

    /** Offer a job/work contract to a specific agent. */
    record OfferJob(String targetAgentId, double wagesPerTick, int durationTicks, String description) implements AgentAction {}

    /** Accept a pending job offer. */
    record AcceptJob(String offererAgentId) implements AgentAction {}

    /** Propose a general contract (rental, partnership, service agreement). */
    record ProposeContract(String targetAgentId, String contractType, double valuePerTick,
                           int durationTicks, String terms) implements AgentAction {}

    /** Accept a pending contract proposal. */
    record AcceptContract(String proposerAgentId, String contractType) implements AgentAction {}

    /** Terminate an existing contract (quit job, end partnership, cancel agreement). */
    record TerminateContract(String contractId, String reason) implements AgentAction {}

    /** Register a property claim on a tile (first-come-first-served, validated by Governance GM). */
    record ClaimProperty(HexCoord tile) implements AgentAction {}

    /** Accept a pending GM proposal (infrastructure, service) after seeing the quote. */
    record AcceptProposal(String proposalId) implements AgentAction {}

    /** Reject a pending GM proposal. */
    record RejectProposal(String proposalId) implements AgentAction {}
}
