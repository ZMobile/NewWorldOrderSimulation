package com.measim.service.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.measim.dao.AgentDao;
import com.measim.dao.MetricsDao;
import com.measim.dao.TechnologyRegistryDao;
import com.measim.model.agent.Agent;
import com.measim.model.economy.ItemType;
import com.measim.model.gamemaster.DiscoverySpec;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class SnapshotServiceImpl implements SnapshotService {

    private final ObjectMapper mapper;
    private final AgentDao agentDao;
    private final MetricsDao metricsDao;
    private final TechnologyRegistryDao techRegistryDao;

    @Inject
    public SnapshotServiceImpl(AgentDao agentDao, MetricsDao metricsDao,
                                TechnologyRegistryDao techRegistryDao) {
        this.agentDao = agentDao;
        this.metricsDao = metricsDao;
        this.techRegistryDao = techRegistryDao;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void saveSnapshot(int currentTick, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path snapshotFile = outputDir.resolve("snapshot_tick_" + currentTick + ".json");

        ObjectNode root = mapper.createObjectNode();
        root.put("tick", currentTick);
        root.put("timestamp", System.currentTimeMillis());

        // Serialize agents
        ArrayNode agentsNode = root.putArray("agents");
        for (Agent agent : agentDao.getAllAgents()) {
            ObjectNode agentNode = agentsNode.addObject();
            agentNode.put("id", agent.id());
            agentNode.put("name", agent.name());
            agentNode.put("archetype", agent.identity().archetype().name());
            agentNode.put("credits", agent.state().credits());
            agentNode.put("locationQ", agent.state().location().q());
            agentNode.put("locationR", agent.state().location().r());
            agentNode.put("satisfaction", agent.state().satisfaction());
            agentNode.put("ownedRobots", agent.state().ownedRobots());
            agentNode.put("totalRevenue", agent.state().totalRevenue());
            agentNode.put("totalEmissions", agent.state().totalEmissions());
            agentNode.put("commonsScore", agent.state().commonsScore());

            // Score vector
            ObjectNode scoreNode = agentNode.putObject("scoreVector");
            var sv = agent.state().scoreVector();
            scoreNode.put("ef", sv.environmentalFootprint());
            scoreNode.put("cc", sv.commonsContribution());
            scoreNode.put("ld", sv.laborDisplacement());
            scoreNode.put("rc", sv.resourceConcentration());
            scoreNode.put("ep", sv.economicProductivity());

            // Inventory
            ObjectNode invNode = agentNode.putObject("inventory");
            for (var entry : agent.state().inventory().entrySet()) {
                invNode.put(entry.getKey().id(), entry.getValue());
            }
        }

        // Serialize discoveries
        ArrayNode discoveriesNode = root.putArray("discoveries");
        for (DiscoverySpec discovery : techRegistryDao.getAllDiscoveries()) {
            ObjectNode dNode = discoveriesNode.addObject();
            dNode.put("id", discovery.id());
            dNode.put("name", discovery.name());
            dNode.put("category", discovery.category().level());
            dNode.put("discoverer", discovery.discovererId());
            dNode.put("discoveryTick", discovery.discoveryTick());
        }

        // Serialize latest metrics
        MetricsDao.TickMetrics latest = metricsDao.getLatest();
        if (latest != null) {
            ObjectNode metricsNode = root.putObject("latestMetrics");
            metricsNode.put("gini", latest.giniCoefficient());
            metricsNode.put("satisfactionMean", latest.satisfactionMean());
            metricsNode.put("environmentalHealth", latest.environmentalHealth());
            metricsNode.put("ubiPool", latest.ubiPoolSize());
            metricsNode.put("totalRobots", latest.totalRobots());
            metricsNode.put("avgCredits", latest.averageCredits());
        }

        mapper.writeValue(snapshotFile.toFile(), root);
    }

    @Override
    public void loadSnapshot(Path snapshotFile) throws IOException {
        // TODO: Full state restoration from snapshot
        // This requires reconstructing agents, world state, etc. from JSON
        // For now, log that the feature is available
        if (!Files.exists(snapshotFile)) {
            throw new IOException("Snapshot file not found: " + snapshotFile);
        }
        System.out.println("Snapshot loading from " + snapshotFile + " - full restoration not yet implemented.");
    }
}
