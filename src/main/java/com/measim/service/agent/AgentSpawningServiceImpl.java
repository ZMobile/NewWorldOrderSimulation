package com.measim.service.agent;

import com.measim.dao.AgentDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.*;
import com.measim.model.config.SimulationConfig;
import com.measim.model.event.EventBus;
import com.measim.model.world.Tile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class AgentSpawningServiceImpl implements AgentSpawningService {

    private final AgentDao agentDao;
    private final WorldDao worldDao;
    private final SimulationConfig config;
    private final EventBus eventBus;

    @Inject
    public AgentSpawningServiceImpl(AgentDao agentDao, WorldDao worldDao,
                                     SimulationConfig config, EventBus eventBus) {
        this.agentDao = agentDao;
        this.worldDao = worldDao;
        this.config = config;
        this.eventBus = eventBus;
    }

    @Override
    public void spawnAgents() {
        Random rng = new Random(config.seed() + 100);
        List<Tile> settlements = worldDao.getSettlementZones();
        List<Tile> spawnLocations = settlements.isEmpty()
                ? worldDao.getAllTiles().stream().filter(t -> t.terrain().isPassable()).toList()
                : settlements;

        int agentNum = 0;
        for (var entry : config.archetypeDistribution().entrySet()) {
            Archetype archetype = Archetype.valueOf(entry.getKey());
            int count = (int) Math.round(config.agentCount() * entry.getValue());
            for (int i = 0; i < count && agentNum < config.agentCount(); i++) {
                Tile spawnTile = spawnLocations.get(rng.nextInt(spawnLocations.size()));
                String id = "agent_" + agentNum;
                String name = archetype.name().toLowerCase() + "_" + agentNum;
                IdentityProfile profile = IdentityProfile.fromArchetype(id, name, archetype, Set.of("general"), rng);
                AgentState state = new AgentState(spawnTile.coord(), config.startingCredits());
                Agent agent = new Agent(profile, state, config.memoryCapacity());
                agentDao.addAgent(agent);
                eventBus.publish(new EventBus.AgentCreated(id));
                agentNum++;
            }
        }
    }
}
