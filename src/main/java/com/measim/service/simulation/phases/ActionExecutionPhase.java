package com.measim.service.simulation.phases;

import com.measim.dao.*;
import com.measim.model.agent.*;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.*;
import com.measim.model.gamemaster.NovelAction;
import com.measim.model.world.*;
import com.measim.model.service.ServiceProposal;
import com.measim.service.agentservice.AgentServiceManager;
import com.measim.service.economy.ProductionService;
import com.measim.service.gamemaster.GameMasterService;
import com.measim.service.infrastructure.InfrastructureService;
import com.measim.service.simulation.TickPhase;
import com.measim.service.world.EnvironmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

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
    private final SimulationConfig config;

    @Inject
    public ActionExecutionPhase(DecisionPhase decisionPhase, AgentDao agentDao, WorldDao worldDao,
                                 MarketDao marketDao, ProductionChainDao chainDao,
                                 ProductionService productionService, EnvironmentService environmentService,
                                 GameMasterService gameMasterService, InfrastructureService infrastructureService,
                                 AgentServiceManager agentServiceManager, SimulationConfig config) {
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
        this.config = config;
    }

    @Override public String name() { return "Action Execution"; }
    @Override public int order() { return 30; }

    @Override
    public void execute(int currentTick) {
        MarketDao.MarketData market = marketDao.getAllMarkets().iterator().next();

        for (Agent agent : agentDao.getAllAgents()) {

            // Step 1: CONSUME — agents need goods to survive and be satisfied.
            // This is what creates demand and makes the economy circulate.
            consume(agent);

            // Step 2: EXTRACT — gather raw resources from the tile you're on.
            // You get inventory, NOT credits. Resources have no value until sold.
            autoExtract(agent);

            // Step 3: PRODUCE — transform resources into products if you can.
            // Products are more valuable than inputs, but only when sold.
            autoProduce(agent, currentTick);

            // Step 4: SELL — offer products and excess resources to the market.
            autoSell(agent, market, currentTick);

            // Step 5: BUY — place buy orders for food and production inputs.
            autoBuy(agent, market, currentTick);

            // Step 6: STRATEGIC ACTION — the one deliberate choice per tick.
            AgentAction action = decisionPhase.pendingActions().get(agent.id());
            if (action != null) {
                executeStrategicAction(agent, action, market, currentTick);
            }

            // Step 7: NOVEL ACTIONS — periodic GM interaction for all archetypes.
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

    // ====== CONSUMPTION (creates demand) ======

    private void consume(Agent agent) {
        AgentState state = agent.state();
        ItemType food = ItemType.of(ProductType.FOOD);
        ItemType goods = ItemType.of(ProductType.BASIC_GOODS);
        ItemType medicine = ItemType.of(ProductType.MEDICINE);

        // FOOD is required: consume 1 per tick. No food = satisfaction drops.
        if (state.getInventoryCount(food) > 0) {
            state.removeFromInventory(food, 1);
            state.setSatisfaction(state.satisfaction() + 0.02); // fed
        } else {
            state.setSatisfaction(state.satisfaction() - 0.05); // hungry
        }

        // BASIC_GOODS are optional: consume 1 per 3 ticks for comfort.
        if (state.getInventoryCount(goods) > 0) {
            state.removeFromInventory(goods, 1);
            state.setSatisfaction(state.satisfaction() + 0.01);
        }

        // MEDICINE is optional: helps recover satisfaction if low.
        if (state.satisfaction() < 0.4 && state.getInventoryCount(medicine) > 0) {
            state.removeFromInventory(medicine, 1);
            state.setSatisfaction(state.satisfaction() + 0.05);
        }

        // Natural satisfaction decay toward neutral
        if (state.satisfaction() > 0.5) {
            state.setSatisfaction(state.satisfaction() - 0.005);
        }
    }

    // ====== EXTRACTION (inventory gain, no credits) ======

    private void autoExtract(Agent agent) {
        HexCoord loc = agent.state().location();
        Tile tile = worldDao.getTile(loc);
        if (tile == null) return;

        double extractionMultiplier = infrastructureService.getExtractionMultiplier(loc);

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
                agent.state().setHumanEmployees(agent.state().ownedRobots() > 0 ? 0 : 1);
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
                    agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                            "Purchased robot #" + agent.state().ownedRobots(), 0.7, null, -config.robotInitialCost()));
                }
            }
            case AgentAction.InvestResearch research -> {
                if (agent.state().spendCredits(research.creditInvestment())) {
                    gameMasterService.submitResearch(agent.id(), research.direction(),
                            research.creditInvestment(), currentTick);
                }
            }
            case AgentAction.ContributeCommons contrib -> {
                if (agent.state().spendCredits(contrib.creditInvestment())) {
                    agent.state().setCommonsScore(Math.min(1.0, agent.state().commonsScore() + 0.05));
                }
            }
            case AgentAction.BuildInfrastructure buildInfra -> {
                // Agent proposes infrastructure → GM evaluates → if approved, build it
                var proposal = new com.measim.model.gamemaster.InfrastructureProposal(
                        agent.id(), buildInfra.typeId(), // typeId used as proposed name here
                        "Agent-proposed infrastructure",
                        "Available local materials",
                        buildInfra.location(), buildInfra.connectTo(),
                        "Transport resources from connected tile",
                        Math.min(agent.state().credits() * 0.3, 1000));

                var typeOpt = gameMasterService.evaluateInfrastructureProposal(proposal, currentTick);
                if (typeOpt.isPresent()) {
                    var result = infrastructureService.build(agent.id(), typeOpt.get().id(),
                            buildInfra.location(), buildInfra.connectTo(), currentTick);
                    if (result.success()) {
                        agent.addMemory(new MemoryEntry(currentTick, "ACTION",
                                "Built " + result.infrastructure().type().name(),
                                0.8, null, -result.infrastructure().type().constructionCost()));
                    }
                }
            }
            case AgentAction.CreateService cs -> {
                var proposal = new ServiceProposal(agent.id(), cs.name(), cs.description(),
                        cs.category(), "All agents in range", "Market rate",
                        "Credits and time", cs.location(), cs.budget());
                agentServiceManager.proposeService(proposal, currentTick);
            }
            case AgentAction.ConsumeService consume -> {
                agentServiceManager.consumeService(agent.id(), consume.serviceInstanceId(), currentTick);
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
        };
    }
}
