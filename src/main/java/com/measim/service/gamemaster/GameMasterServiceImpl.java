package com.measim.service.gamemaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.measim.dao.AgentDao;
import com.measim.dao.InfrastructureDao;
import com.measim.dao.ProductionChainDao;
import com.measim.dao.TechnologyRegistryDao;
import com.measim.dao.WorldDao;
import com.measim.model.economy.ItemType;
import com.measim.model.economy.ProductionChain;
import com.measim.model.gamemaster.*;
import com.measim.model.infrastructure.*;
import com.measim.model.llm.LlmResponse;
import com.measim.model.world.HexCoord;
import com.measim.model.world.Tile;
import com.measim.service.communication.CommunicationService;
import com.measim.model.communication.Message;
import com.measim.service.llm.LlmService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Singleton
public class GameMasterServiceImpl implements GameMasterService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GM_ID = "GAME_MASTER";

    private final TechnologyRegistryDao techRegistry;
    private final ProductionChainDao chainDao;
    private final InfrastructureDao infraDao;
    private final WorldDao worldDao;
    private final AgentDao agentDao;
    private final LlmService llmService;
    private final CommunicationService commService;
    private final List<NovelAction> pendingNovelActions = new ArrayList<>();
    private final List<String> recentEventLog = new ArrayList<>();
    private final Random fallbackRandom;

    @Inject
    public GameMasterServiceImpl(TechnologyRegistryDao techRegistry,
                                  ProductionChainDao chainDao, InfrastructureDao infraDao,
                                  WorldDao worldDao, AgentDao agentDao,
                                  LlmService llmService, CommunicationService commService) {
        this.techRegistry = techRegistry;
        this.chainDao = chainDao;
        this.infraDao = infraDao;
        this.worldDao = worldDao;
        this.agentDao = agentDao;
        this.llmService = llmService;
        this.commService = commService;
        this.fallbackRandom = new Random(42);
    }

    // ========== INITIALIZATION ==========

    @Override
    public void initializeBaseTechTree() {
        techRegistry.addTechNode(new TechNode("basic_metallurgy", "Basic Metallurgy", Set.of(), 0, true));
        techRegistry.addTechNode(new TechNode("basic_agriculture", "Basic Agriculture", Set.of(), 0, true));
        techRegistry.addTechNode(new TechNode("basic_electronics", "Basic Electronics", Set.of("basic_metallurgy"), 1, true));
        techRegistry.addTechNode(new TechNode("basic_chemistry", "Basic Chemistry", Set.of(), 0, true));
        techRegistry.addTechNode(new TechNode("woodworking", "Woodworking", Set.of(), 0, true));
        techRegistry.addTechNode(new TechNode("basic_medicine", "Basic Medicine", Set.of("basic_chemistry", "basic_agriculture"), 1, true));
    }

    // ========== RESEARCH ==========

    @Override
    public void submitResearch(String agentId, String direction, double creditInvestment, int currentTick) {
        int researchTime = (int) Math.max(1, 3 - creditInvestment / 1000);
        techRegistry.submitProposal(new ResearchProposal(
                agentId, direction, creditInvestment, currentTick, researchTime,
                "Research in " + direction));
    }

    @Override
    public List<DiscoverySpec> processReadyProposals(int currentTick) {
        List<DiscoverySpec> discoveries = new ArrayList<>();
        for (ResearchProposal proposal : techRegistry.getReadyProposals(currentTick)) {
            adjudicateProposal(proposal).ifPresent(discovery -> {
                registerDiscovery(discovery);
                discoveries.add(discovery);
            });
            techRegistry.removeProposal(proposal);
        }
        return discoveries;
    }

    @Override
    public Optional<DiscoverySpec> adjudicateProposal(ResearchProposal proposal) {
        if (!llmService.isAvailable()) return adjudicateResearchDeterministic(proposal);
        try {
            String experience = getAgentExperience(proposal.agentId());
            String basePrompt = GameMasterPrompts.researchUserPrompt(proposal, techRegistry.getAllTechNodes(),
                    techRegistry.getAllDiscoveries(), techRegistry.techTreeDepth());
            // Append agent experience
            String userPrompt = basePrompt + "\nResearcher experience: " + experience
                    + "\n(More research experience = higher success chance, better discoveries)\n";

            LlmResponse response = llmService.queryGameMaster(
                    GameMasterPrompts.researchSystemPrompt(), userPrompt).join();
            return parseDiscoveryResponse(response.content(), proposal);
        } catch (Exception e) {
            return adjudicateResearchDeterministic(proposal);
        }
    }

    // ========== FREE-FORM ACTION RESOLUTION ==========

    @Override
    public FreeFormResolution resolveFreeFormAction(String agentId, String description,
                                                     double creditBudget, int currentTick) {
        var agent = agentDao.getAgent(agentId);
        if (agent == null) return new FreeFormResolution(false, "Agent not found", 0, 0, 0,
                Map.of(), null, null, List.of(), List.of(), "unknown");

        commService.logThought(agentId,
                "Free-form action: " + description + " (budget: " + String.format("%.0f", creditBudget) + ")",
                Message.Channel.AGENT_INTERNAL, currentTick);

        if (!llmService.isAvailable()) {
            return resolveFreeFormDeterministic(agent, description, creditBudget, currentTick);
        }

        try {
            String experience = agent.state().experienceSummary();
            String archetype = agent.identity().archetype().name();
            String inventory = agent.state().inventory().toString();
            String assets = buildOwnedAssetsSummary(agentId);
            String spatial = "At tile (" + agent.state().location().q() + "," + agent.state().location().r() + ")";

            String systemPrompt = GameMasterPrompts.freeFormActionSystemPrompt();
            String userPrompt = GameMasterPrompts.freeFormActionUserPrompt(
                    agentId, archetype, description, creditBudget,
                    experience, inventory, assets, spatial);

            commService.logThought(GM_ID, "Resolving free-form action from " + agentId + ": " + description,
                    Message.Channel.GM_INTERNAL, currentTick);

            LlmResponse response = llmService.queryGameMaster(systemPrompt, userPrompt).join();
            commService.logThought(GM_ID, response.content(), Message.Channel.GM_INTERNAL, currentTick);

            return parseFreeFormResolution(response.content(), agentId, description, creditBudget, currentTick);
        } catch (Exception e) {
            return resolveFreeFormDeterministic(agent, description, creditBudget, currentTick);
        }
    }

    private FreeFormResolution resolveFreeFormDeterministic(com.measim.model.agent.Agent agent,
                                                             String description, double creditBudget,
                                                             int currentTick) {
        // Simple heuristic: success based on budget and experience
        double successChance = Math.min(0.7, creditBudget / 1000.0);
        boolean success = fallbackRandom.nextDouble() < successChance;
        double cost = creditBudget * (success ? 0.6 : 0.3);
        double gain = success ? creditBudget * 0.3 : 0;
        String domain = description.length() > 20 ? description.substring(0, 20) : description;

        return new FreeFormResolution(success,
                success ? "Action succeeded: " + description : "Action failed: " + description,
                cost, gain, success ? 0.02 : -0.02, Map.of(), null, null,
                List.of(), List.of(), domain);
    }

    private FreeFormResolution parseFreeFormResolution(String content, String agentId,
                                                        String description, double creditBudget,
                                                        int currentTick) {
        try {
            String json = stripMarkdown(content);
            JsonNode root = MAPPER.readTree(json);
            boolean success = root.path("success").asBoolean(false);

            Map<String, Integer> invChanges = new HashMap<>();
            JsonNode invNode = root.path("inventoryChanges");
            if (invNode.isObject()) {
                invNode.fields().forEachRemaining(e -> invChanges.put(e.getKey(), e.getValue().asInt(0)));
            }

            return new FreeFormResolution(
                    success,
                    root.path("narrative").asText(description),
                    root.path("creditCost").asDouble(0),
                    root.path("creditGain").asDouble(0),
                    root.path("satisfactionChange").asDouble(0),
                    invChanges,
                    root.path("createdEntityType").asText(null),
                    root.path("createdEntityId").asText(null),
                    List.of(), // TODO: parse risks from response
                    List.of(), // TODO: parse byproducts from response
                    root.path("experienceDomain").asText("general"));
        } catch (Exception e) {
            return resolveFreeFormDeterministic(agentDao.getAgent(agentId), description, creditBudget, currentTick);
        }
    }

    private String buildOwnedAssetsSummary(String agentId) {
        StringBuilder sb = new StringBuilder();
        var infras = infraDao.getByOwner(agentId);
        if (!infras.isEmpty()) {
            sb.append("Infrastructure: ");
            for (var i : infras) sb.append(i.type().name()).append(" (condition ").append(String.format("%.0f%%", i.condition() * 100)).append("), ");
        }
        // Services would be added here when ServiceDao has getByOwner
        if (sb.isEmpty()) sb.append("None");
        return sb.toString();
    }

    // ========== NOVEL AGENT ACTIONS ==========

    @Override
    public void submitNovelAction(NovelAction action) {
        pendingNovelActions.add(action);
    }

    @Override
    public List<WorldEvent> adjudicateNovelActions(int currentTick, WorldState worldState) {
        List<WorldEvent> events = new ArrayList<>();
        List<NovelAction> toProcess = new ArrayList<>(pendingNovelActions);
        pendingNovelActions.clear();

        for (NovelAction action : toProcess) {
            WorldEvent event = adjudicateOneNovelAction(action, currentTick, worldState);
            if (event != null) {
                events.add(event);
                logEvent(event);
            }
        }
        return events;
    }

    private WorldEvent adjudicateOneNovelAction(NovelAction action, int currentTick,
                                                  WorldState worldState) {
        if (!llmService.isAvailable()) {
            return adjudicateNovelActionDeterministic(action, currentTick);
        }
        try {
            String experience = getAgentExperience(action.agentId());
            LlmResponse response = llmService.queryGameMaster(
                    GameMasterPrompts.novelActionSystemPrompt(),
                    GameMasterPrompts.novelActionUserPrompt(action, worldState, experience)
            ).join();

            return parseNovelActionResponse(response.content(), action, currentTick);
        } catch (Exception e) {
            return adjudicateNovelActionDeterministic(action, currentTick);
        }
    }

    private WorldEvent adjudicateNovelActionDeterministic(NovelAction action, int currentTick) {
        // Deterministic fallback: outcome based on action type and credit stake
        double successChance = Math.min(0.7, action.creditStake() / 2000.0);
        boolean success = fallbackRandom.nextDouble() < successChance;

        WorldEvent.WorldEventType eventType = switch (action.type()) {
            case NOVEL_BUSINESS -> WorldEvent.WorldEventType.NOVEL_BUSINESS_MODEL;
            case SYSTEM_GAMING -> WorldEvent.WorldEventType.EXPLOITATION_ATTEMPT;
            case PUBLIC_WORKS -> WorldEvent.WorldEventType.PHILANTHROPIC_PROJECT;
            case POLITICAL_CAMPAIGN -> WorldEvent.WorldEventType.POLITICAL_MANEUVER;
            case ARTISANAL_CREATION -> WorldEvent.WorldEventType.ARTISTIC_CREATION;
            case NOVEL_AUTOMATION, FINANCIAL_INNOVATION, OPERATIONAL_RESTRUCTURE ->
                    WorldEvent.WorldEventType.NOVEL_BUSINESS_MODEL;
            case UBI_EXPLOITATION -> WorldEvent.WorldEventType.EXPLOITATION_ATTEMPT;
            case REGULATORY_INNOVATION -> WorldEvent.WorldEventType.POLITICAL_MANEUVER;
            case SPECULATIVE_RESEARCH -> WorldEvent.WorldEventType.TECH_BREAKTHROUGH;
            case DESPERATE_MEASURE -> WorldEvent.WorldEventType.SOCIAL_UNREST;
        };

        String outcome = success ? "succeeded" : "failed";
        return new WorldEvent(
                "novel_" + UUID.randomUUID().toString().substring(0, 8),
                eventType,
                action.type().name() + " by " + action.archetypeName(),
                String.format("Agent %s attempted %s and %s (stake: %.0f credits)",
                        action.agentId(), action.type(), outcome, action.creditStake()),
                currentTick,
                success ? 0.3 : 0.1,
                List.of(),
                Map.of("success", success, "creditChange", success ? action.creditStake() * 0.5 : -action.creditStake() * 0.3,
                        "agentId", action.agentId())
        );
    }

    // ========== INFRASTRUCTURE EVALUATION (agent proposes, GM evaluates) ==========

    @Override
    public Optional<InfrastructureType> evaluateInfrastructureProposal(InfrastructureProposal proposal, int currentTick) {
        // Log the agent's proposal as observable communication
        commService.logThought(proposal.agentId(),
                String.format("Proposing infrastructure: %s — %s using %s (budget: %.0f)",
                        proposal.proposedName(), proposal.intendedPurpose(),
                        proposal.proposedMaterials(), proposal.creditBudget()),
                Message.Channel.AGENT_INTERNAL, currentTick);

        if (!llmService.isAvailable()) {
            return evaluateInfrastructureDeterministic(proposal, currentTick);
        }

        try {
            Tile locationTile = worldDao.getTile(proposal.location());
            Tile connectionTile = proposal.connectTo() != null ? worldDao.getTile(proposal.connectTo()) : null;

            String systemPrompt = GameMasterPrompts.infrastructureEvalSystemPrompt();
            String experience = getAgentExperience(proposal.agentId());
            String userPrompt = GameMasterPrompts.infrastructureEvalUserPrompt(
                    proposal, techRegistry.getAllTechNodes(), infraDao.getAllTypes(),
                    locationTile != null ? locationTile.terrain().name() : "UNKNOWN",
                    connectionTile != null ? connectionTile.terrain().name() : "N/A",
                    experience);

            // Log GM's thinking
            commService.logThought(GM_ID,
                    "Evaluating infrastructure proposal from " + proposal.agentId() + ": " + proposal.proposedName(),
                    Message.Channel.GM_INTERNAL, currentTick);

            LlmResponse response = llmService.queryGameMaster(systemPrompt, userPrompt).join();

            // Log the full GM reasoning
            commService.logThought(GM_ID, response.content(), Message.Channel.GM_INTERNAL, currentTick);

            return parseInfrastructureEvaluation(response.content(), proposal, currentTick);
        } catch (Exception e) {
            return evaluateInfrastructureDeterministic(proposal, currentTick);
        }
    }

    private Optional<InfrastructureType> evaluateInfrastructureDeterministic(
            InfrastructureProposal proposal, int currentTick) {
        // Deterministic fallback: evaluate based on simple heuristics
        Tile locationTile = worldDao.getTile(proposal.location());
        if (locationTile == null || !locationTile.terrain().isPassable()) {
            commService.logThought(GM_ID, "Rejected: invalid or impassable terrain",
                    Message.Channel.GM_INTERNAL, currentTick);
            return Optional.empty();
        }

        if (proposal.creditBudget() < 100) {
            commService.logThought(GM_ID, "Rejected: insufficient budget for any infrastructure",
                    Message.Channel.GM_INTERNAL, currentTick);
            return Optional.empty();
        }

        // Determine connection mode
        InfrastructureType.ConnectionMode mode = proposal.connectTo() != null
                ? InfrastructureType.ConnectionMode.POINT_TO_POINT
                : InfrastructureType.ConnectionMode.TILE_LOCAL;

        // Scale properties by budget
        double budgetFactor = Math.min(3.0, proposal.creditBudget() / 300.0);
        double capacity = 2.0 + budgetFactor * 2.0;
        double maintenance = proposal.creditBudget() * 0.01;
        int maxRange = proposal.connectTo() != null
                ? proposal.location().distanceTo(proposal.connectTo()) + 2 : 0;

        // Pick effect based on purpose keywords
        List<InfrastructureEffect> effects = new ArrayList<>();
        String purpose = proposal.intendedPurpose().toLowerCase();
        if (purpose.contains("transport") || purpose.contains("move") || purpose.contains("pipe") || purpose.contains("aqueduct")) {
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.RESOURCE_TRANSPORT, capacity, null));
        } else if (purpose.contains("trade") || purpose.contains("road") || purpose.contains("rail")) {
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.TRADE_COST_REDUCTION, Math.min(0.5, budgetFactor * 0.2), null));
        } else if (purpose.contains("clean") || purpose.contains("remediat") || purpose.contains("pollution")) {
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.ENVIRONMENTAL_REMEDIATION, Math.min(0.08, budgetFactor * 0.03), null));
            mode = InfrastructureType.ConnectionMode.AREA_OF_EFFECT;
        } else if (purpose.contains("extract") || purpose.contains("mine") || purpose.contains("drill")) {
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.EXTRACTION_BOOST, 1.0 + budgetFactor * 0.5, null));
            mode = InfrastructureType.ConnectionMode.TILE_LOCAL;
        } else if (purpose.contains("factory") || purpose.contains("production") || purpose.contains("manufact")) {
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.PRODUCTION_SPEED_BOOST, 1.0 + budgetFactor * 0.3, null));
            mode = InfrastructureType.ConnectionMode.TILE_LOCAL;
        } else if (purpose.contains("storage") || purpose.contains("warehouse")) {
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.STORAGE_CAPACITY, 20 + budgetFactor * 15, null));
            mode = InfrastructureType.ConnectionMode.TILE_LOCAL;
        } else {
            // Generic: small transport + small trade cost reduction
            effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.RESOURCE_TRANSPORT, capacity * 0.5, null));
        }

        String id = "infra_type_" + UUID.randomUUID().toString().substring(0, 8);
        String reasoning = String.format("Deterministic evaluation: budget %.0f, terrain %s, purpose '%s' → %s with %d effects",
                proposal.creditBudget(), locationTile.terrain(), proposal.intendedPurpose(), mode, effects.size());
        commService.logThought(GM_ID, reasoning, Message.Channel.GM_INTERNAL, currentTick);

        InfrastructureType type = new InfrastructureType(
                id, proposal.proposedName(), proposal.proposedDescription(), mode, effects,
                InfrastructureConstraints.defaults(),
                proposal.creditBudget() * 0.8,  // construction cost = 80% of budget
                maintenance, maxRange, capacity,
                Map.of(), true);

        infraDao.registerType(type);
        return Optional.of(type);
    }

    private Optional<InfrastructureType> parseInfrastructureEvaluation(String content,
                                                                        InfrastructureProposal proposal,
                                                                        int currentTick) {
        try {
            String json = stripMarkdown(content);
            JsonNode root = MAPPER.readTree(json);

            if (root.has("needsClarification") && root.path("needsClarification").asBoolean()) {
                String question = root.path("question").asText("Please provide more details.");
                commService.logThought(GM_ID, "Asking clarification: " + question,
                        Message.Channel.GM_TO_AGENT, currentTick);

                // Generate agent's clarification response
                String agentClarification = generateAgentClarification(
                        proposal.agentId(), question, proposal.proposedName(),
                        proposal.proposedDescription(), currentTick);

                commService.logThought(proposal.agentId(), "Clarifying: " + agentClarification,
                        Message.Channel.AGENT_TO_GM, currentTick);

                // Send clarification back to GM for final evaluation
                String followUp = "Agent's clarification: " + agentClarification
                        + "\n\nNow provide your final evaluation as JSON (feasible/not feasible).";
                LlmResponse followUpResponse = llmService.queryGameMaster(
                        GameMasterPrompts.infrastructureEvalSystemPrompt(), followUp).join();

                commService.logThought(GM_ID, followUpResponse.content(), Message.Channel.GM_INTERNAL, currentTick);

                // Parse the follow-up response (if still needs clarification, fall back to deterministic)
                JsonNode followUpRoot = MAPPER.readTree(stripMarkdown(followUpResponse.content()));
                if (followUpRoot.has("needsClarification")) {
                    return evaluateInfrastructureDeterministic(proposal, currentTick);
                }
                root = followUpRoot; // Continue with normal parsing below
            }

            if (!root.path("feasible").asBoolean(false)) {
                commService.logThought(GM_ID,
                        "Rejected: " + root.path("reasoning").asText("Not feasible"),
                        Message.Channel.GM_INTERNAL, currentTick);
                return Optional.empty();
            }

            String id = "infra_type_" + UUID.randomUUID().toString().substring(0, 8);
            String name = root.path("name").asText(proposal.proposedName());
            String desc = root.path("description").asText(proposal.proposedDescription());

            InfrastructureType.ConnectionMode mode;
            try { mode = InfrastructureType.ConnectionMode.valueOf(root.path("connectionMode").asText("TILE_LOCAL")); }
            catch (Exception e) { mode = InfrastructureType.ConnectionMode.TILE_LOCAL; }

            double constructionCost = root.path("constructionCost").asDouble(proposal.creditBudget() * 0.8);
            double maintenance = root.path("maintenanceCostPerTick").asDouble(constructionCost * 0.01);
            int maxRange = root.path("maxRange").asInt(10);
            double capacity = root.path("capacity").asDouble(5.0);
            int footprint = Math.max(1, Math.min(5, root.path("footprint").asInt(1)));
            double envPressure = Math.max(0, Math.min(0.1, root.path("environmentalPressure").asDouble(0.005)));

            List<InfrastructureEffect> effects = new ArrayList<>();
            JsonNode effectsNode = root.path("effects");
            if (effectsNode.isArray()) {
                for (JsonNode e : effectsNode) {
                    InfrastructureEffect.EffectType effectType;
                    try { effectType = InfrastructureEffect.EffectType.valueOf(e.path("type").asText("CUSTOM")); }
                    catch (Exception ex) { effectType = InfrastructureEffect.EffectType.CUSTOM; }
                    double magnitude = e.path("magnitude").asDouble(1.0);
                    String target = e.path("targetResource").asText(null);
                    if ("null".equals(target)) target = null;
                    effects.add(new InfrastructureEffect(effectType, magnitude, target));
                }
            }

            if (effects.isEmpty()) {
                effects.add(new InfrastructureEffect(InfrastructureEffect.EffectType.CUSTOM, 1.0, null));
            }

            Set<com.measim.model.world.TerrainType> allowedTerrains = InfrastructureConstraints.defaults().allowedTerrains();
            InfrastructureConstraints constraints = new InfrastructureConstraints(
                    footprint, allowedTerrains, Set.of(), envPressure, false);

            InfrastructureType type = new InfrastructureType(
                    id, name, desc, mode, effects, constraints,
                    constructionCost, maintenance, maxRange, capacity,
                    Map.of(), true);

            infraDao.registerType(type);

            commService.logThought(GM_ID,
                    String.format("Approved: %s — cost %.0f, maintenance %.1f/tick, %d effects. %s",
                            name, constructionCost, maintenance, effects.size(),
                            root.path("reasoning").asText("")),
                    Message.Channel.GM_INTERNAL, currentTick);

            return Optional.of(type);
        } catch (Exception e) {
            return evaluateInfrastructureDeterministic(proposal, currentTick);
        }
    }

    // ========== SPONTANEOUS WORLD EVENTS ==========

    @Override
    public List<WorldEvent> generateWorldEvents(int currentTick, WorldState worldState) {
        if (!llmService.isAvailable()) {
            return generateWorldEventsDeterministic(currentTick, worldState);
        }
        try {
            String tileSummaries = buildNotableTileSummaries();
            LlmResponse response = llmService.queryGameMaster(
                    GameMasterPrompts.worldEventSystemPrompt(),
                    GameMasterPrompts.worldEventUserPrompt(worldState, tileSummaries)
            ).join();
            List<WorldEvent> events = parseWorldEvents(response.content(), currentTick);
            events.forEach(this::logEvent);
            return events;
        } catch (Exception e) {
            return generateWorldEventsDeterministic(currentTick, worldState);
        }
    }

    private List<WorldEvent> generateWorldEventsDeterministic(int currentTick, WorldState worldState) {
        List<WorldEvent> events = new ArrayList<>();

        // Environmental disaster if health is critically low
        if (worldState.averageEnvironmentalHealth() < 0.3 && fallbackRandom.nextDouble() < 0.3) {
            events.add(new WorldEvent(
                    "env_crisis_" + currentTick, WorldEvent.WorldEventType.ENVIRONMENTAL_DISASTER,
                    "Environmental Crisis", "Pollution levels have reached critical thresholds. Crop yields dropping.",
                    currentTick, 0.7, List.of(), Map.of()));
        }

        // Social unrest if satisfaction is very low
        if (worldState.averageSatisfaction() < 0.25 && fallbackRandom.nextDouble() < 0.25) {
            events.add(new WorldEvent(
                    "unrest_" + currentTick, WorldEvent.WorldEventType.SOCIAL_UNREST,
                    "Social Unrest", "Widespread widespread dissatisfaction has led to protests and work stoppages.",
                    currentTick, 0.5, List.of(), Map.of()));
        }

        // Market boom if economy is healthy
        if (worldState.giniCoefficient() < 0.3 && worldState.averageSatisfaction() > 0.7
                && fallbackRandom.nextDouble() < 0.1) {
            events.add(new WorldEvent(
                    "boom_" + currentTick, WorldEvent.WorldEventType.MARKET_BOOM,
                    "Economic Boom", "Consumer confidence drives a surge in spending and investment.",
                    currentTick, 0.4, List.of(), Map.of()));
        }

        // Spontaneous resource discovery (~once per year)
        if (fallbackRandom.nextDouble() < 0.08) {
            int q = fallbackRandom.nextInt(50);
            int r = fallbackRandom.nextInt(50);
            events.add(new WorldEvent(
                    "resource_" + currentTick, WorldEvent.WorldEventType.RESOURCE_DISCOVERY,
                    "New Resource Deposit", "Geological survey reveals untapped mineral deposits.",
                    currentTick, 0.3, List.of(new HexCoord(q, r)), Map.of()));
        }

        // High inequality pressure
        if (worldState.giniCoefficient() > 0.55 && fallbackRandom.nextDouble() < 0.2) {
            events.add(new WorldEvent(
                    "migration_" + currentTick, WorldEvent.WorldEventType.MIGRATION_WAVE,
                    "Migration Pressure", "Agents in high-inequality regions seek better opportunities elsewhere.",
                    currentTick, 0.4, List.of(), Map.of()));
        }

        events.forEach(this::logEvent);
        return events;
    }

    // ========== WORLD COHERENCE AUDIT ==========

    @Override
    public List<WorldEvent> auditWorldCoherence(int currentTick, WorldState worldState) {
        if (!llmService.isAvailable()) {
            return auditCoherenceDeterministic(currentTick, worldState);
        }
        try {
            LlmResponse response = llmService.queryGameMaster(
                    GameMasterPrompts.coherenceAuditSystemPrompt(),
                    GameMasterPrompts.worldEventUserPrompt(worldState, buildNotableTileSummaries())
            ).join();
            return parseCoherenceResponse(response.content(), currentTick);
        } catch (Exception e) {
            return auditCoherenceDeterministic(currentTick, worldState);
        }
    }

    private List<WorldEvent> auditCoherenceDeterministic(int currentTick, WorldState worldState) {
        List<WorldEvent> corrections = new ArrayList<>();

        // Check: high inequality + high satisfaction is incoherent
        if (worldState.giniCoefficient() > 0.6 && worldState.averageSatisfaction() > 0.8) {
            corrections.add(new WorldEvent(
                    "coherence_" + currentTick, WorldEvent.WorldEventType.COHERENCE_CORRECTION,
                    "Satisfaction Adjustment",
                    "High inequality is creating hidden resentment that surface metrics don't capture.",
                    currentTick, 0.3, List.of(),
                    Map.of("satisfactionDelta", -0.1)));
        }

        // Check: many robots but no LD diversion effect visible
        if (worldState.totalRobots() > worldState.totalAgents() * 0.5
                && worldState.giniCoefficient() < 0.2) {
            corrections.add(new WorldEvent(
                    "coherence_auto_" + currentTick, WorldEvent.WorldEventType.COHERENCE_CORRECTION,
                    "Automation Impact Recognition",
                    "Heavy automation should be creating more economic stratification than currently observed.",
                    currentTick, 0.2, List.of(), Map.of()));
        }

        return corrections;
    }

    // ========== GETTERS ==========

    @Override public int techTreeDepth() { return techRegistry.techTreeDepth(); }
    @Override public int discoveryCount() { return techRegistry.discoveryCount(); }

    // ========== INTERNAL HELPERS ==========

    /**
     * Generate an agent's response to a GM clarification question.
     * Uses agent LLM if available, otherwise constructs from proposal details.
     * Agent can only respond with information they would naturally have.
     */
    private String generateAgentClarification(String agentId, String question,
                                               String proposalName, String proposalDescription,
                                               int currentTick) {
        if (!llmService.isAvailable()) {
            // Deterministic: repeat proposal details as clarification
            return String.format("Regarding my proposal '%s': %s. I plan to use locally available materials and maintain it regularly.",
                    proposalName, proposalDescription);
        }

        try {
            var agent = agentDao.getAgent(agentId);
            String archetype = agent != null ? agent.identity().archetype().name() : "Unknown";

            String systemPrompt = String.format("""
                    You are agent %s (archetype: %s) in MeaSim. The Game Master asked you a clarification
                    question about your proposal. Answer based on what you would reasonably know —
                    your plans, your intentions, your materials, your maintenance approach.
                    Keep it brief (1-2 sentences). Only output your answer, no JSON.
                    """, agentId, archetype);
            String userPrompt = String.format("""
                    Your proposal: %s — %s
                    GM's question: %s
                    """, proposalName, proposalDescription, question);

            LlmResponse response = llmService.queryGameMaster(systemPrompt, userPrompt).join();
            return response.content().trim();
        } catch (Exception e) {
            return "I plan to build " + proposalName + " using standard materials and maintain it regularly.";
        }
    }

    private String buildNotableTileSummaries() {
        StringBuilder sb = new StringBuilder();
        var allTiles = worldDao.getAllTiles();
        allTiles.stream()
                .filter(t -> t.history().totalInfrastructureTicksBuilt() > 0
                        || t.history().riskEventsOccurred() > 0
                        || t.environment().averageHealth() < 0.5)
                .sorted((a, b) -> b.history().totalInfrastructureTicksBuilt() - a.history().totalInfrastructureTicksBuilt())
                .limit(8)
                .forEach(t -> sb.append(String.format("  (%d,%d) %s env=%.2f — %s%n",
                        t.coord().q(), t.coord().r(), t.terrain(),
                        t.environment().averageHealth(), t.history().summary())));
        return sb.isEmpty() ? "No notable tiles." : sb.toString();
    }

    private String getAgentExperience(String agentId) {
        var agent = agentDao.getAgent(agentId);
        if (agent == null) return "Unknown agent.";
        return agent.state().experienceSummary();
    }

    private void registerDiscovery(DiscoverySpec discovery) {
        techRegistry.registerDiscovery(discovery);
        if (discovery.category() == DiscoveryCategory.PRODUCTION_CHAIN_IMPROVEMENT
                || discovery.category() == DiscoveryCategory.NEW_PRODUCTION_CHAIN) {
            chainDao.register(new ProductionChain(
                    discovery.id(), discovery.name(),
                    discovery.inputs(), discovery.outputs(),
                    discovery.pollutionOutput(), discovery.productionTimeTicks(),
                    discovery.prerequisiteTechs(), true));
        }
        techRegistry.addTechNode(new TechNode(
                discovery.id(), discovery.name(),
                discovery.prerequisiteTechs(),
                techRegistry.techTreeDepth() + 1, true));
        logEvent(new WorldEvent("disc_" + discovery.id(),
                WorldEvent.WorldEventType.TECH_BREAKTHROUGH,
                discovery.name(), discovery.description(),
                discovery.discoveryTick(), 0.5, List.of(), Map.of()));
    }

    private void logEvent(WorldEvent event) {
        recentEventLog.add(event.name() + ": " + event.description());
        while (recentEventLog.size() > 10) recentEventLog.removeFirst();
    }

    private Optional<DiscoverySpec> adjudicateResearchDeterministic(ResearchProposal proposal) {
        double difficulty = 1.0 + techRegistry.techTreeDepth() * 0.15;
        double successThreshold = 500 * difficulty;
        if (proposal.creditInvestment() < successThreshold * 0.5) return Optional.empty();
        double successProb = Math.min(0.8, proposal.creditInvestment() / (successThreshold * 2));
        if (new Random(proposal.hashCode()).nextDouble() > successProb) return Optional.empty();
        String id = "discovery_" + UUID.randomUUID().toString().substring(0, 8);
        return Optional.of(new DiscoverySpec(id, "Improved " + proposal.direction(),
                "Deterministic fallback discovery", DiscoveryCategory.PRODUCTION_CHAIN_IMPROVEMENT,
                Map.of(ItemType.custom("MINERAL"), 8, ItemType.custom("ENERGY"), 4),
                Map.of(ItemType.custom("CONSTRUCTION"), 3), 1.5, 1, Set.of(),
                null, 0, proposal.agentId(), proposal.tickSubmitted(), false));
    }

    private Optional<DiscoverySpec> parseDiscoveryResponse(String content, ResearchProposal proposal) {
        try {
            String json = stripMarkdown(content);
            JsonNode root = MAPPER.readTree(json);
            if (!root.path("success").asBoolean(false)) return Optional.empty();
            String id = "discovery_" + UUID.randomUUID().toString().substring(0, 8);
            DiscoveryCategory cat = switch (root.path("category").asInt(1)) {
                case 2 -> DiscoveryCategory.NEW_PRODUCTION_CHAIN;
                case 3 -> DiscoveryCategory.NEW_RESOURCE;
                case 4 -> DiscoveryCategory.INFRASTRUCTURE_TECH;
                default -> DiscoveryCategory.PRODUCTION_CHAIN_IMPROVEMENT;
            };
            Map<ItemType, Integer> inputs = parseItemMap(root.path("inputs"));
            Map<ItemType, Integer> outputs = parseItemMap(root.path("outputs"));
            double pollution = Math.max(0.1, root.path("pollutionOutput").asDouble(1.0));
            int timeTicks = Math.max(1, root.path("productionTimeTicks").asInt(1));
            Set<String> prereqs = new HashSet<>();
            root.path("prerequisiteTechs").forEach(n -> prereqs.add(n.asText()));
            InfrastructureEffectType effectType = parseEffectType(root.path("effectType").asText(null));
            double effectMag = root.path("effectMagnitude").asDouble(0);
            int totalOutput = outputs.values().stream().mapToInt(Integer::intValue).sum();
            if (totalOutput > 15) return Optional.empty();
            return Optional.of(new DiscoverySpec(id, root.path("name").asText("Unnamed"),
                    root.path("description").asText(""), cat, inputs, outputs, pollution, timeTicks,
                    prereqs, effectType, effectMag, proposal.agentId(), proposal.tickSubmitted(), false));
        } catch (Exception e) {
            return adjudicateResearchDeterministic(proposal);
        }
    }

    private WorldEvent parseNovelActionResponse(String content, NovelAction action, int currentTick) {
        try {
            String json = stripMarkdown(content);
            JsonNode root = MAPPER.readTree(json);
            String outcome = root.path("outcome").asText("FAILURE");
            String narrative = root.path("narrative").asText("The attempt had no notable effect.");
            double creditChange = root.path("creditChange").asDouble(0);
            double satisfactionChange = root.path("satisfactionChange").asDouble(0);

            WorldEvent.WorldEventType type = switch (action.type()) {
                case NOVEL_BUSINESS -> WorldEvent.WorldEventType.NOVEL_BUSINESS_MODEL;
                case SYSTEM_GAMING, UBI_EXPLOITATION -> WorldEvent.WorldEventType.EXPLOITATION_ATTEMPT;
                case PUBLIC_WORKS -> WorldEvent.WorldEventType.PHILANTHROPIC_PROJECT;
                case POLITICAL_CAMPAIGN, REGULATORY_INNOVATION -> WorldEvent.WorldEventType.POLITICAL_MANEUVER;
                case ARTISANAL_CREATION -> WorldEvent.WorldEventType.ARTISTIC_CREATION;
                default -> WorldEvent.WorldEventType.NOVEL_BUSINESS_MODEL;
            };

            return new WorldEvent(
                    "novel_" + UUID.randomUUID().toString().substring(0, 8),
                    type, outcome + ": " + action.type().name(), narrative,
                    currentTick, outcome.equals("SUCCESS") ? 0.4 : 0.2, List.of(),
                    Map.of("outcome", outcome, "creditChange", creditChange,
                            "satisfactionChange", satisfactionChange, "agentId", action.agentId()));
        } catch (Exception e) {
            return adjudicateNovelActionDeterministic(action, currentTick);
        }
    }

    private List<WorldEvent> parseWorldEvents(String content, int currentTick) {
        try {
            String json = stripMarkdown(content);
            JsonNode root = MAPPER.readTree(json);
            JsonNode eventsNode = root.path("events");
            if (!eventsNode.isArray()) return List.of();
            List<WorldEvent> events = new ArrayList<>();
            for (JsonNode eventNode : eventsNode) {
                String typeStr = eventNode.path("type").asText("COHERENCE_CORRECTION");
                WorldEvent.WorldEventType type;
                try { type = WorldEvent.WorldEventType.valueOf(typeStr); }
                catch (IllegalArgumentException e) { type = WorldEvent.WorldEventType.COHERENCE_CORRECTION; }
                events.add(new WorldEvent(
                        "world_" + UUID.randomUUID().toString().substring(0, 8),
                        type, eventNode.path("name").asText("World Event"),
                        eventNode.path("description").asText("Something happened."),
                        currentTick, eventNode.path("severity").asDouble(0.3),
                        List.of(), Map.of()));
            }
            return events;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<WorldEvent> parseCoherenceResponse(String content, int currentTick) {
        try {
            String json = stripMarkdown(content);
            JsonNode root = MAPPER.readTree(json);
            if (root.path("coherent").asBoolean(true)) return List.of();
            List<WorldEvent> corrections = new ArrayList<>();
            JsonNode correctionsNode = root.path("corrections");
            if (correctionsNode.isArray()) {
                for (JsonNode c : correctionsNode) {
                    corrections.add(new WorldEvent(
                            "coherence_" + UUID.randomUUID().toString().substring(0, 8),
                            WorldEvent.WorldEventType.COHERENCE_CORRECTION,
                            c.path("name").asText("Coherence Correction"),
                            c.path("description").asText(""),
                            currentTick, c.path("severity").asDouble(0.2),
                            List.of(), Map.of()));
                }
            }
            return corrections;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String stripMarkdown(String content) {
        String s = content.trim();
        if (s.startsWith("```")) s = s.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        return s;
    }

    private static InfrastructureEffectType parseEffectType(String s) {
        if (s == null || s.equals("null") || s.isEmpty()) return null;
        try { return InfrastructureEffectType.valueOf(s); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Map<ItemType, Integer> parseItemMap(JsonNode array) {
        Map<ItemType, Integer> result = new LinkedHashMap<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array)
                result.put(ItemType.custom(item.path("type").asText()), item.path("amount").asInt(1));
        }
        return result;
    }
}
