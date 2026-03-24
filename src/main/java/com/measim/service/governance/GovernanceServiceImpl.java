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

/**
 * Governance in MeaSim is minimal by design.
 *
 * The 5-layer architecture:
 *   Layer 1: MEAS Protocol (automatic) — scoring, modifiers, UBI
 *   Layer 2: Contracts (binding, automatic) — wages garnished, trades atomic
 *   Layer 3: Property (first-come-first-served) — registered via GM, simple ledger
 *   Layer 4: Governance GM (periodic) — yearly MEAS audit, reserve management
 *   Layer 5: Emergent (agent-built) — courts, police, regulations, coalitions
 *
 * This service handles only the minimal institutional functions.
 * There is NO hardcoded voting system, NO fixed government regions,
 * NO automatic legislation. Agents create governance through their actions.
 */
@Singleton
public class GovernanceServiceImpl implements GovernanceService {

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
        // Single default jurisdiction covering the whole map.
        // MEAS parameters are protocol-level — not government-specific.
        if (governmentDao.getAllGovernments().isEmpty()) {
            governmentDao.addGovernment(new Government(
                    "default", "MEAS Protocol Zone",
                    new HexCoord(0, 0),
                    new HexCoord(config.worldWidth() - 1, config.worldHeight() - 1),
                    1.0, 1.0, 1.0));
        }
    }

    @Override
    public void proposeFormulaChange(String agentId, String governmentId, String parameterName,
                                      double proposedValue, String description, int currentTick) {
        // Agents can propose MEAS parameter changes.
        // These are recorded but require agent-built governance to process.
        // The Governance GM evaluates during yearly audits whether any proposals have merit.
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
        // Proposals are recorded but not auto-processed.
        // Agent-created governance institutions decide how to handle them.
        // The Governance GM reviews proposals during yearly audits.
    }

    @Override
    public void fileDispute(String agentId, String governmentId, String type,
                             String description, int currentTick) {
        // Disputes are filed and recorded in the communication log.
        // Resolution depends on agent-built institutions:
        //   - If an arbitration service exists, the dispute goes there
        //   - If not, it's just a public record that affects reputation
        DisputeCase.DisputeType disputeType = switch (type.toUpperCase()) {
            case "MEASUREMENT" -> DisputeCase.DisputeType.MEASUREMENT;
            case "FORMULA" -> DisputeCase.DisputeType.FORMULA;
            case "CONTRACT" -> DisputeCase.DisputeType.WRONGFUL_SCORING;
            default -> DisputeCase.DisputeType.WRONGFUL_SCORING;
        };
        governmentDao.fileDispute(new DisputeCase(
                "dispute_" + UUID.randomUUID().toString().substring(0, 8),
                agentId, governmentId, disputeType, description, currentTick,
                DisputeCase.DisputeStatus.FILED, null));
    }

    @Override
    public void processDisputes(int currentTick) {
        // Disputes are not auto-resolved. They remain as public records.
        // Agent-created arbitration services handle resolution.
    }

    @Override
    public String getAgentGovernment(String agentId) {
        return "default"; // single global MEAS zone
    }

    @Override
    public void migrateAgent(String agentId, String targetGovernmentId) {
        // Migration is just moving — agents move freely.
        // No government boundaries to cross in a single MEAS zone.
    }
}
