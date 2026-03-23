package com.measim.dao;

import com.measim.model.gamemaster.DiscoverySpec;
import com.measim.model.gamemaster.ResearchProposal;
import com.measim.model.gamemaster.TechNode;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class TechnologyRegistryDaoImpl implements TechnologyRegistryDao {

    private final Map<String, DiscoverySpec> discoveries = new LinkedHashMap<>();
    private final Map<String, TechNode> techNodes = new LinkedHashMap<>();
    private final List<ResearchProposal> pendingProposals = new ArrayList<>();

    @Override
    public void registerDiscovery(DiscoverySpec discovery) { discoveries.put(discovery.id(), discovery); }

    @Override
    public Optional<DiscoverySpec> getDiscovery(String id) { return Optional.ofNullable(discoveries.get(id)); }

    @Override
    public List<DiscoverySpec> getAllDiscoveries() { return List.copyOf(discoveries.values()); }

    @Override
    public int discoveryCount() { return discoveries.size(); }

    @Override
    public void addTechNode(TechNode node) { techNodes.put(node.id(), node); }

    @Override
    public Optional<TechNode> getTechNode(String id) { return Optional.ofNullable(techNodes.get(id)); }

    @Override
    public List<TechNode> getAllTechNodes() { return List.copyOf(techNodes.values()); }

    @Override
    public int techTreeDepth() {
        return techNodes.values().stream().mapToInt(TechNode::depth).max().orElse(0);
    }

    @Override
    public void submitProposal(ResearchProposal proposal) { pendingProposals.add(proposal); }

    @Override
    public List<ResearchProposal> getPendingProposals(int currentTick) {
        return pendingProposals.stream().filter(p -> !p.isReady(currentTick)).toList();
    }

    @Override
    public List<ResearchProposal> getReadyProposals(int currentTick) {
        return pendingProposals.stream().filter(p -> p.isReady(currentTick)).toList();
    }

    @Override
    public void removeProposal(ResearchProposal proposal) { pendingProposals.remove(proposal); }
}
