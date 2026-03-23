package com.measim.dao;

import com.measim.model.governance.DisputeCase;
import com.measim.model.governance.FormulaProposal;
import com.measim.model.governance.Government;
import com.measim.model.world.HexCoord;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class GovernmentDaoImpl implements GovernmentDao {

    private final Map<String, Government> governments = new LinkedHashMap<>();
    private final Map<String, FormulaProposal> proposals = new LinkedHashMap<>();
    private final List<DisputeCase> disputes = new ArrayList<>();

    @Override
    public void addGovernment(Government government) { governments.put(government.id(), government); }

    @Override
    public Optional<Government> getGovernment(String id) { return Optional.ofNullable(governments.get(id)); }

    @Override
    public List<Government> getAllGovernments() { return List.copyOf(governments.values()); }

    @Override
    public Optional<Government> getGovernmentForTile(HexCoord coord) {
        return governments.values().stream().filter(g -> g.containsTile(coord)).findFirst();
    }

    @Override
    public void submitProposal(FormulaProposal proposal) { proposals.put(proposal.id(), proposal); }

    @Override
    public List<FormulaProposal> getActiveProposals(String governmentId) {
        return proposals.values().stream()
                .filter(p -> p.governmentId().equals(governmentId))
                .filter(p -> p.status() != FormulaProposal.Status.IMPLEMENTED
                        && p.status() != FormulaProposal.Status.REJECTED)
                .toList();
    }

    @Override
    public Optional<FormulaProposal> getProposal(String id) { return Optional.ofNullable(proposals.get(id)); }

    @Override
    public void fileDispute(DisputeCase dispute) { disputes.add(dispute); }

    @Override
    public List<DisputeCase> getOpenDisputes(String governmentId) {
        return disputes.stream()
                .filter(d -> d.governmentId().equals(governmentId))
                .filter(d -> d.status() != DisputeCase.DisputeStatus.RESOLVED
                        && d.status() != DisputeCase.DisputeStatus.DISMISSED)
                .toList();
    }

    @Override
    public void updateDispute(DisputeCase updated) {
        disputes.removeIf(d -> d.id().equals(updated.id()));
        disputes.add(updated);
    }
}
