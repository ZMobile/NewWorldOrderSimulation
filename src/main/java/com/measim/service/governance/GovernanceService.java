package com.measim.service.governance;

import com.measim.model.governance.FormulaProposal;

public interface GovernanceService {
    void initializeGovernments();
    void proposeFormulaChange(String agentId, String governmentId, String parameterName,
                               double proposedValue, String description, int currentTick);
    void castVote(String agentId, String proposalId, boolean inFavor);
    void processProposals(int currentTick);
    void fileDispute(String agentId, String governmentId, String type,
                      String description, int currentTick);
    void processDisputes(int currentTick);
    String getAgentGovernment(String agentId);
    void migrateAgent(String agentId, String targetGovernmentId);
}
