package com.measim.service.trade;

import com.measim.model.agent.Agent;

public interface CommunicationRangeService {
    /**
     * Get the effective communication range for an agent, based on:
     * - Base range (1-2 tiles, shouting distance)
     * - Infrastructure at/near agent's location (message boards, postal, telecom)
     * - Technology available
     *
     * The GM sets communication range as an infrastructure effect property.
     * This service reads those effects and computes the max range for each agent.
     */
    int getEffectiveRange(Agent agent);

    /** Check if two agents can communicate (either within range of each other). */
    boolean canCommunicate(Agent a, Agent b);

    /** Default range with no infrastructure. */
    int BASE_RANGE = 2;
}
