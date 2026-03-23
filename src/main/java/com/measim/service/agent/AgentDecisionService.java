package com.measim.service.agent;

import com.measim.model.agent.Agent;
import com.measim.model.agent.AgentAction;
import com.measim.model.economy.ItemType;

import java.util.List;
import java.util.Map;

public interface AgentDecisionService {
    /**
     * Returns the agent's strategic action for this tick.
     * Routine economic activity (extract, produce, sell) is handled separately
     * in the ActionExecutionPhase pipeline.
     */
    AgentAction decideStrategicAction(Agent agent, Map<ItemType, Double> marketPrices, int currentTick);
}
