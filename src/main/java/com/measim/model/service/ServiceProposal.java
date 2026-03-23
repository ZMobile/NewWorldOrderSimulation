package com.measim.model.service;

import com.measim.model.world.HexCoord;

/**
 * An agent's proposal to create a new service.
 * Like InfrastructureProposal: agent has creative agency, GM evaluates feasibility.
 */
public record ServiceProposal(
        String agentId,
        String proposedName,
        String proposedDescription,
        String intendedCategory,
        String targetCustomers,
        String proposedPricing,
        String requiredResources,
        HexCoord location,
        double creditBudget
) {}
