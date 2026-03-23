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
}
