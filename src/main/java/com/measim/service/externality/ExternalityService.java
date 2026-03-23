package com.measim.service.externality;

import com.measim.model.externality.ExternalityProfile;

import java.util.List;

public interface ExternalityService {
    /**
     * Process all externality profiles: compute true outputs, apply to world,
     * update what's measurable for EF scoring.
     */
    void processExternalities(int currentTick);

    /**
     * Get the MEASURED pollution for an entity (feeds into EF scoring).
     * May be less than true pollution if byproducts are hidden/cumulative.
     */
    double getMeasuredPollution(String entityId, int currentTick);

    /**
     * Register an externality profile for an entity.
     */
    void registerProfile(ExternalityProfile profile);

    /**
     * Get total measured pollution for all entities owned by an agent.
     */
    double getAgentMeasuredPollution(String agentId, int currentTick);
}
