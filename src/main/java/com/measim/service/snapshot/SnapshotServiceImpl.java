package com.measim.service.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.measim.dao.*;
import com.measim.model.agent.Agent;
import com.measim.model.communication.Message;
import com.measim.model.contract.Contract;
import com.measim.model.gamemaster.DiscoverySpec;
import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.property.TileClaim;
import com.measim.model.risk.RiskEvent;
import com.measim.model.service.ServiceInstance;
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
    private final InfrastructureDao infraDao;
    private final ServiceDao serviceDao;
    private final ContractDao contractDao;
    private final PropertyDao propertyDao;
    private final LlmDao llmDao;

    @Inject
    public SnapshotServiceImpl(AgentDao agentDao, MetricsDao metricsDao,
                                TechnologyRegistryDao techRegistryDao,
                                CommunicationDao communicationDao, RiskDao riskDao,
                                InfrastructureDao infraDao, ServiceDao serviceDao,
                                ContractDao contractDao, PropertyDao propertyDao,
                                LlmDao llmDao) {
        this.agentDao = agentDao;
        this.metricsDao = metricsDao;
        this.techRegistryDao = techRegistryDao;
        this.communicationDao = communicationDao;
        this.riskDao = riskDao;
        this.infraDao = infraDao;
        this.serviceDao = serviceDao;
        this.contractDao = contractDao;
        this.propertyDao = propertyDao;
        this.llmDao = llmDao;
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

        // LLM cost tracking
        ObjectNode llmNode = root.putObject("llmUsage");
        llmNode.put("totalCalls", llmDao.totalCalls());
        llmNode.put("totalSpentUsd", llmDao.totalSpent());
        llmNode.put("budgetRemainingUsd", llmDao.budgetRemaining());

        // Agents
        ArrayNode agentsNode = root.putArray("agents");
        for (Agent agent : agentDao.getAllAgents()) {
            ObjectNode a = agentsNode.addObject();
            a.put("id", agent.id());
            a.put("name", agent.name());
            a.put("archetype", agent.identity().archetype().name());
            a.put("credits", agent.state().credits());
            a.put("locationQ", agent.state().location().q());
            a.put("locationR", agent.state().location().r());
            a.put("satisfaction", agent.state().satisfaction());
            a.put("employmentStatus", agent.state().employmentStatus().name());
            a.put("ownedRobots", agent.state().ownedRobots());
            a.put("totalRevenue", agent.state().totalRevenue());
            a.put("totalEmissions", agent.state().totalEmissions());
            a.put("commonsScore", agent.state().commonsScore());
            a.put("humanEmployees", agent.state().humanEmployees());

            ObjectNode sv = a.putObject("scoreVector");
            var score = agent.state().scoreVector();
            sv.put("ef", score.environmentalFootprint());
            sv.put("cc", score.commonsContribution());
            sv.put("ld", score.laborDisplacement());
            sv.put("rc", score.resourceConcentration());
            sv.put("ep", score.economicProductivity());

            ObjectNode mods = a.putObject("modifiers");
            var m = agent.state().modifiers();
            mods.put("ef", m.environmentalFootprint());
            mods.put("cc", m.commonsContribution());
            mods.put("rc", m.resourceConcentration());
            mods.put("ldRate", m.laborDisplacementRate());
            mods.put("combined", m.combinedMultiplier());

            ObjectNode exp = a.putObject("experience");
            for (var entry : agent.state().allExperience().entrySet()) {
                exp.put(entry.getKey(), entry.getValue()
                        + " ticks (" + agent.state().getSuccessCount(entry.getKey()) + " successes)");
            }

            ObjectNode inv = a.putObject("inventory");
            for (var entry : agent.state().inventory().entrySet()) {
                inv.put(entry.getKey().id(), entry.getValue());
            }
        }

        // Infrastructure
        ArrayNode infraNode = root.putArray("infrastructure");
        for (Infrastructure infra : infraDao.getAllActive()) {
            ObjectNode i = infraNode.addObject();
            i.put("id", infra.id());
            i.put("type", infra.type().name());
            i.put("owner", infra.ownerId());
            i.put("locationQ", infra.location().q());
            i.put("locationR", infra.location().r());
            i.put("condition", infra.condition());
            i.put("builtTick", infra.builtTick());
            if (infra.connectedTo() != null) {
                i.put("connectedToQ", infra.connectedTo().q());
                i.put("connectedToR", infra.connectedTo().r());
            }
        }

        // Services
        ArrayNode servicesNode = root.putArray("services");
        for (ServiceInstance svc : serviceDao.getActiveInstances()) {
            ObjectNode s = servicesNode.addObject();
            s.put("id", svc.id());
            s.put("name", svc.type().name());
            s.put("category", svc.type().category().name());
            s.put("owner", svc.ownerId());
            s.put("reputation", svc.reputation());
            s.put("totalUses", svc.totalUses());
            s.put("revenue", svc.accumulatedRevenue());
            s.put("profit", svc.profit());
        }

        // Contracts
        ArrayNode contractsNode = root.putArray("activeContracts");
        for (Contract contract : contractDao.getActiveContracts()) {
            ObjectNode c = contractsNode.addObject();
            c.put("id", contract.id());
            c.put("type", contract.type().name());
            c.put("partyA", contract.partyAId());
            c.put("partyB", contract.partyBId());
            c.put("paymentPerTick", contract.paymentPerTick());
            c.put("laborWeight", contract.laborWeight());
            c.put("startTick", contract.startTick());
            c.put("status", contract.status().name());
        }

        // Property claims (summary: count per agent)
        ObjectNode propertyNode = root.putObject("propertySummary");
        var allAgentProperties = new java.util.HashMap<String, Integer>();
        for (Agent agent : agentDao.getAllAgents()) {
            var claims = propertyDao.getClaimsByOwner(agent.id());
            if (!claims.isEmpty()) allAgentProperties.put(agent.id(), claims.size());
        }
        propertyNode.put("totalClaims", allAgentProperties.values().stream().mapToInt(Integer::intValue).sum());
        propertyNode.put("agentsWithProperty", allAgentProperties.size());
        if (!allAgentProperties.isEmpty()) {
            var topLandlord = allAgentProperties.entrySet().stream()
                    .max(java.util.Comparator.comparingInt(java.util.Map.Entry::getValue));
            topLandlord.ifPresent(e -> {
                propertyNode.put("topLandlord", e.getKey());
                propertyNode.put("topLandlordClaims", e.getValue());
            });
        }

        // Discoveries
        ArrayNode discoveriesNode = root.putArray("discoveries");
        for (DiscoverySpec discovery : techRegistryDao.getAllDiscoveries()) {
            ObjectNode d = discoveriesNode.addObject();
            d.put("id", discovery.id());
            d.put("name", discovery.name());
            d.put("category", discovery.category().level());
            d.put("discoverer", discovery.discovererId());
            d.put("discoveryTick", discovery.discoveryTick());
        }

        // Risk events (last 12 ticks)
        ArrayNode riskEventsNode = root.putArray("recentRiskEvents");
        for (RiskEvent event : riskDao.getRecentEvents(12, currentTick)) {
            ObjectNode e = riskEventsNode.addObject();
            e.put("tick", event.tick());
            e.put("riskName", event.riskName());
            e.put("entityId", event.entityId());
            e.put("entityType", event.entityType().name());
            e.put("severity", event.severity());
            e.put("narrative", event.gmNarrative());
        }

        // Communication (last 100 messages)
        ArrayNode commsNode = root.putArray("recentCommunication");
        var allMessages = communicationDao.getAllMessages();
        int start = Math.max(0, allMessages.size() - 100);
        for (int i = start; i < allMessages.size(); i++) {
            Message msg = allMessages.get(i);
            ObjectNode m = commsNode.addObject();
            m.put("tick", msg.tick());
            m.put("channel", msg.channel().name());
            m.put("type", msg.type().name());
            m.put("sender", msg.senderId());
            m.put("receiver", msg.receiverId());
            m.put("content", msg.content());
        }

        // Metrics
        MetricsDao.TickMetrics latest = metricsDao.getLatest();
        if (latest != null) {
            ObjectNode metrics = root.putObject("latestMetrics");
            metrics.put("gini", latest.giniCoefficient());
            metrics.put("satisfactionMean", latest.satisfactionMean());
            metrics.put("environmentalHealth", latest.environmentalHealth());
            metrics.put("ubiPool", latest.ubiPoolSize());
            metrics.put("totalRobots", latest.totalRobots());
            metrics.put("avgCredits", latest.averageCredits());
            metrics.put("totalTransactions", latest.totalTransactions());
        }

        // Summary stats
        ObjectNode summary = root.putObject("summary");
        summary.put("totalAgents", agentDao.getAgentCount());
        summary.put("activeInfrastructure", infraDao.getAllActive().size());
        summary.put("activeServices", serviceDao.getActiveInstances().size());
        summary.put("activeContracts", contractDao.getActiveContracts().size());
        summary.put("totalDiscoveries", techRegistryDao.getAllDiscoveries().size());
        summary.put("totalRiskEvents", riskDao.getAllEvents().size());
        summary.put("totalMessages", communicationDao.getAllMessages().size());
        long employed = agentDao.getAllAgents().stream()
                .filter(a -> a.state().employmentStatus() == com.measim.model.agent.EmploymentStatus.EMPLOYED).count();
        summary.put("employedAgents", employed);
        summary.put("unemployedAgents", agentDao.getAgentCount() - employed);

        mapper.writeValue(snapshotFile.toFile(), root);
    }

    @Override
    public void exportFullCommunicationLog(Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        ArrayNode logNode = mapper.createArrayNode();
        for (Message msg : communicationDao.getAllMessages()) {
            ObjectNode m = logNode.addObject();
            m.put("tick", msg.tick());
            m.put("channel", msg.channel().name());
            m.put("type", msg.type().name());
            m.put("sender", msg.senderId());
            m.put("receiver", msg.receiverId());
            m.put("content", msg.content());
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
