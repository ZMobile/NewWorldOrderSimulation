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
 *   1. Consume goods (creates demand — FOOD required, others boost satisfaction)
 *   2. Extract resources from current tile (gain inventory, not credits)
 *   3. Produce goods if inputs available (transform inventory, not credits)
 *   4. Sell excess to market (offer to other agents)
 *   5. Buy what you need from market (food, production inputs)
 *   6. Execute strategic action (move, research, robot purchase, etc.)
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
    private final SimulationConfig config;
    private SubsistenceHelper subsistenceHelper;

    @Inject
    public ActionExecutionPhase(DecisionPhase decisionPhase, AgentDao agentDao, WorldDao worldDao,
                                 MarketDao marketDao, ProductionChainDao chainDao,
                                 ProductionService productionService, EnvironmentService environmentService,
                                 GameMasterService gameMasterService, InfrastructureService infrastructureService,
                                 AgentServiceManager agentServiceManager, PropertyService propertyService,
                                 ContractService contractService, PropertyDao propertyDao,
                                 ContractDao contractDao, SimulationConfig config) {
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
                // Incapacitated: can only extract and buy food (survival mode)
                autoExtract(agent);
                autoBuy(agent, market, currentTick);
                continue;
            }
            autoExtract(agent);
            autoProduce(agent, currentTick);
            autoSell(agent, market, currentTick);
            autoBuy(agent, market, currentTick);
            autoLaborMarket(agent, currentTick);
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
        for (var entry : localActions) {
            executeStrategicAction(entry.getKey(), entry.getValue(), market, currentTick);
        }

        // Execute GM actions concurrently in batches
        if (!gmActions.isEmpty()) {
            System.out.printf("    [Action] %d GM evaluations needed, processing concurrently...%n", gmActions.size());
            int batchSize = 5;
            for (int i = 0; i < gmActions.size(); i += batchSize) {
                int end = Math.min(i + batchSize, gmActions.size());
                var batch = gmActions.subList(i, end);

                var futures = batch.stream()
                        .map(entry -> java.util.concurrent.CompletableFuture.runAsync(() ->
                                executeStrategicAction(entry.getKey(), entry.getValue(), market, currentTick)))
                        .toArray(java.util.concurrent.CompletableFuture[]::new);

                java.util.concurrent.CompletableFuture.allOf(futures).join();
            }
        }

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

    // ====== SELLING (offer goods for credits) ======

    private void autoSell(Agent agent, MarketDao.MarketData market, int currentTick) {
        // Sell finished products — these are what consumers need
        for (ProductType productType : ProductType.values()) {
            ItemType item = ItemType.of(productType);
            int count = agent.state().getInventoryCount(item);
            int keepForSelf = (productType == ProductType.FOOD) ? 3 : 0; // keep some food
            if (count > keepForSelf) {
                int toSell = count - keepForSelf;
                agent.state().removeFromInventory(item, toSell);
                double askPrice = productBasePrice(productType);
                market.submitOrder(new Order(agent.id(), item, toSell, askPrice,
                        Order.OrderSide.SELL, currentTick));
            }
        }

        // Sell excess raw resources (keep some for own production)
        for (ResourceType resourceType : ResourceType.values()) {
            ItemType item = ItemType.of(resourceType);
            int count = agent.state().getInventoryCount(item);
            if (count > 5) {
                int toSell = count - 5;
                agent.state().removeFromInventory(item, toSell);
                market.submitOrder(new Order(agent.id(), item, toSell, 2.0,
                        Order.OrderSide.SELL, currentTick));
            }
        }
    }

    // ====== BUYING (spend credits for goods you need) ======

    private void autoBuy(Agent agent, MarketDao.MarketData market, int currentTick) {
        AgentState state = agent.state();

        // Priority 1: Buy FOOD if running low (everyone needs to eat)
        ItemType food = ItemType.of(ProductType.FOOD);
        if (state.getInventoryCount(food) < 3 && state.credits() > 5) {
            int need = 3 - state.getInventoryCount(food);
            market.submitOrder(new Order(agent.id(), food, need,
                    productBasePrice(ProductType.FOOD) * 1.3, // willing to pay premium
                    Order.OrderSide.BUY, currentTick));
        }

        // Priority 2: Buy BASIC_GOODS for satisfaction
        ItemType goods = ItemType.of(ProductType.BASIC_GOODS);
        if (state.getInventoryCount(goods) < 1 && state.credits() > 20) {
            market.submitOrder(new Order(agent.id(), goods, 1,
                    productBasePrice(ProductType.BASIC_GOODS) * 1.2,
                    Order.OrderSide.BUY, currentTick));
        }

        // Priority 3: Buy MEDICINE if satisfaction is low
        if (state.satisfaction() < 0.35 && state.credits() > 15) {
            ItemType medicine = ItemType.of(ProductType.MEDICINE);
            if (state.getInventoryCount(medicine) < 1) {
                market.submitOrder(new Order(agent.id(), medicine, 1,
                        productBasePrice(ProductType.MEDICINE) * 1.2,
                        Order.OrderSide.BUY, currentTick));
            }
        }

        // Priority 4: Buy raw resources for production chains
        for (ProductionChain chain : chainDao.getAllDiscovered()) {
            for (var input : chain.inputs().entrySet()) {
                int have = state.getInventoryCount(input.getKey());
                if (have < input.getValue() && state.credits() > 10) {
                    int need = input.getValue() - have;
                    market.submitOrder(new Order(agent.id(), input.getKey(), need, 3.0,
                            Order.OrderSide.BUY, currentTick));
                    return; // one buy order for production inputs per tick
                }
            }
        }
    }

    private double productBasePrice(ProductType type) {
        return switch (type) {
            case FOOD -> 3.0;
            case BASIC_GOODS -> 5.0;
            case CONSTRUCTION -> 8.0;
            case TECHNOLOGY -> 15.0;
            case MEDICINE -> 12.0;
            case LUXURY -> 25.0;
        };
    }

    // ====== LABOR MARKET ======

    private void autoLaborMarket(Agent agent, int currentTick) {
        var profile = agent.identity();
        var state = agent.state();

        // Hire only when you have something that NEEDS workers:
        // infrastructure to operate, services to staff, or robots to manage
        boolean hasBusinessNeed = !propertyService.getAgentProperties(agent.id()).isEmpty()
                || state.ownedRobots() > 0
                || state.employmentStatus() == EmploymentStatus.BUSINESS_OWNER;

        if (hasBusinessNeed && state.credits() > 200) {
            var existingWorkers = contractService.getWorkRelationsOf(agent.id());
            int currentWorkers = existingWorkers.size();

            // Hire if: have infrastructure/robots but no workers yet
            int workersNeeded = Math.max(1, state.ownedRobots()) - currentWorkers;
            if (workersNeeded > 0) {
                // Find unemployed agents nearby
                for (Agent candidate : agentDao.getAllAgents()) {
                    if (candidate.id().equals(agent.id())) continue;
                    if (candidate.state().employmentStatus() != EmploymentStatus.UNEMPLOYED) continue;
                    if (candidate.state().location().distanceTo(state.location()) > 10) continue;

                    // Offer wages based on what we can afford
                    double wages = Math.min(state.credits() * 0.02, 20);
                    if (wages < 3) break; // can't afford workers

                    contractService.createContract(
                            com.measim.model.contract.Contract.ContractType.WORK_RELATION,
                            agent.id(), candidate.id(), wages, 12, currentTick,
                            java.util.Map.of("hoursPerTick", 1.0, "laborWeight", 1.0));

                    agent.addMemory(new MemoryEntry(currentTick, "CONTRACT",
                            "Hired " + candidate.name() + " at " + String.format("%.1f", wages) + "/tick",
                            0.6, candidate.id(), 0));
                    break; // one hire per tick
                }
            }
        }

        // Workers without employment actively seek work
        if (state.employmentStatus() == EmploymentStatus.UNEMPLOYED
                && (profile.archetype() == Archetype.WORKER || profile.ambition() > 0.3)) {
            // Worker archetype is actively looking — this is handled by the hiring loop above
            // But we can make them move toward employment opportunities
        }
    }

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
            case AgentAction.PlaceBuyOrder buy -> {
                if (agent.state().credits() > buy.maxPrice() * buy.quantity()) {
                    market.submitOrder(new Order(agent.id(), buy.itemType(), buy.quantity(),
                            buy.maxPrice(), Order.OrderSide.BUY, currentTick));
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
            case AgentAction.ExtractResource ignored -> {}
            case AgentAction.Produce ignored -> {}
            case AgentAction.PlaceSellOrder ignored -> {}
            case AgentAction.ProposeGovernance ignored -> {}
            case AgentAction.Vote ignored -> {}
            case AgentAction.Migrate ignored -> {}
            case AgentAction.CreateBusiness ignored -> {}
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
