package com.measim.service.gamemaster;

import com.measim.model.gamemaster.*;
import com.measim.model.infrastructure.InfrastructureType;

import java.util.List;
import java.util.Optional;

/**
 * The Game Master is the simulation's "Dungeon Master" — the physics engine and referee.
 *
 * CRITICAL DESIGN PRINCIPLE:
 *   Agents have creative agency. They propose solutions.
 *   The GM evaluates feasibility and sets numerical properties.
 *   The GM NEVER invents solutions for agents. It only determines whether
 *   an agent's idea works and what the real-world costs/properties would be.
 *
 * All GM reasoning is observable via the communication log.
 * All GM interactions can be multi-turn conversations with clarification rounds.
 */
public interface GameMasterService {

    // --- Research ---
    void submitResearch(String agentId, String direction, double creditInvestment, int currentTick);
    List<DiscoverySpec> processReadyProposals(int currentTick);
    Optional<DiscoverySpec> adjudicateProposal(ResearchProposal proposal);

    // --- Infrastructure (agent proposes, GM evaluates) ---
    /**
     * Agent proposes a specific infrastructure solution.
     * GM evaluates feasibility and returns an InfrastructureType with properties set,
     * or empty if infeasible. This can trigger a multi-turn conversation.
     */
    Optional<InfrastructureType> evaluateInfrastructureProposal(InfrastructureProposal proposal, int currentTick);

    // --- Novel Agent Actions ---
    void submitNovelAction(NovelAction action);
    List<WorldEvent> adjudicateNovelActions(int currentTick);

    // --- Spontaneous World Events ---
    List<WorldEvent> generateWorldEvents(int currentTick, WorldState worldState);

    // --- World Coherence ---
    List<WorldEvent> auditWorldCoherence(int currentTick, WorldState worldState);

    // --- Initialization ---
    void initializeBaseTechTree();
    int techTreeDepth();
    int discoveryCount();

    record WorldState(
            double averageEnvironmentalHealth,
            double giniCoefficient,
            double averageSatisfaction,
            double averageCredits,
            int totalAgents,
            int totalRobots,
            int totalDiscoveries,
            int currentTick,
            int ticksPerYear,
            long crisisTileCount,
            double ubiPoolSize,
            List<String> recentEvents
    ) {}
}
