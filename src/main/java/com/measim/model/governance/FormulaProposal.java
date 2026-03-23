package com.measim.model.governance;

import java.util.HashMap;
import java.util.Map;

public class FormulaProposal {

    public enum Status { PENDING, COMMENT_PERIOD, VOTING, APPROVED, REJECTED, IMPLEMENTED }

    private final String id;
    private final String governmentId;
    private final String proposerAgentId;
    private final String description;
    private final String parameterName;
    private final double proposedValue;
    private final int tickProposed;
    private Status status;
    private final Map<String, Boolean> votes = new HashMap<>();
    private int implementationTick = -1;

    public FormulaProposal(String id, String governmentId, String proposerAgentId,
                           String description, String parameterName, double proposedValue, int tickProposed) {
        this.id = id;
        this.governmentId = governmentId;
        this.proposerAgentId = proposerAgentId;
        this.description = description;
        this.parameterName = parameterName;
        this.proposedValue = proposedValue;
        this.tickProposed = tickProposed;
        this.status = Status.PENDING;
    }

    public void castVote(String agentId, boolean inFavor) { votes.put(agentId, inFavor); }

    public double approvalRate() {
        if (votes.isEmpty()) return 0;
        long inFavor = votes.values().stream().filter(v -> v).count();
        return (double) inFavor / votes.size();
    }

    public String id() { return id; }
    public String governmentId() { return governmentId; }
    public String proposerAgentId() { return proposerAgentId; }
    public String description() { return description; }
    public String parameterName() { return parameterName; }
    public double proposedValue() { return proposedValue; }
    public int tickProposed() { return tickProposed; }
    public Status status() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Map<String, Boolean> votes() { return votes; }
    public int implementationTick() { return implementationTick; }
    public void setImplementationTick(int tick) { this.implementationTick = tick; }
}
