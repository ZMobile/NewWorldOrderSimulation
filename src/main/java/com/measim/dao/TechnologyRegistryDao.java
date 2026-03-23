package com.measim.dao;

import com.measim.model.gamemaster.DiscoverySpec;
import com.measim.model.gamemaster.ResearchProposal;
import com.measim.model.gamemaster.TechNode;

import java.util.List;
import java.util.Optional;

public interface TechnologyRegistryDao {
    void registerDiscovery(DiscoverySpec discovery);
    Optional<DiscoverySpec> getDiscovery(String id);
    List<DiscoverySpec> getAllDiscoveries();
    int discoveryCount();

    void addTechNode(TechNode node);
    Optional<TechNode> getTechNode(String id);
    List<TechNode> getAllTechNodes();
    int techTreeDepth();

    void submitProposal(ResearchProposal proposal);
    List<ResearchProposal> getPendingProposals(int currentTick);
    List<ResearchProposal> getReadyProposals(int currentTick);
    void removeProposal(ResearchProposal proposal);
}
