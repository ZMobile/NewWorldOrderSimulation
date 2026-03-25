package com.measim.model.gamemaster;

import com.measim.model.infrastructure.InfrastructureType;
import com.measim.model.world.HexCoord;

import java.util.Map;

/**
 * A GM-evaluated proposal awaiting agent acceptance.
 * The agent sees: cost, construction time, approval status.
 * The agent does NOT see: risk profiles, hidden byproducts, GM reasoning.
 */
public class PendingProposal {

    public enum Status { AWAITING_AGENT, ACCEPTED, REJECTED, EXPIRED }
    public enum ProposalType { INFRASTRUCTURE, SERVICE, PROPERTY_CLAIM }

    private final String id;
    private final String agentId;
    private final ProposalType type;
    private final String name;
    private final String description;
    private final HexCoord location;
    private final double creditCost;
    private final Map<String, Integer> resourceCost; // e.g. {"TIMBER": 10, "MINERAL": 5}
    private final int constructionTimeTicks;
    private final String gmPublicResponse; // what the agent sees (cost + approval, no secrets)
    private final InfrastructureType infraType; // full GM evaluation (internal, not shown to agent)
    private final int createdTick;
    private Status status;

    public PendingProposal(String id, String agentId, ProposalType type, String name, String description,
                           HexCoord location, double creditCost, Map<String, Integer> resourceCost,
                           int constructionTimeTicks, String gmPublicResponse,
                           InfrastructureType infraType, int createdTick) {
        this.id = id;
        this.agentId = agentId;
        this.type = type;
        this.name = name;
        this.description = description;
        this.location = location;
        this.creditCost = creditCost;
        this.resourceCost = Map.copyOf(resourceCost);
        this.constructionTimeTicks = constructionTimeTicks;
        this.gmPublicResponse = gmPublicResponse;
        this.infraType = infraType;
        this.createdTick = createdTick;
        this.status = Status.AWAITING_AGENT;
    }

    public void accept() { this.status = Status.ACCEPTED; }
    public void reject() { this.status = Status.REJECTED; }
    public void expire() { this.status = Status.EXPIRED; }

    public String id() { return id; }
    public String agentId() { return agentId; }
    public ProposalType type() { return type; }
    public String name() { return name; }
    public HexCoord location() { return location; }
    public double creditCost() { return creditCost; }
    public Map<String, Integer> resourceCost() { return resourceCost; }
    public int constructionTimeTicks() { return constructionTimeTicks; }
    public String gmPublicResponse() { return gmPublicResponse; }
    public InfrastructureType infraType() { return infraType; }
    public int createdTick() { return createdTick; }
    public Status status() { return status; }

    /** What the agent sees — cost and approval only, no hidden info */
    public String agentSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Proposal '%s': ", name));
        sb.append(String.format("Cost: %.0f credits", creditCost));
        if (!resourceCost.isEmpty()) {
            sb.append(" + resources: ");
            resourceCost.forEach((k, v) -> sb.append(k).append("x").append(v).append(" "));
        }
        if (constructionTimeTicks > 0) {
            sb.append(String.format(", construction: %d ticks", constructionTimeTicks));
        }
        sb.append(". ").append(gmPublicResponse);
        sb.append(String.format(" Use {\"action\":\"ACCEPT_PROPOSAL\",\"proposalId\":\"%s\"} to proceed", id));
        sb.append(String.format(" or {\"action\":\"REJECT_PROPOSAL\",\"proposalId\":\"%s\"} to decline.", id));
        return sb.toString();
    }
}
