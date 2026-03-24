package com.measim.service.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.measim.dao.*;
import com.measim.model.agent.Agent;
import com.measim.model.communication.Message;
import com.measim.model.gamemaster.DiscoverySpec;
import com.measim.model.risk.RiskEvent;
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
    private final CommunicationDao communicationDao;
    private final RiskDao riskDao;

    @Inject
    public SnapshotServiceImpl(AgentDao agentDao, MetricsDao metricsDao,
                                TechnologyRegistryDao techRegistryDao,
                                CommunicationDao communicationDao, RiskDao riskDao) {
        this.agentDao = agentDao;
        this.metricsDao = metricsDao;
        this.techRegistryDao = techRegistryDao;
        this.communicationDao = communicationDao;
        this.riskDao = riskDao;
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

        // Agents
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
            agentNode.put("employmentStatus", agent.state().employmentStatus().name());
            agentNode.put("ownedRobots", agent.state().ownedRobots());
            agentNode.put("totalRevenue", agent.state().totalRevenue());
            agentNode.put("totalEmissions", agent.state().totalEmissions());
            agentNode.put("commonsScore", agent.state().commonsScore());
            agentNode.put("humanEmployees", agent.state().humanEmployees());

            // Score vector
            ObjectNode scoreNode = agentNode.putObject("scoreVector");
            var sv = agent.state().scoreVector();
            scoreNode.put("ef", sv.environmentalFootprint());
            scoreNode.put("cc", sv.commonsContribution());
            scoreNode.put("ld", sv.laborDisplacement());
            scoreNode.put("rc", sv.resourceConcentration());
            scoreNode.put("ep", sv.economicProductivity());

            // Modifiers
            ObjectNode modNode = agentNode.putObject("modifiers");
            var m = agent.state().modifiers();
            modNode.put("ef", m.environmentalFootprint());
            modNode.put("cc", m.commonsContribution());
            modNode.put("rc", m.resourceConcentration());
            modNode.put("ldRate", m.laborDisplacementRate());
            modNode.put("combined", m.combinedMultiplier());

            // Experience
            ObjectNode expNode = agentNode.putObject("experience");
            for (var entry : agent.state().allExperience().entrySet()) {
                expNode.put(entry.getKey(), entry.getValue()
                        + " ticks (" + agent.state().getSuccessCount(entry.getKey()) + " successes)");
            }

            // Inventory
            ObjectNode invNode = agentNode.putObject("inventory");
            for (var entry : agent.state().inventory().entrySet()) {
                invNode.put(entry.getKey().id(), entry.getValue());
            }
        }

        // Discoveries
        ArrayNode discoveriesNode = root.putArray("discoveries");
        for (DiscoverySpec discovery : techRegistryDao.getAllDiscoveries()) {
            ObjectNode dNode = discoveriesNode.addObject();
            dNode.put("id", discovery.id());
            dNode.put("name", discovery.name());
            dNode.put("category", discovery.category().level());
            dNode.put("discoverer", discovery.discovererId());
            dNode.put("discoveryTick", discovery.discoveryTick());
        }

        // Risk events (last 12 ticks)
        ArrayNode riskEventsNode = root.putArray("recentRiskEvents");
        for (RiskEvent event : riskDao.getRecentEvents(12, currentTick)) {
            ObjectNode eNode = riskEventsNode.addObject();
            eNode.put("tick", event.tick());
            eNode.put("riskName", event.riskName());
            eNode.put("entityId", event.entityId());
            eNode.put("entityType", event.entityType().name());
            eNode.put("severity", event.severity());
            eNode.put("narrative", event.gmNarrative());
        }

        // Communication log (last 50 messages)
        ArrayNode commsNode = root.putArray("recentCommunication");
        var allMessages = communicationDao.getAllMessages();
        int start = Math.max(0, allMessages.size() - 50);
        for (int i = start; i < allMessages.size(); i++) {
            Message msg = allMessages.get(i);
            ObjectNode msgNode = commsNode.addObject();
            msgNode.put("tick", msg.tick());
            msgNode.put("channel", msg.channel().name());
            msgNode.put("type", msg.type().name());
            msgNode.put("sender", msg.senderId());
            msgNode.put("receiver", msg.receiverId());
            msgNode.put("content", msg.content());
        }

        // Metrics
        MetricsDao.TickMetrics latest = metricsDao.getLatest();
        if (latest != null) {
            ObjectNode metricsNode = root.putObject("latestMetrics");
            metricsNode.put("gini", latest.giniCoefficient());
            metricsNode.put("satisfactionMean", latest.satisfactionMean());
            metricsNode.put("environmentalHealth", latest.environmentalHealth());
            metricsNode.put("ubiPool", latest.ubiPoolSize());
            metricsNode.put("totalRobots", latest.totalRobots());
            metricsNode.put("avgCredits", latest.averageCredits());
            metricsNode.put("totalTransactions", latest.totalTransactions());
        }

        mapper.writeValue(snapshotFile.toFile(), root);
    }

    @Override
    public void exportFullCommunicationLog(Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        ArrayNode logNode = mapper.createArrayNode();
        for (Message msg : communicationDao.getAllMessages()) {
            ObjectNode msgNode = logNode.addObject();
            msgNode.put("tick", msg.tick());
            msgNode.put("channel", msg.channel().name());
            msgNode.put("type", msg.type().name());
            msgNode.put("sender", msg.senderId());
            msgNode.put("receiver", msg.receiverId());
            msgNode.put("content", msg.content());
        }
        mapper.writeValue(outputPath.toFile(), logNode);
    }

    @Override
    public void loadSnapshot(Path snapshotFile) throws IOException {
        if (!Files.exists(snapshotFile)) {
            throw new IOException("Snapshot file not found: " + snapshotFile);
        }
        System.out.println("Snapshot loading from " + snapshotFile + " — full restoration not yet implemented.");
    }
}
