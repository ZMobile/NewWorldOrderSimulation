package com.measim.service.agentservice;

import com.measim.dao.AgentDao;
import com.measim.dao.ServiceDao;
import com.measim.model.agent.Agent;
import com.measim.model.agent.MemoryEntry;
import com.measim.model.communication.Message;
import com.measim.model.service.*;
import com.measim.model.world.HexCoord;
import com.measim.service.communication.CommunicationService;
import com.measim.service.gamemaster.GameMasterService;
import com.measim.service.llm.LlmService;
import com.measim.model.llm.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class AgentServiceManagerImpl implements AgentServiceManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GM_ID = "GAME_MASTER";

    private final ServiceDao serviceDao;
    private final AgentDao agentDao;
    private final LlmService llmService;
    private final CommunicationService commService;

    @Inject
    public AgentServiceManagerImpl(ServiceDao serviceDao, AgentDao agentDao,
                                    LlmService llmService, CommunicationService commService) {
        this.serviceDao = serviceDao;
        this.agentDao = agentDao;
        this.llmService = llmService;
        this.commService = commService;
    }

    @Override
    public Optional<ServiceInstance> proposeService(ServiceProposal proposal, int currentTick) {
        Agent agent = agentDao.getAgent(proposal.agentId());
        if (agent == null || agent.state().credits() < proposal.creditBudget()) return Optional.empty();

        commService.logThought(proposal.agentId(),
                String.format("Proposing service: %s — %s for %s",
                        proposal.proposedName(), proposal.proposedDescription(), proposal.targetCustomers()),
                Message.Channel.AGENT_INTERNAL, currentTick);

        AgentServiceType type = evaluateProposal(proposal, currentTick);
        if (type == null) return Optional.empty();

        // Pay setup cost
        if (!agent.state().spendCredits(type.setupCost())) return Optional.empty();

        serviceDao.registerType(type);
        String instanceId = "svc_" + UUID.randomUUID().toString().substring(0, 8);
        ServiceInstance instance = new ServiceInstance(instanceId, type, proposal.agentId(),
                proposal.location(), currentTick);
        serviceDao.addInstance(instance);

        agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                "Launched service: " + type.name(), 0.8, null, -type.setupCost()));

        commService.logThought(GM_ID,
                String.format("Approved service: %s by %s — setup %.0f, price %.1f/use, capacity %d",
                        type.name(), proposal.agentId(), type.setupCost(), type.pricePerUse(), type.capacityPerTick()),
                Message.Channel.GM_INTERNAL, currentTick);

        return Optional.of(instance);
    }

    @Override
    public boolean consumeService(String consumerId, String serviceInstanceId, int currentTick) {
        Agent consumer = agentDao.getAgent(consumerId);
        var instanceOpt = serviceDao.getInstance(serviceInstanceId);
        if (consumer == null || instanceOpt.isEmpty()) return false;

        ServiceInstance instance = instanceOpt.get();
        if (!instance.isActive() || !instance.hasCapacity()) return false;

        double price = instance.type().pricePerUse();
        if (!consumer.state().spendCredits(price)) return false;

        // Transfer payment to owner
        Agent owner = agentDao.getAgent(instance.ownerId());
        if (owner != null) owner.state().addCredits(price);

        instance.addSubscriber(consumerId);
        instance.recordUse(price, instance.type().operatingCostPerTick() / Math.max(1, instance.type().capacityPerTick()));

        // Apply effects to consumer
        for (AgentServiceType.ServiceEffect effect : instance.type().effects()) {
            applyEffect(consumer, effect, instance.effectiveQuality());
        }

        // Reputation builds with successful use
        instance.updateReputation(0.01);
        return true;
    }

    @Override
    public void tickServices(int currentTick) {
        for (ServiceInstance instance : serviceDao.getActiveInstances()) {
            // Pay operating costs
            Agent owner = agentDao.getAgent(instance.ownerId());
            if (owner != null) {
                if (!owner.state().spendCredits(instance.type().operatingCostPerTick())) {
                    // Can't afford operating costs — service quality degrades
                    instance.updateReputation(-0.05);
                    if (instance.reputation() <= 0) {
                        instance.deactivate();
                        commService.logThought(GM_ID,
                                "Service " + instance.type().name() + " shut down — insolvent",
                                Message.Channel.GM_WORLD_NARRATION, currentTick);
                    }
                }
            }

            // Track revenue for MEAS scoring
            if (owner != null) {
                owner.state().addRevenue(instance.accumulatedRevenue());
            }

            // Reset per-tick capacity
            instance.clearSubscribersForTick();

            // Natural reputation decay (must maintain quality to keep reputation)
            instance.updateReputation(-0.002);
        }
    }

    @Override
    public List<ServiceInstance> findServices(AgentServiceType.ServiceCategory category,
                                               HexCoord nearLocation, int radius) {
        return serviceDao.getInstancesNear(nearLocation, radius).stream()
                .filter(i -> i.type().category() == category && i.hasCapacity())
                .sorted(Comparator.comparingDouble(ServiceInstance::effectiveQuality).reversed())
                .toList();
    }

    @Override
    public void shutdownService(String serviceId, int currentTick) {
        serviceDao.getInstance(serviceId).ifPresent(instance -> {
            instance.deactivate();
            commService.logThought(GM_ID,
                    "Service " + instance.type().name() + " shut down by owner",
                    Message.Channel.GM_WORLD_NARRATION, currentTick);
        });
    }

    // ====== GM EVALUATION ======

    private AgentServiceType evaluateProposal(ServiceProposal proposal, int currentTick) {
        if (!llmService.isAvailable()) {
            return evaluateDeterministic(proposal, currentTick);
        }

        try {
            String systemPrompt = """
                    You are the Game Master (physics engine/DM) evaluating an agent's proposal to create a SERVICE.
                    Services are intangible — they take credits and produce effects on consumers.
                    You evaluate feasibility and set numerical properties. You do NOT invent the service.
                    The agent proposes, you determine if it works and what it costs/produces.

                    Consider: Does the agent have sufficient capital? Is there market demand?
                    Does it require infrastructure to operate? What are the risks (bank run, malpractice, insolvency)?
                    What byproducts does it create (data collection, social disruption, noise, waste)?
                    Agent experience in this domain should improve quality evaluation.

                    Respond with JSON:
                    {
                      "feasible": true/false,
                      "reasoning": "Observable thought process",
                      "name": "Service name",
                      "description": "What it does",
                      "category": "FINANCIAL|LOGISTICS|HEALTHCARE|EDUCATION|LEGAL|SECURITY|INFORMATION|ENTERTAINMENT|MAINTENANCE|GOVERNANCE|CUSTOM",
                      "setupCost": N.N,
                      "operatingCostPerTick": N.N,
                      "pricePerUse": N.N,
                      "capacityPerTick": N,
                      "qualityScore": 0.0-1.0,
                      "effects": [
                        {"target": "CONSUMER_CREDITS|CONSUMER_SATISFACTION|CONSUMER_SKILL|CONSUMER_RISK|CONSUMER_PRODUCTION|CONSUMER_TRADE|CONSUMER_INFORMATION|INFRASTRUCTURE|CUSTOM", "property": "what changes", "magnitude": N.N, "durationTicks": N}
                      ],
                      "risks": [
                        {"name":"...","category":"ECONOMIC|OPERATIONAL|SOCIAL|...","baseProbability":0.01,"agingRate":0.01,"minSeverity":0.1,"maxSeverity":0.5,"canCascade":false}
                      ],
                      "byproducts": [
                        {"name":"...","type":"SOCIAL|NOISE|CUSTOM|...","visibility":"VISIBLE|DELAYED|HIDDEN","baseAmountPerTick":0.01}
                      ]
                    }
                    Only output JSON.
                    """;

            String userPrompt = String.format("""
                    Agent %s proposes a service:
                    Name: %s
                    Description: %s
                    Category: %s
                    Target customers: %s
                    Pricing model: %s
                    Required resources: %s
                    Budget: %.0f credits
                    Existing services in world: %d
                    """,
                    proposal.agentId(), proposal.proposedName(), proposal.proposedDescription(),
                    proposal.intendedCategory(), proposal.targetCustomers(), proposal.proposedPricing(),
                    proposal.requiredResources(), proposal.creditBudget(),
                    serviceDao.getActiveInstances().size());

            LlmResponse response = llmService.queryGameMaster(systemPrompt, userPrompt).join();
            return parseServiceType(response.content(), proposal);
        } catch (Exception e) {
            return evaluateDeterministic(proposal, currentTick);
        }
    }

    private AgentServiceType evaluateDeterministic(ServiceProposal proposal, int currentTick) {
        AgentServiceType.ServiceCategory category;
        try { category = AgentServiceType.ServiceCategory.valueOf(proposal.intendedCategory().toUpperCase()); }
        catch (Exception e) { category = AgentServiceType.ServiceCategory.CUSTOM; }

        double budgetFactor = Math.min(3.0, proposal.creditBudget() / 300.0);
        double setupCost = proposal.creditBudget() * 0.7;
        double operatingCost = setupCost * 0.02;
        double price = operatingCost * 2 + budgetFactor;
        int capacity = (int) (3 + budgetFactor * 2);

        // Default effect based on category
        AgentServiceType.ServiceEffect.EffectTarget target = switch (category) {
            case FINANCIAL -> AgentServiceType.ServiceEffect.EffectTarget.CONSUMER_CREDITS;
            case LOGISTICS -> AgentServiceType.ServiceEffect.EffectTarget.CONSUMER_TRADE;
            case HEALTHCARE -> AgentServiceType.ServiceEffect.EffectTarget.CONSUMER_SATISFACTION;
            case EDUCATION -> AgentServiceType.ServiceEffect.EffectTarget.CONSUMER_SKILL;
            case INFORMATION -> AgentServiceType.ServiceEffect.EffectTarget.CONSUMER_INFORMATION;
            case MAINTENANCE -> AgentServiceType.ServiceEffect.EffectTarget.INFRASTRUCTURE;
            case ENTERTAINMENT -> AgentServiceType.ServiceEffect.EffectTarget.CONSUMER_SATISFACTION;
            default -> AgentServiceType.ServiceEffect.EffectTarget.CUSTOM;
        };

        String id = "svc_type_" + UUID.randomUUID().toString().substring(0, 8);
        return new AgentServiceType(id, proposal.proposedName(), proposal.proposedDescription(),
                category, setupCost, operatingCost, price, capacity, 0.5 + budgetFactor * 0.15,
                Set.of(),
                List.of(new AgentServiceType.ServiceEffect(target, "default", budgetFactor * 0.3, 1)),
                List.of(), List.of(), Map.of());
    }

    private AgentServiceType parseServiceType(String content, ServiceProposal proposal) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode root = MAPPER.readTree(json);
            if (!root.path("feasible").asBoolean(false)) return null;

            AgentServiceType.ServiceCategory category;
            try { category = AgentServiceType.ServiceCategory.valueOf(root.path("category").asText("CUSTOM")); }
            catch (Exception e) { category = AgentServiceType.ServiceCategory.CUSTOM; }

            List<AgentServiceType.ServiceEffect> effects = new ArrayList<>();
            JsonNode effectsNode = root.path("effects");
            if (effectsNode.isArray()) {
                for (JsonNode e : effectsNode) {
                    AgentServiceType.ServiceEffect.EffectTarget target;
                    try { target = AgentServiceType.ServiceEffect.EffectTarget.valueOf(e.path("target").asText("CUSTOM")); }
                    catch (Exception ex) { target = AgentServiceType.ServiceEffect.EffectTarget.CUSTOM; }
                    effects.add(new AgentServiceType.ServiceEffect(target,
                            e.path("property").asText("default"),
                            e.path("magnitude").asDouble(1.0),
                            e.path("durationTicks").asInt(1)));
                }
            }

            String id = "svc_type_" + UUID.randomUUID().toString().substring(0, 8);
            return new AgentServiceType(id,
                    root.path("name").asText(proposal.proposedName()),
                    root.path("description").asText(proposal.proposedDescription()),
                    category,
                    root.path("setupCost").asDouble(proposal.creditBudget() * 0.7),
                    root.path("operatingCostPerTick").asDouble(5),
                    root.path("pricePerUse").asDouble(3),
                    root.path("capacityPerTick").asInt(5),
                    root.path("qualityScore").asDouble(0.5),
                    Set.of(), effects,
                    List.of(), List.of(), Map.of());
        } catch (Exception e) {
            return evaluateDeterministic(proposal, 0);
        }
    }

    private void applyEffect(Agent consumer, AgentServiceType.ServiceEffect effect, double quality) {
        double scaledMagnitude = effect.magnitude() * quality;
        switch (effect.target()) {
            case CONSUMER_SATISFACTION -> consumer.state().setSatisfaction(
                    consumer.state().satisfaction() + scaledMagnitude * 0.1);
            case CONSUMER_CREDITS -> consumer.state().addCredits(scaledMagnitude);
            case CONSUMER_RISK -> {} // reduces perceived risk — handled by risk system
            case CONSUMER_INFORMATION -> {} // improves market knowledge — handled by decision service
            default -> {} // other effects tracked but applied by specific systems
        }
    }
}
