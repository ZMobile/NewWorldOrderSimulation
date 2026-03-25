package com.measim.service.simulation.phases;

import com.measim.dao.*;
import com.measim.model.agent.*;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.*;
import com.measim.model.gamemaster.NovelAction;
import com.measim.model.world.*;
import com.measim.model.service.ServiceProposal;
import com.measim.service.agentservice.AgentServiceManager;
import com.measim.service.contract.ContractService;
import com.measim.service.economy.ProductionService;
import com.measim.service.gamemaster.GameMasterService;
import com.measim.service.infrastructure.InfrastructureService;
import com.measim.service.property.PropertyService;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.EnvironmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

/**
 * Phase 3: Realistic economic pipeline.
 *
 * No credit injection from nowhere. Credits only enter agent accounts via:
 *   - Market sales (another agent pays you)
 *   - UBI distribution (from the pool funded by MEAS modifiers)
 *   - Starting capital
 *
 * Each tick per agent:
 *   1. Consume goods (subsistence — FOOD required, others boost satisfaction)
 *   2. Extract resources from tile (physics)
 *   3. Produce goods from inputs (mechanics)
 *   4. Execute strategic action (move, research, robot purchase, trade, etc.)
 *   5. Interaction rounds (agent conversations, negotiations, deal closure)
 */
@Singleton
public class ActionExecutionPhase implements TickPhase {
    private final DecisionPhase decisionPhase;
    private final AgentDao agentDao;
    private final WorldDao worldDao;
    private final MarketDao marketDao;
    private final ProductionChainDao chainDao;
    private final ProductionService productionService;
    private final EnvironmentService environmentService;
    private final GameMasterService gameMasterService;
    private final InfrastructureService infrastructureService;
    private final AgentServiceManager agentServiceManager;
    private final PropertyService propertyService;
    private final ContractService contractService;
    private final com.measim.service.trade.TradeService tradeService;
    private final com.measim.service.trade.CommunicationRangeService commRange;

    // Pending job/contract offers — maps "offererAgentId:targetAgentId" to offer details
    record PendingJobOffer(String offererId, String targetId, double wagesPerTick, int durationTicks, String description, int tick) {}
    record PendingContractOffer(String proposerId, String targetId, String contractType, double valuePerTick, int durationTicks, String terms, int tick) {}
    private final java.util.concurrent.ConcurrentHashMap<String, PendingJobOffer> pendingJobOffers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, PendingContractOffer> pendingContractOffers = new java.util.concurrent.ConcurrentHashMap<>();
    private final com.measim.service.communication.CommunicationService commService;
    private final com.measim.service.economy.CreditFlowService creditFlowService;
    private final SimulationConfig config;
    private SubsistenceHelper subsistenceHelper;

    @Inject
    public ActionExecutionPhase(DecisionPhase decisionPhase, AgentDao agentDao, WorldDao worldDao,
                                 MarketDao marketDao, ProductionChainDao chainDao,
                                 ProductionService productionService, EnvironmentService environmentService,
                                 GameMasterService gameMasterService, InfrastructureService infrastructureService,
                                 AgentServiceManager agentServiceManager, PropertyService propertyService,
                                 ContractService contractService, com.measim.service.trade.TradeService tradeService,
                                 com.measim.service.trade.CommunicationRangeService commRange,
                                 com.measim.service.communication.CommunicationService commService,
                                 com.measim.service.economy.CreditFlowService creditFlowService,
                                 PropertyDao propertyDao, ContractDao contractDao, SimulationConfig config) {
        this.decisionPhase = decisionPhase;
        this.agentDao = agentDao;
        this.worldDao = worldDao;
        this.marketDao = marketDao;
        this.chainDao = chainDao;
        this.productionService = productionService;
        this.environmentService = environmentService;
        this.gameMasterService = gameMasterService;
        this.infrastructureService = infrastructureService;
        this.agentServiceManager = agentServiceManager;
        this.propertyService = propertyService;
        this.contractService = contractService;
        this.tradeService = tradeService;
        this.commRange = commRange;
        this.commService = commService;
        this.creditFlowService = creditFlowService;
        this.config = config;
        this.subsistenceHelper = new SubsistenceHelper(worldDao, propertyDao, contractDao);
    }

    @Override public String name() { return "Action Execution"; }
    @Override public int order() { return 30; }

    @Override
    public void execute(int currentTick) {
        MarketDao.MarketData market = marketDao.getAllMarkets().iterator().next();

        // Phase A: Subsistence + deterministic economic pipeline (fast, no LLM)
        Set<String> incapacitatedAgents = new HashSet<>();
        for (Agent agent : agentDao.getAllAgents()) {
            boolean incapacitated = subsistenceHelper.processNeeds(agent, currentTick);
            if (incapacitated) {
                incapacitatedAgents.add(agent.id());
                // Incapacitated: can only extract (survival mode)
                autoExtract(agent);
                continue;
            }
            autoExtract(agent);
            autoProduce(agent, currentTick);
            // No autoSell/autoBuy/autoLaborMarket — all inter-agent commerce is LLM-only.
            // Agents must negotiate trades, sales, purchases, and hiring themselves.
        }

        // Phase B: Collect all strategic actions that need GM evaluation
        List<java.util.Map.Entry<Agent, AgentAction>> gmActions = new java.util.ArrayList<>();
        List<java.util.Map.Entry<Agent, AgentAction>> localActions = new java.util.ArrayList<>();

        for (Agent agent : agentDao.getAllAgents()) {
            AgentAction action = decisionPhase.pendingActions().get(agent.id());
            if (action == null || action instanceof AgentAction.Idle) continue;

            if (incapacitatedAgents.contains(agent.id())) continue; // survival mode only

            if (action instanceof AgentAction.BuildInfrastructure
                    || action instanceof AgentAction.CreateService
                    || action instanceof AgentAction.FreeFormAction) {
                gmActions.add(java.util.Map.entry(agent, action));
            } else {
                localActions.add(java.util.Map.entry(agent, action));
            }
        }

        // Execute local actions immediately (no LLM)
        System.out.printf("    [Action] %d local actions, %d GM actions queued%n",
                localActions.size(), gmActions.size());
        System.out.flush();
        for (var entry : localActions) {
            executeStrategicAction(entry.getKey(), entry.getValue(), market, currentTick);
        }

        // Execute ALL GM actions concurrently with dedicated thread pool
        // (avoids ForkJoinPool starvation from nested .join() calls)
        if (!gmActions.isEmpty()) {
            System.out.printf("    [Action] %d GM evaluations, firing all concurrently...%n", gmActions.size());
            System.out.flush();

            var executor = java.util.concurrent.Executors.newFixedThreadPool(
                    Math.min(gmActions.size(), 50));
            try {
                var futures = gmActions.stream()
                        .map(entry -> java.util.concurrent.CompletableFuture.runAsync(() ->
                                executeStrategicAction(entry.getKey(), entry.getValue(), market, currentTick), executor))
                        .toArray(java.util.concurrent.CompletableFuture[]::new);

                java.util.concurrent.CompletableFuture.allOf(futures).join();
            } finally {
                executor.shutdown();
            }
        }

        // Phase B.5: Interaction rounds — agents respond to messages, offers, negotiate
        // Actions execute BETWEEN rounds so agents see each other's responses
        decisionPhase.runInteractionRounds(currentTick, 4, (agentId, action) -> {
            Agent interactionAgent = agentDao.getAgent(agentId);
            if (interactionAgent != null && !incapacitatedAgents.contains(agentId)) {
                executeStrategicAction(interactionAgent, action, market, currentTick);
            }
        });

        // Phase B.6: Process trade offers (expire old, clean up)
        tradeService.processTrades(currentTick);

        // Phase C: Novel actions — periodic GM interaction for non-incapacitated agents.
        for (Agent agent : agentDao.getAllAgents()) {
            if (incapacitatedAgents.contains(agent.id())) continue;
            if (currentTick % 6 == 0 && shouldTriggerNovelAction(agent, currentTick)) {
                double stake = Math.min(agent.state().credits() * 0.03, 200);
                if (stake > 10) {
                    gameMasterService.submitNovelAction(new NovelAction(
                            agent.id(), agent.identity().archetype().name(),
                            mapArchetypeToNovelAction(agent.identity().archetype()),
                            generateNovelActionDescription(agent), stake, currentTick));
                    agent.state().spendCredits(stake);
                }
            }
        }
    }

    // Consumption is now handled by SubsistenceHelper

    // ====== EXTRACTION (inventory gain, no credits) ======

    private void autoExtract(Agent agent) {
        HexCoord loc = agent.state().location();
        Tile tile = worldDao.getTile(loc);
        if (tile == null) return;

        double extractionMultiplier = infrastructureService.getExtractionMultiplier(loc);

        agent.state().addExperience("extraction");
        tile.history().recordAgentVisit();

        // Public resource extraction fee: extracting from unclaimed public land costs a small fee
        // that flows to UBI pool. Agents with very low credits are exempt (subsistence foraging).
        boolean ownsThisTile = propertyService.getAgentProperties(agent.id()).stream()
                .anyMatch(c -> c.tile().equals(loc));
        if (!ownsThisTile && agent.state().credits() > 50 && !tile.resources().isEmpty()) {
            double fee = 0.5; // small royalty for public resource use
            if (agent.state().spendCredits(fee)) {
                creditFlowService.addPublicRevenue(fee, "extraction_royalty");
            }
        }

        // Extract from local tile
        for (ResourceNode resource : tile.resources()) {
            if (!resource.isDepleted()) {
                double amount = Math.min(2.0 * extractionMultiplier, resource.abundance());
                double extracted = resource.extract(amount);
                if (extracted > 0) {
                    agent.state().addToInventory(ItemType.of(resource.type()), (int) Math.ceil(extracted));
                }
            }
        }

        // Extract from infrastructure-connected remote tiles (pipelines, aqueducts, etc.)
        Map<ItemType, Double> remoteResources = infrastructureService.getAccessibleResources(loc);
        for (var entry : remoteResources.entrySet()) {
            // Only take remote resources that aren't already from the local tile
            boolean isLocal = tile.resources().stream()
                    .anyMatch(r -> ItemType.of(r.type()).equals(entry.getKey()) && !r.isDepleted());
            if (!isLocal && entry.getValue() > 0) {
                int qty = (int) Math.min(entry.getValue(), 3.0); // capped by transport capacity
                if (qty > 0) {
                    agent.state().addToInventory(entry.getKey(), qty);
                }
            }
        }
    }

    // ====== PRODUCTION (transforms inventory, no credits) ======

    private void autoProduce(Agent agent, int currentTick) {
        double robotMultiplier = agent.state().ownedRobots() > 0
                ? config.robotInitialEfficiency() + agent.state().ownedRobots() * 0.05
                : 1.0;

        for (ProductionChain chain : chainDao.getAllDiscovered()) {
            Map<ItemType, Integer> inv = new HashMap<>(agent.state().inventory());
            var result = productionService.execute(chain, inv, robotMultiplier);
            if (result.success()) {
                for (var entry : chain.inputs().entrySet())
                    agent.state().removeFromInventory(entry.getKey(), entry.getValue());
                for (var entry : result.produced().entrySet())
                    agent.state().addToInventory(entry.getKey(), entry.getValue());

                agent.state().addEmissions(result.pollutionGenerated());
                environmentService.applyProductionPollution(agent.state().location(), result.pollutionGenerated());
                Tile prodTile = worldDao.getTile(agent.state().location());
                if (prodTile != null) {
                    prodTile.history().recordProduction();
                    prodTile.history().recordPollution(result.pollutionGenerated());
                }
                agent.state().setHumanEmployees(agent.state().ownedRobots() > 0 ? 0 : 1);
                agent.state().addExperience("production:" + chain.id());
                agent.state().recordSuccess("production:" + chain.id());
                agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                        "Produced: " + chain.name(), 0.5, null, 0));
                break; // one production per tick
            }
        }
    }

    // autoSell, autoBuy, autoLaborMarket REMOVED — all inter-agent commerce is LLM-only.
    // Agents negotiate trades, purchases, sales, and hiring through their archetype reasoning.

    // ====== STRATEGIC ACTION ======

    private void executeStrategicAction(Agent agent, AgentAction action,
                                         MarketDao.MarketData market, int currentTick) {
        switch (action) {
            case AgentAction.Idle ignored -> {}
            case AgentAction.Move move -> {
                if (worldDao.inBounds(move.destination())) {
                    Tile dest = worldDao.getTile(move.destination());
                    if (dest != null && dest.terrain().isPassable())
                        agent.state().setLocation(move.destination());
                }
            }
            case AgentAction.PurchaseRobot ignored -> {
                if (agent.state().spendCredits(config.robotInitialCost())) {
                    agent.state().setOwnedRobots(agent.state().ownedRobots() + 1);
                    agent.state().addExperience("automation");
                    agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                            "Purchased robot #" + agent.state().ownedRobots(), 0.7, null, -config.robotInitialCost()));
                }
            }
            case AgentAction.InvestResearch research -> {
                if (agent.state().spendCredits(research.creditInvestment())) {
                    gameMasterService.submitResearch(agent.id(), research.direction(),
                            research.creditInvestment(), currentTick);
                    agent.state().addExperience("research:" + research.direction());
                }
            }
            case AgentAction.ContributeCommons contrib -> {
                if (agent.state().spendCredits(contrib.creditInvestment())) {
                    agent.state().setCommonsScore(Math.min(1.0, agent.state().commonsScore() + 0.05));
                    agent.state().addExperience("commons");
                }
            }
            case AgentAction.BuildInfrastructure buildInfra -> {
                // Must own a property claim on the tile to build
                var loc = buildInfra.location() != null ? buildInfra.location() : agent.state().location();
                var existingClaims = propertyService.getAgentProperties(agent.id());
                boolean hasClaim = existingClaims.stream().anyMatch(c -> c.tile().equals(loc));

                if (!hasClaim) {
                    // Auto-purchase a claim if affordable
                    var claimOpt = propertyService.purchaseClaim(agent.id(), loc, 1, currentTick);
                    hasClaim = claimOpt.isPresent();
                    if (claimOpt.isPresent()) {
                        agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                                "Purchased property claim at " + loc, 0.4, null,
                                -propertyService.getClaimBasePrice(loc)));
                    }
                }

                if (hasClaim) {
                    var proposal = new com.measim.model.gamemaster.InfrastructureProposal(
                            agent.id(), buildInfra.typeId(),
                            "Agent-proposed infrastructure",
                            "Available local materials",
                            loc, buildInfra.connectTo(),
                            "Transport resources from connected tile",
                            Math.min(agent.state().credits() * 0.3, 1000));

                    var typeOpt = gameMasterService.evaluateInfrastructureProposal(proposal, currentTick);
                    if (typeOpt.isPresent()) {
                        var result = infrastructureService.build(agent.id(), typeOpt.get().id(),
                                loc, buildInfra.connectTo(), currentTick);
                        if (result.success()) {
                            agent.state().addExperience("infrastructure");
                            agent.state().recordSuccess("infrastructure");
                            agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                                    "Built " + result.infrastructure().type().name(),
                                    0.8, null, -result.infrastructure().type().constructionCost()));
                        }
                    }
                }
            }
            case AgentAction.CreateService cs -> {
                var proposal = new ServiceProposal(agent.id(), cs.name(), cs.description(),
                        cs.category(), "All agents in range", "Market rate",
                        "Credits and time", cs.location(), cs.budget());
                var created = agentServiceManager.proposeService(proposal, currentTick);
                if (created.isPresent()) {
                    agent.state().addExperience("service:" + cs.category());
                    agent.state().recordSuccess("service:" + cs.category());
                }
            }
            case AgentAction.ConsumeService consume -> {
                agentServiceManager.consumeService(agent.id(), consume.serviceInstanceId(), currentTick);
            }
            case AgentAction.FreeFormAction freeForm -> {
                var resolution = gameMasterService.resolveFreeFormAction(
                        agent.id(), freeForm.description(), freeForm.creditBudget(), currentTick);
                if (resolution.success()) {
                    agent.state().spendCredits(resolution.creditCost());
                    agent.state().addCredits(resolution.creditGain());
                    agent.state().setSatisfaction(agent.state().satisfaction() + resolution.satisfactionChange());
                    for (var entry : resolution.inventoryChanges().entrySet()) {
                        if (entry.getValue() > 0) agent.state().addToInventory(
                                com.measim.model.economy.ItemType.custom(entry.getKey()), entry.getValue());
                        else agent.state().removeFromInventory(
                                com.measim.model.economy.ItemType.custom(entry.getKey()), -entry.getValue());
                    }
                    agent.state().addExperience(resolution.experienceDomain());
                    agent.state().recordSuccess(resolution.experienceDomain());
                    agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                            resolution.narrative(), 0.7, null, resolution.creditGain() - resolution.creditCost()));
                } else {
                    agent.state().spendCredits(resolution.creditCost());
                    agent.state().addExperience(resolution.experienceDomain());
                    agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                            "Failed: " + resolution.narrative(), 0.5, null, -resolution.creditCost()));
                }
            }
            case AgentAction.OfferTrade offer -> {
                tradeService.createOffer(agent.id(), offer.targetAgentId(),
                        offer.itemsOffered(), offer.itemsRequested(),
                        offer.creditsOffered(), offer.creditsRequested(),
                        offer.message(), currentTick);
            }
            case AgentAction.AcceptTrade accept -> {
                tradeService.acceptOffer(accept.offerId(), agent.id(), currentTick);
            }
            case AgentAction.RejectTrade reject -> {
                tradeService.rejectOffer(reject.offerId());
            }
            case AgentAction.SendMessage msg -> {
                // Auto-detect verbal acceptance: if message says "accept" and there's a pending
                // job/contract offer from the target, convert to mechanical acceptance
                String lowerContent = msg.content().toLowerCase();
                if (lowerContent.contains("accept") && (lowerContent.contains("offer") || lowerContent.contains("job")
                        || lowerContent.contains("contract") || lowerContent.contains("deal")
                        || lowerContent.contains("terms") || lowerContent.contains("agree"))) {
                    // Check for pending job offer from this target
                    String jobKey = msg.targetAgentId() + ":" + agent.id();
                    if (pendingJobOffers.containsKey(jobKey)) {
                        executeStrategicAction(agent, new AgentAction.AcceptJob(msg.targetAgentId()), market, currentTick);
                        break;
                    }
                    // Check for pending contract offer
                    for (var key : pendingContractOffers.keySet()) {
                        if (key.startsWith(msg.targetAgentId() + ":" + agent.id() + ":")) {
                            String contractType = key.substring(key.lastIndexOf(':') + 1);
                            executeStrategicAction(agent, new AgentAction.AcceptContract(msg.targetAgentId(), contractType), market, currentTick);
                            break;
                        }
                    }
                }

                // Private message — only delivered if within comm range
                var target = agentDao.getAgent(msg.targetAgentId());
                if (target != null && commRange.canCommunicate(agent, target)) {
                    commService.sendAgentMessage(agent.id(), msg.targetAgentId(),
                            msg.content(), com.measim.model.communication.Message.MessageType.SOCIAL, currentTick);
                    agent.addMemory(new MemoryEntry(currentTick, "MESSAGE",
                            "Sent to " + msg.targetAgentId() + ": " + msg.content().substring(0, Math.min(60, msg.content().length())),
                            0.3, msg.targetAgentId(), 0));
                }
            }
            case AgentAction.BroadcastMessage broadcast -> {
                commService.sendAgentMessage(agent.id(), "ALL_AT_TILE",
                        broadcast.content(), com.measim.model.communication.Message.MessageType.INFORMATION_SHARE, currentTick);
                agent.addMemory(new MemoryEntry(currentTick, "BROADCAST",
                        "Said: " + broadcast.content().substring(0, Math.min(60, broadcast.content().length())),
                        0.3, null, 0));
            }
            case AgentAction.OfferJob offer -> {
                var target = agentDao.getAgent(offer.targetAgentId());
                if (target != null && commRange.canCommunicate(agent, target)) {
                    // Store pending offer with actual terms
                    String key = agent.id() + ":" + offer.targetAgentId();
                    pendingJobOffers.put(key, new PendingJobOffer(
                            agent.id(), offer.targetAgentId(), offer.wagesPerTick(),
                            offer.durationTicks(), offer.description(), currentTick));

                    String offerMsg = String.format("JOB_OFFER from %s: %.1f credits/tick for %d ticks. %s. Use {\"action\":\"ACCEPT_JOB\",\"offererAgent\":\"%s\"} to accept.",
                            agent.id(), offer.wagesPerTick(), offer.durationTicks(), offer.description(), agent.id());
                    commService.sendAgentMessage(agent.id(), offer.targetAgentId(),
                            offerMsg, com.measim.model.communication.Message.MessageType.TRADE_PROPOSAL, currentTick);
                    agent.addMemory(new MemoryEntry(currentTick, "JOB_OFFER",
                            "Offered job to " + offer.targetAgentId() + " at " + String.format("%.1f", offer.wagesPerTick()) + "/tick",
                            0.5, offer.targetAgentId(), 0));
                }
            }
            case AgentAction.AcceptJob accept -> {
                var offerer = agentDao.getAgent(accept.offererAgentId());
                if (offerer != null && commRange.canCommunicate(agent, offerer)) {
                    // Look up the actual pending offer terms
                    String key = accept.offererAgentId() + ":" + agent.id();
                    PendingJobOffer pending = pendingJobOffers.remove(key);

                    // Remove reverse offer if both offered (prevent duplicate)
                    pendingJobOffers.remove(agent.id() + ":" + accept.offererAgentId());

                    // Check for existing work relation (prevent duplicates)
                    boolean alreadyWorking = contractService.getAgentContracts(agent.id()).stream()
                            .anyMatch(c -> c.isActive() && c.type() == com.measim.model.contract.Contract.ContractType.WORK_RELATION
                                    && (c.partyAId().equals(accept.offererAgentId()) || c.partyBId().equals(accept.offererAgentId())));
                    if (alreadyWorking) {
                        agent.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                                "Already have work relation with " + accept.offererAgentId(), 0.3, null, 0));
                        break;
                    }
                    double wages = pending != null ? pending.wagesPerTick() : 5.0;
                    int duration = pending != null ? pending.durationTicks() : 12;

                    contractService.createContract(
                            com.measim.model.contract.Contract.ContractType.WORK_RELATION,
                            accept.offererAgentId(), agent.id(), wages, duration, currentTick,
                            java.util.Map.of("hoursPerTick", 1.0, "laborWeight", 1.0));
                    commService.sendAgentMessage(agent.id(), accept.offererAgentId(),
                            "ACCEPTED job offer: " + wages + " credits/tick for " + duration + " ticks. Contract created.",
                            com.measim.model.communication.Message.MessageType.TRADE_RESPONSE, currentTick);
                    agent.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                            "Accepted job from " + accept.offererAgentId() + " at " + wages + "/tick",
                            0.6, accept.offererAgentId(), 0));
                    offerer.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                            agent.id() + " accepted job at " + wages + "/tick",
                            0.6, agent.id(), 0));
                }
            }
            case AgentAction.ProposeContract proposal -> {
                var target = agentDao.getAgent(proposal.targetAgentId());
                if (target != null && commRange.canCommunicate(agent, target)) {
                    // Store pending contract with actual terms
                    String key = agent.id() + ":" + proposal.targetAgentId() + ":" + proposal.contractType();
                    pendingContractOffers.put(key, new PendingContractOffer(
                            agent.id(), proposal.targetAgentId(), proposal.contractType(),
                            proposal.valuePerTick(), proposal.durationTicks(), proposal.terms(), currentTick));

                    String propMsg = String.format("CONTRACT_PROPOSAL from %s: %s, %.1f credits/tick, %d ticks. Terms: %s. Use {\"action\":\"ACCEPT_CONTRACT\",\"proposerAgent\":\"%s\",\"contractType\":\"%s\"} to accept.",
                            agent.id(), proposal.contractType(), proposal.valuePerTick(),
                            proposal.durationTicks(), proposal.terms(), agent.id(), proposal.contractType());
                    commService.sendAgentMessage(agent.id(), proposal.targetAgentId(),
                            propMsg, com.measim.model.communication.Message.MessageType.TRADE_PROPOSAL, currentTick);
                    agent.addMemory(new MemoryEntry(currentTick, "CONTRACT_PROPOSAL",
                            "Proposed " + proposal.contractType() + " to " + proposal.targetAgentId(),
                            0.5, proposal.targetAgentId(), 0));
                }
            }
            case AgentAction.AcceptContract accept -> {
                var proposer = agentDao.getAgent(accept.proposerAgentId());
                if (proposer != null && commRange.canCommunicate(agent, proposer)) {
                    // Look up actual pending offer terms
                    String key = accept.proposerAgentId() + ":" + agent.id() + ":" + accept.contractType();
                    PendingContractOffer pending = pendingContractOffers.remove(key);

                    // Also remove the reverse proposal if both proposed (prevent duplicate contract)
                    String reverseKey = agent.id() + ":" + accept.proposerAgentId() + ":" + accept.contractType();
                    pendingContractOffers.remove(reverseKey);

                    // Check for existing active contract between this pair (prevent duplicates)
                    boolean alreadyContracted = contractService.getAgentContracts(agent.id()).stream()
                            .anyMatch(c -> c.isActive() &&
                                    (c.partyAId().equals(accept.proposerAgentId()) || c.partyBId().equals(accept.proposerAgentId())));
                    if (alreadyContracted) {
                        agent.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                                "Already have active contract with " + accept.proposerAgentId(), 0.3, null, 0));
                        break;
                    }

                    double value = pending != null ? pending.valuePerTick() : 5.0;
                    int duration = pending != null ? pending.durationTicks() : 12;

                    var type = switch (accept.contractType().toUpperCase()) {
                        case "WORK_RELATION" -> com.measim.model.contract.Contract.ContractType.WORK_RELATION;
                        case "RENTAL" -> com.measim.model.contract.Contract.ContractType.RENTAL;
                        case "SERVICE" -> com.measim.model.contract.Contract.ContractType.SERVICE_SUBSCRIPTION;
                        default -> com.measim.model.contract.Contract.ContractType.PARTNERSHIP;
                    };
                    contractService.createContract(type,
                            accept.proposerAgentId(), agent.id(), value, duration, currentTick,
                            java.util.Map.of());
                    commService.sendAgentMessage(agent.id(), accept.proposerAgentId(),
                            "ACCEPTED " + accept.contractType() + ": " + value + " credits/tick for " + duration + " ticks. Contract created.",
                            com.measim.model.communication.Message.MessageType.TRADE_RESPONSE, currentTick);
                    agent.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                            "Accepted " + accept.contractType() + " from " + accept.proposerAgentId() + " at " + value + "/tick",
                            0.6, accept.proposerAgentId(), 0));
                    proposer.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                            agent.id() + " accepted " + accept.contractType() + " at " + value + "/tick",
                            0.6, agent.id(), 0));
                }
            }
            case AgentAction.TerminateContract terminate -> {
                contractService.terminateContract(terminate.contractId(), agent.id(), currentTick);
                agent.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                        "Terminated contract " + terminate.contractId() + ": " + terminate.reason(),
                        0.5, null, 0));
            }
            case AgentAction.ClaimProperty claim -> {
                // Nature GM quick check: physically possible?
                // - Must be within 2 tiles (can't claim across map)
                // - Slots must be available
                // - Agent must have credits for the price
                // Nature GM does NOT judge suitability — agents are free to make bad choices
                int distance = agent.state().location().distanceTo(claim.tile());
                if (distance > 2) {
                    agent.addMemory(new MemoryEntry(currentTick, "PROPERTY",
                            "Cannot claim (" + claim.tile().q() + "," + claim.tile().r() + "): too far (distance " + distance + ")",
                            0.3, null, 0));
                    break;
                }
                int available = propertyService.availableSlots(claim.tile());
                if (available <= 0) {
                    agent.addMemory(new MemoryEntry(currentTick, "PROPERTY",
                            "Cannot claim (" + claim.tile().q() + "," + claim.tile().r() + "): no slots available",
                            0.3, null, 0));
                    break;
                }
                // Governance GM sets price — based on tile value (resources, settlement, demand)
                double price = propertyService.getClaimBasePrice(claim.tile());
                if (agent.state().credits() < price) {
                    agent.addMemory(new MemoryEntry(currentTick, "PROPERTY",
                            "Cannot afford claim at (" + claim.tile().q() + "," + claim.tile().r() + "): " +
                            "price " + String.format("%.0f", price) + ", have " + String.format("%.0f", agent.state().credits()),
                            0.4, null, 0));
                    break;
                }
                var claimResult = propertyService.purchaseClaim(agent.id(), claim.tile(), 1, currentTick);
                if (claimResult.isPresent()) {
                    agent.addMemory(new MemoryEntry(currentTick, "PROPERTY",
                            "Claimed property at (" + claim.tile().q() + "," + claim.tile().r() + ") for " +
                            String.format("%.0f", price) + " credits",
                            0.7, null, -price));
                    commService.sendAgentMessage(agent.id(), "ALL_AT_TILE",
                            "Registered property claim at (" + claim.tile().q() + "," + claim.tile().r() + ")",
                            com.measim.model.communication.Message.MessageType.INFORMATION_SHARE, currentTick);
                }
            }
        }
    }

    // ====== NOVEL ACTIONS ======

    private boolean shouldTriggerNovelAction(Agent agent, int currentTick) {
        var profile = agent.identity();
        return switch (profile.archetype()) {
            case EXPLOITER -> profile.complianceDisposition() < 0.3 && agent.state().credits() > 200;
            case POLITICIAN -> currentTick % 12 == 0;
            case ARTISAN -> profile.creativity() > 0.4 && agent.state().credits() > 300;
            case PHILANTHROPIST -> profile.altruism() > 0.7 && agent.state().credits() > 2000;
            case ACCUMULATOR -> agent.state().credits() > 5000;
            case AUTOMATOR -> agent.state().ownedRobots() > 2;
            case OPTIMIZER -> agent.state().credits() > 3000;
            case FREE_RIDER -> agent.state().satisfaction() < 0.3;
            case REGULATOR -> currentTick % 12 == 0;
            case SPECULATOR -> agent.state().credits() > 500;
            case LANDLORD -> agent.state().credits() > 400;
            case PROVIDER -> agent.state().credits() > 300 && currentTick % 6 == 0;
            case ORGANIZER -> currentTick % 12 == 0;
            default -> false;
        };
    }

    private NovelAction.NovelActionType mapArchetypeToNovelAction(Archetype archetype) {
        return switch (archetype) {
            case EXPLOITER -> NovelAction.NovelActionType.SYSTEM_GAMING;
            case POLITICIAN -> NovelAction.NovelActionType.POLITICAL_CAMPAIGN;
            case ARTISAN -> NovelAction.NovelActionType.ARTISANAL_CREATION;
            case PHILANTHROPIST -> NovelAction.NovelActionType.PUBLIC_WORKS;
            case ACCUMULATOR -> NovelAction.NovelActionType.FINANCIAL_INNOVATION;
            case AUTOMATOR -> NovelAction.NovelActionType.NOVEL_AUTOMATION;
            case OPTIMIZER -> NovelAction.NovelActionType.OPERATIONAL_RESTRUCTURE;
            case FREE_RIDER -> NovelAction.NovelActionType.UBI_EXPLOITATION;
            case REGULATOR -> NovelAction.NovelActionType.REGULATORY_INNOVATION;
            case ENTREPRENEUR -> NovelAction.NovelActionType.NOVEL_BUSINESS;
            case INNOVATOR -> NovelAction.NovelActionType.SPECULATIVE_RESEARCH;
            case COOPERATOR -> NovelAction.NovelActionType.PUBLIC_WORKS;
            case WORKER -> NovelAction.NovelActionType.OPERATIONAL_RESTRUCTURE;
            case SPECULATOR -> NovelAction.NovelActionType.FINANCIAL_INNOVATION;
            case HOMESTEADER -> NovelAction.NovelActionType.NOVEL_BUSINESS;
            case PROVIDER -> NovelAction.NovelActionType.NOVEL_BUSINESS;
            case LANDLORD -> NovelAction.NovelActionType.FINANCIAL_INNOVATION;
            case ORGANIZER -> NovelAction.NovelActionType.POLITICAL_CAMPAIGN;
        };
    }

    private String generateNovelActionDescription(Agent agent) {
        return switch (agent.identity().archetype()) {
            case EXPLOITER -> "Looking for scoring loopholes";
            case POLITICIAN -> "Building coalition to influence governance";
            case ARTISAN -> "Creating a unique handcrafted product";
            case PHILANTHROPIST -> "Funding community infrastructure";
            case ACCUMULATOR -> "Developing new financial instrument";
            case AUTOMATOR -> "Designing novel robot deployment";
            case OPTIMIZER -> "Restructuring operations for efficiency";
            case FREE_RIDER -> "Finding ways to maximize UBI benefits";
            case REGULATOR -> "Proposing new compliance framework";
            case ENTREPRENEUR -> "Launching innovative business model";
            case INNOVATOR -> "Pursuing speculative cross-domain research";
            case COOPERATOR -> "Organizing community mutual aid";
            case WORKER -> "Negotiating better work terms";
            case SPECULATOR -> "Trading property claims for profit";
            case HOMESTEADER -> "Expanding self-sufficient operations";
            case PROVIDER -> "Launching new service for agents";
            case LANDLORD -> "Acquiring and developing property";
            case ORGANIZER -> "Forming multi-agent alliance";
        };
    }
}
