package com.measim.service.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.measim.dao.*;
import com.measim.model.agent.Agent;
import com.measim.model.communication.Message;
import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.risk.*;
import com.measim.model.world.HexCoord;
import com.measim.model.world.Tile;
import com.measim.service.communication.CommunicationService;
import com.measim.service.llm.LlmService;
import com.measim.model.llm.LlmResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class RiskServiceImpl implements RiskService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GM_ID = "GAME_MASTER";

    private final RiskDao riskDao;
    private final InfrastructureDao infraDao;
    private final AgentDao agentDao;
    private final WorldDao worldDao;
    private final LlmService llmService;
    private final CommunicationService commService;
    private final Random rng;

    @Inject
    public RiskServiceImpl(RiskDao riskDao, InfrastructureDao infraDao, AgentDao agentDao,
                            WorldDao worldDao, LlmService llmService,
                            CommunicationService commService) {
        this.riskDao = riskDao;
        this.infraDao = infraDao;
        this.agentDao = agentDao;
        this.worldDao = worldDao;
        this.llmService = llmService;
        this.commService = commService;
        this.rng = new Random(12345);
    }

    @Override
    public List<RiskEvent> evaluateRisks(int currentTick) {
        List<RiskEvent> triggered = new ArrayList<>();

        for (RiskProfile profile : riskDao.getAllProfiles()) {
            int age = profile.ageTicks(currentTick);
            int ticksSinceMaint = profile.ticksSinceLastMaintenance(currentTick);
            double envHealth = getEnvHealthForEntity(profile);

            for (Risk risk : profile.trueRisks()) {
                double prob = risk.effectiveProbability(
                        age, profile.usageIntensity(), ticksSinceMaint,
                        envHealth, profile.neighborRiskLoad());

                if (rng.nextDouble() < prob) {
                    double severity = risk.minSeverity() +
                            rng.nextDouble() * (risk.maxSeverity() - risk.minSeverity());

                    RiskEvent event = adjudicateConsequences(risk, profile, severity, currentTick);
                    triggered.add(event);
                    riskDao.recordEvent(event);

                    commService.logThought(GM_ID,
                            String.format("RISK: %s on %s/%s — severity %.2f (prob %.4f, age=%d, usage=%.0f%%, maint=%d, env=%.2f)",
                                    risk.name(), profile.entityType(), profile.entityId(),
                                    severity, prob, age, profile.usageIntensity() * 100,
                                    ticksSinceMaint, envHealth),
                            Message.Channel.GM_WORLD_NARRATION, currentTick);
                }
            }
        }
        return triggered;
    }

    @Override
    public void applyConsequences(List<RiskEvent> events, int currentTick) {
        for (RiskEvent event : events) {
            switch (event.entityType()) {
                case INFRASTRUCTURE -> applyToInfrastructure(event);
                case AGENT -> applyToAgent(event);
                case TILE -> applyToTile(event);
                default -> {}
            }
        }
    }

    @Override
    public void propagateCascades(List<RiskEvent> events, int currentTick) {
        for (RiskEvent event : events) {
            Object cascadeObj = event.consequences().get("cascadeRadius");
            int radius = cascadeObj instanceof Number n ? n.intValue() : 0;
            if (radius <= 0) continue;

            HexCoord epicenter = getEntityLocation(event.entityId(), event.entityType());
            if (epicenter == null) continue;

            recalculateNeighborLoads(epicenter, radius, currentTick);

            commService.logThought(GM_ID,
                    String.format("Cascade from %s: updating risk loads within %d hexes",
                            event.riskName(), radius),
                    Message.Channel.GM_INTERNAL, currentTick);
        }
    }

    @Override
    public void registerProfile(RiskProfile profile) { riskDao.registerProfile(profile); }

    @Override
    public void updateEntityState(String entityId, double usageIntensity, int currentTick, boolean maintained) {
        riskDao.getProfile(entityId).ifPresent(profile -> {
            profile.setUsageIntensity(usageIntensity);
            if (maintained) profile.recordMaintenance(currentTick);
        });
    }

    @Override
    public void recalculateNeighborLoads(HexCoord epicenter, int radius, int currentTick) {
        double recentLoad = riskDao.getAllEvents().stream()
                .filter(e -> e.tick() >= currentTick - 12)
                .mapToDouble(RiskEvent::severity)
                .sum();

        for (RiskProfile profile : riskDao.getAllProfiles()) {
            HexCoord loc = getEntityLocation(profile.entityId(), profile.entityType());
            if (loc != null && loc.distanceTo(epicenter) <= radius) {
                double falloff = 1.0 / (1.0 + loc.distanceTo(epicenter));
                profile.setNeighborRiskLoad(recentLoad * falloff);
            }
        }
    }

    // ====== CONSEQUENCE ADJUDICATION ======

    private RiskEvent adjudicateConsequences(Risk risk, RiskProfile profile,
                                              double severity, int currentTick) {
        if (!llmService.isAvailable()) {
            return adjudicateDeterministic(risk, profile, severity, currentTick);
        }
        try {
            String systemPrompt = """
                    You are the Game Master (physics engine) adjudicating a triggered risk event in MeritSim.
                    Determine SPECIFIC consequences. Be realistic and proportional to severity.
                    Consider: cascading effects to nearby entities, environmental byproducts released,
                    impact on the entity owner's credits and operations, and whether connected
                    infrastructure/services are affected.
                    JSON only:
                    {"narrative":"What happened (vivid, 2-3 sentences)","conditionLoss":0.0-1.0,"creditCost":N,"environmentalDamage":0.0-1.0,"productionHalted":false,"resourceLossPercent":0.0-1.0,"satisfactionImpact":-1.0 to 0,"cascadeRadius":0-5,"destroyed":false}
                    """;
            // Build rich context for the GM
            String ownerInfo = "";
            String locationInfo = "";
            if (profile.entityType() == RiskProfile.EntityType.INFRASTRUCTURE) {
                infraDao.getById(profile.entityId()).ifPresent(infra -> {});
                var infraOpt = infraDao.getById(profile.entityId());
                if (infraOpt.isPresent()) {
                    var infra = infraOpt.get();
                    ownerInfo = "Owner: " + infra.ownerId() + ". ";
                    var tile = worldDao.getTile(infra.location());
                    if (tile != null) locationInfo = "Terrain: " + tile.terrain() + ", env health: "
                            + String.format("%.2f", tile.environment().averageHealth()) + ". ";
                }
            }
            String userPrompt = String.format(
                    "Risk: %s (%s) on %s/%s. %s%sSeverity: %.2f. Age: %d ticks. Usage: %.0f%%. Maintenance gap: %d ticks. Env health: %.2f. Neighbor risk load: %.2f. Can cascade: %s (radius %d).",
                    risk.name(), risk.category(), profile.entityType(), profile.entityId(),
                    ownerInfo, locationInfo,
                    severity, profile.ageTicks(currentTick), profile.usageIntensity() * 100,
                    profile.ticksSinceLastMaintenance(currentTick),
                    getEnvHealthForEntity(profile), profile.neighborRiskLoad(),
                    risk.canCascade(), risk.cascadeRadius());

            LlmResponse response = llmService.queryGameMaster(systemPrompt, userPrompt).join();
            return parseResponse(response.content(), risk, profile, severity, currentTick);
        } catch (Exception e) {
            return adjudicateDeterministic(risk, profile, severity, currentTick);
        }
    }

    private RiskEvent adjudicateDeterministic(Risk risk, RiskProfile profile,
                                                double severity, int currentTick) {
        Map<String, Object> consequences = new HashMap<>();
        consequences.put("conditionLoss", severity * 0.3);
        consequences.put("creditCost", severity * 150);
        consequences.put("satisfactionImpact", -severity * 0.1);
        if (severity > 0.7) consequences.put("environmentalDamage", severity * 0.15);
        if (severity > 0.9) consequences.put("destroyed", true);
        if (risk.canCascade() && severity > 0.5) consequences.put("cascadeRadius", risk.cascadeRadius());
        for (Risk.ConsequenceType type : risk.possibleConsequences()) {
            switch (type) {
                case PRODUCTION_HALT -> { if (severity > 0.5) consequences.put("productionHalted", true); }
                case RESOURCE_LOSS -> consequences.put("resourceLossPercent", severity * 0.2);
                default -> {}
            }
        }
        return new RiskEvent("risk_" + UUID.randomUUID().toString().substring(0, 8),
                risk.id(), profile.entityId(), profile.entityType(),
                risk.name(), severity, currentTick, consequences,
                String.format("%s struck %s/%s (severity %.1f)", risk.name(), profile.entityType(), profile.entityId(), severity));
    }

    private RiskEvent parseResponse(String content, Risk risk, RiskProfile profile,
                                      double severity, int currentTick) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode root = MAPPER.readTree(json);
            Map<String, Object> consequences = new HashMap<>();
            consequences.put("conditionLoss", root.path("conditionLoss").asDouble(0));
            consequences.put("creditCost", root.path("creditCost").asDouble(0));
            consequences.put("environmentalDamage", root.path("environmentalDamage").asDouble(0));
            consequences.put("productionHalted", root.path("productionHalted").asBoolean(false));
            consequences.put("resourceLossPercent", root.path("resourceLossPercent").asDouble(0));
            consequences.put("satisfactionImpact", root.path("satisfactionImpact").asDouble(0));
            consequences.put("cascadeRadius", root.path("cascadeRadius").asInt(0));
            consequences.put("destroyed", root.path("destroyed").asBoolean(false));
            return new RiskEvent("risk_" + UUID.randomUUID().toString().substring(0, 8),
                    risk.id(), profile.entityId(), profile.entityType(),
                    risk.name(), severity, currentTick, consequences,
                    root.path("narrative").asText(risk.description()));
        } catch (Exception e) {
            return adjudicateDeterministic(risk, profile, severity, currentTick);
        }
    }

    // ====== CONSEQUENCE APPLICATION ======

    private void applyToInfrastructure(RiskEvent event) {
        infraDao.getById(event.entityId()).ifPresent(infra -> {
            double condLoss = getNum(event.consequences(), "conditionLoss");
            for (int i = 0; i < (int)(condLoss * 10); i++) infra.tickMaintenance(false);
            double cost = getNum(event.consequences(), "creditCost");
            if (cost > 0) {
                Agent owner = agentDao.getAgent(infra.ownerId());
                if (owner != null) owner.state().spendCredits(cost);
            }
            double env = getNum(event.consequences(), "environmentalDamage");
            if (env > 0) {
                Tile tile = worldDao.getTile(infra.location());
                if (tile != null) tile.environment().applyPollution(env);
            }
        });
    }

    private void applyToAgent(RiskEvent event) {
        Agent agent = agentDao.getAgent(event.entityId());
        if (agent == null) return;
        double cost = getNum(event.consequences(), "creditCost");
        if (cost > 0) agent.state().spendCredits(cost);
        double sat = getNum(event.consequences(), "satisfactionImpact");
        if (sat != 0) agent.state().setSatisfaction(agent.state().satisfaction() + sat);
    }

    private void applyToTile(RiskEvent event) {
        double env = getNum(event.consequences(), "environmentalDamage");
        if (env > 0) {
            for (Tile tile : worldDao.getAllTiles())
                if (tile.environment().isCrisis()) tile.environment().applyPollution(env * 0.05);
        }
    }

    // ====== HELPERS ======

    private double getEnvHealthForEntity(RiskProfile profile) {
        HexCoord loc = getEntityLocation(profile.entityId(), profile.entityType());
        if (loc != null) {
            Tile tile = worldDao.getTile(loc);
            if (tile != null) return tile.environment().averageHealth();
        }
        return 0.8;
    }

    private HexCoord getEntityLocation(String entityId, RiskProfile.EntityType type) {
        return switch (type) {
            case INFRASTRUCTURE -> infraDao.getById(entityId).map(Infrastructure::location).orElse(null);
            case AGENT -> {
                Agent a = agentDao.getAgent(entityId);
                yield a != null ? a.state().location() : null;
            }
            default -> null;
        };
    }

    private static double getNum(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number n ? n.doubleValue() : 0;
    }
}
