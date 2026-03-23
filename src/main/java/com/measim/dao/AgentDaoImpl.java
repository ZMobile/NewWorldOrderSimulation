package com.measim.dao;

import com.measim.model.agent.Agent;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class AgentDaoImpl implements AgentDao {

    private final List<Agent> agents = new ArrayList<>();
    private final Map<String, Agent> index = new HashMap<>();

    @Override
    public void addAgent(Agent agent) {
        agents.add(agent);
        index.put(agent.id(), agent);
    }

    @Override
    public Agent getAgent(String id) { return index.get(id); }

    @Override
    public List<Agent> getAllAgents() { return Collections.unmodifiableList(agents); }

    @Override
    public int getAgentCount() { return agents.size(); }
}
