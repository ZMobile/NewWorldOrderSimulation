package com.measim.model.gamemaster;

import com.measim.model.world.HexCoord;

/**
 * An agent's proposal for infrastructure. The agent has creative agency —
 * they describe WHAT they want to build and HOW. The Game Master then evaluates
 * feasibility and sets the numerical properties (costs, capacity, risks, etc).
 *
 * The GM is the physics engine, not the architect.
 */
public record InfrastructureProposal(
        String agentId,
        String proposedName,
        String proposedDescription,
        String proposedMaterials,
        HexCoord location,
        HexCoord connectTo,
        String intendedPurpose,
        double creditBudget
) {}
