package com.measim.service.governance;

import com.measim.dao.AgentDao;
import com.measim.dao.GovernmentDao;
import com.measim.model.agent.Agent;
import com.measim.model.config.SimulationConfig;
import com.measim.model.governance.DisputeCase;
import com.measim.model.governance.FormulaProposal;
import com.measim.model.governance.Government;
import com.measim.model.world.HexCoord;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class GovernanceServiceImpl implements GovernanceService {

    private static final double APPROVAL_THRESHOLD = 0.5;
    private static final int COMMENT_PERIOD_TICKS = 3;
    private static final int VOTING_PERIOD_TICKS = 3;
    private static final int IMPLEMENTATION_DELAY_TICKS = 2;

    private final GovernmentDao governmentDao;
    private final AgentDao agentDao;
    private final SimulationConfig config;

    @Inject
    public GovernanceServiceImpl(GovernmentDao governmentDao, AgentDao agentDao,
                                  SimulationConfig config) {
        this.governmentDao = governmentDao;
        this.agentDao = agentDao;
        this.config = config;
    }

    @Override
    public void initializeGovernments() {
        for (var def : config.governments()) {
            governmentDao.addGovernment(new Government(
                    def.name().toLowerCase().replace(' ', '_'),
                    def.name(),
                    new HexCoord(def.q1(), def.r1()),
                    new HexCoord(def.q2(), def.r2()),
                    def.efWeight(), def.ubiMultiplier(), def.domainTwoStrictness()));
        }

        // Default government if none configured
        if (governmentDao.getAllGovernments().isEmpty()) {
            governmentDao.addGovernment(new Government(
                    "default", "Default Government",
                    new HexCoord(0, 0),
                    new HexCoord(config.worldWidth() - 1, config.worldHeight() - 1),
                    1.0, 1.0, 1.0));
        }
    }

    @Override
    public void proposeFormulaChange(String agentId, String governmentId, String parameterName,
                                      double proposedValue, String description, int currentTick) {
        String id = "proposal_" + UUID.randomUUID().toString().substring(0, 8);
        governmentDao.submitProposal(new FormulaProposal(
                id, governmentId, agentId, description, parameterName, proposedValue, currentTick));
    }

    @Override
    public void castVote(String agentId, String proposalId, boolean inFavor) {
        governmentDao.getProposal(proposalId).ifPresent(p -> p.castVote(agentId, inFavor));
    }

    @Override
    public void processProposals(int currentTick) {
        for (Government gov : governmentDao.getAllGovernments()) {
            for (FormulaProposal proposal : governmentDao.getActiveProposals(gov.id())) {
                int age = currentTick - proposal.tickProposed();

                switch (proposal.status()) {
                    case PENDING -> {
                        proposal.setStatus(FormulaProposal.Status.COMMENT_PERIOD);
                    }
                    case COMMENT_PERIOD -> {
                        if (age >= COMMENT_PERIOD_TICKS) {
                            proposal.setStatus(FormulaProposal.Status.VOTING);
                        }
                    }
                    case VOTING -> {
                        if (age >= COMMENT_PERIOD_TICKS + VOTING_PERIOD_TICKS) {
                            if (proposal.approvalRate() >= APPROVAL_THRESHOLD) {
                                proposal.setStatus(FormulaProposal.Status.APPROVED);
                                proposal.setImplementationTick(currentTick + IMPLEMENTATION_DELAY_TICKS);
                            } else {
                                proposal.setStatus(FormulaProposal.Status.REJECTED);
                            }
                        }
                    }
                    case APPROVED -> {
                        if (currentTick >= proposal.implementationTick()) {
                            applyProposal(gov, proposal);
                            proposal.setStatus(FormulaProposal.Status.IMPLEMENTED);
                        }
                    }
                    default -> {}
                }
            }
        }
    }

    @Override
    public void fileDispute(String agentId, String governmentId, String type,
                             String description, int currentTick) {
        DisputeCase.DisputeType disputeType = switch (type.toUpperCase()) {
            case "MEASUREMENT" -> DisputeCase.DisputeType.MEASUREMENT;
            case "FORMULA" -> DisputeCase.DisputeType.FORMULA;
            default -> DisputeCase.DisputeType.WRONGFUL_SCORING;
        };
        governmentDao.fileDispute(new DisputeCase(
                "dispute_" + UUID.randomUUID().toString().substring(0, 8),
                agentId, governmentId, disputeType, description, currentTick,
                DisputeCase.DisputeStatus.FILED, null));
    }

    @Override
    public void processDisputes(int currentTick) {
        for (Government gov : governmentDao.getAllGovernments()) {
            for (DisputeCase dispute : governmentDao.getOpenDisputes(gov.id())) {
                int age = currentTick - dispute.tickFiled();
                if (dispute.status() == DisputeCase.DisputeStatus.FILED) {
                    governmentDao.updateDispute(
                            dispute.withStatus(DisputeCase.DisputeStatus.UNDER_REVIEW, null));
                } else if (dispute.status() == DisputeCase.DisputeStatus.UNDER_REVIEW && age >= 6) {
                    // Auto-resolve after review period (placeholder for LLM-driven adjudication)
                    governmentDao.updateDispute(
                            dispute.withStatus(DisputeCase.DisputeStatus.RESOLVED,
                                    "Reviewed and resolved after " + age + " ticks."));
                }
            }
        }
    }

    @Override
    public String getAgentGovernment(String agentId) {
        Agent agent = agentDao.getAgent(agentId);
        if (agent == null) return "default";
        return governmentDao.getGovernmentForTile(agent.state().location())
                .map(Government::id).orElse("default");
    }

    @Override
    public void migrateAgent(String agentId, String targetGovernmentId) {
        // Migration is handled by moving the agent to a tile in the target government's region
        Agent agent = agentDao.getAgent(agentId);
        Government target = governmentDao.getGovernment(targetGovernmentId).orElse(null);
        if (agent == null || target == null) return;

        // Move to center of target region
        int centerQ = (target.regionMin().q() + target.regionMax().q()) / 2;
        int centerR = (target.regionMin().r() + target.regionMax().r()) / 2;
        agent.state().setLocation(new HexCoord(centerQ, centerR));
    }

    private void applyProposal(Government gov, FormulaProposal proposal) {
        switch (proposal.parameterName()) {
            case "efWeight" -> gov.setEfWeight(proposal.proposedValue());
            case "ubiMultiplier" -> gov.setUbiMultiplier(proposal.proposedValue());
            case "domainTwoStrictness" -> gov.setDomainTwoStrictness(proposal.proposedValue());
        }
    }
}
