package com.measim.dao;

import com.measim.model.governance.DisputeCase;
import com.measim.model.governance.FormulaProposal;
import com.measim.model.governance.Government;
import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Optional;

public interface GovernmentDao {
    void addGovernment(Government government);
    Optional<Government> getGovernment(String id);
    List<Government> getAllGovernments();
    Optional<Government> getGovernmentForTile(HexCoord coord);

    void submitProposal(FormulaProposal proposal);
    List<FormulaProposal> getActiveProposals(String governmentId);
    Optional<FormulaProposal> getProposal(String id);

    void fileDispute(DisputeCase dispute);
    List<DisputeCase> getOpenDisputes(String governmentId);
    void updateDispute(DisputeCase updated);
}
