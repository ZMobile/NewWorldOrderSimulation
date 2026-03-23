package com.measim.dao;

import com.measim.model.agent.Agent;

import java.util.List;

public interface AgentDao {
    void addAgent(Agent agent);
    Agent getAgent(String id);
    List<Agent> getAllAgents();
    int getAgentCount();
}
