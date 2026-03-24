package com.measim.service.agent;

import com.measim.dao.*;
import com.measim.model.agent.*;
import com.measim.model.config.SimulationConfig;
import com.measim.model.economy.*;
import com.measim.model.risk.PerceivedRisk;
import com.measim.model.service.AgentServiceType;
import com.measim.model.service.ServiceInstance;
import com.measim.model.world.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class AgentDecisionServiceImpl implements AgentDecisionService {

    private final WorldDao worldDao;
    private final AgentDao agentDao;
    private final ProductionChainDao chainDao;
    private final InfrastructureDao infraDao;
    private final RiskDao riskDao;
    private final ServiceDao serviceDao;
    private final com.measim.service.property.PropertyService propertyService;
    private final com.measim.service.trade.CommunicationRangeService commRange;
    private final SimulationConfig config;

    @Inject
    public AgentDecisionServiceImpl(WorldDao worldDao, AgentDao agentDao,
                                     ProductionChainDao chainDao,
                                     InfrastructureDao infraDao, RiskDao riskDao,
                                     ServiceDao serviceDao,
                                     com.measim.service.property.PropertyService propertyService,
                                     com.measim.service.trade.CommunicationRangeService commRange,
                                     SimulationConfig config) {
        this.worldDao = worldDao;
        this.agentDao = agentDao;
        this.chainDao = chainDao;
        this.infraDao = infraDao;
        this.riskDao = riskDao;
        this.serviceDao = serviceDao;
        this.propertyService = propertyService;
        this.commRange = commRange;
        this.config = config;
    }

    @Override
    public AgentAction decideStrategicAction(Agent agent, Map<ItemType, Double> marketPrices, int currentTick) {
        IdentityProfile profile = agent.identity();
        AgentState state = agent.state();
        List<ScoredAction> candidates = new ArrayList<>();

        candidates.add(new ScoredAction(new AgentAction.Idle(), 1.0));

        // Move toward resources if current tile is barren
        Tile currentTile = worldDao.getTile(state.location());
        if (currentTile != null) {
            boolean hasUsableResources = currentTile.resources().stream().anyMatch(r -> !r.isDepleted());
            if (!hasUsableResources) {
                // Search neighbors for resource-rich tiles
                for (Tile neighbor : worldDao.getNeighborTiles(state.location())) {
                    if (neighbor.terrain().isPassable() && !neighbor.resources().isEmpty()) {
                        long resourceCount = neighbor.resources().stream().filter(r -> !r.isDepleted()).count();
                        candidates.add(new ScoredAction(
                                new AgentAction.Move(neighbor.coord()),
                                resourceCount * 10.0 + profile.ambition() * 5));
                    }
                }
                // If no neighbors have resources, look wider
                if (candidates.size() <= 1) {
                    for (Tile tile : worldDao.getTilesInRange(state.location(), 3)) {
                        if (tile.terrain().isPassable() && !tile.resources().isEmpty()
                                && !tile.coord().equals(state.location())) {
                            candidates.add(new ScoredAction(
                                    new AgentAction.Move(pickStepToward(state.location(), tile.coord())),
                                    5.0));
                            break;
                        }
                    }
                }
            }
        }

        // Buy resources needed for production chains we can almost complete
        for (ProductionChain chain : chainDao.getAllDiscovered()) {
            int missingTypes = 0;
            int totalMissing = 0;
            for (var input : chain.inputs().entrySet()) {
                int have = state.getInventoryCount(input.getKey());
                if (have < input.getValue()) {
                    missingTypes++;
                    totalMissing += input.getValue() - have;
                }
            }
            // Only buy if we're close to having enough (missing 1-2 types)
            if (missingTypes > 0 && missingTypes <= 2 && totalMissing <= 10) {
                for (var input : chain.inputs().entrySet()) {
                    int need = input.getValue() - state.getInventoryCount(input.getKey());
                    if (need > 0) {
                        double price = marketPrices.getOrDefault(input.getKey(), 1.0);
                        if (state.credits() > price * need * 1.5) {
                            double outputValue = chain.outputs().entrySet().stream()
                                    .mapToDouble(e -> marketPrices.getOrDefault(e.getKey(), 5.0) * e.getValue())
                                    .sum();
                            candidates.add(new ScoredAction(
                                    new AgentAction.PlaceBuyOrder(input.getKey(), need, price * 1.2),
                                    outputValue * 0.8));
                        }
                    }
                }
            }
        }

        // Infrastructure proposals:
        // PUBLIC (roads, trails, bridges between tiles): no property needed, GM approves as public works
        // PRIVATE (farms, mines, factories): REQUIRES owning property on that tile
        if (profile.ambition() > 0.4 && state.credits() > 300) {
            Tile current = worldDao.getTile(state.location());
            if (current != null) {
                var ownedClaims = propertyService.getAgentProperties(agent.id());
                boolean ownsCurrentTile = ownedClaims.stream()
                        .anyMatch(c -> c.tile().equals(state.location()));

                // PUBLIC: trails/roads between tiles (no property needed, lower utility = less common)
                if (current.isSettlementZone() && profile.altruism() > 0.3) {
                    for (Tile nearby : worldDao.getTilesInRange(state.location(), 8)) {
                        if (nearby.isSettlementZone() && !nearby.coord().equals(state.location())) {
                            boolean alreadyConnected = infraDao.getConnectionsTo(state.location()).stream()
                                    .anyMatch(i -> nearby.coord().equals(i.connectedTo()));
                            if (!alreadyConnected && state.credits() > 200) {
                                candidates.add(new ScoredAction(
                                        new AgentAction.BuildInfrastructure(
                                                "Public trail to settlement " + nearby.coord(),
                                                state.location(), nearby.coord()),
                                        profile.altruism() * 10 + 5));
                                break;
                            }
                        }
                    }
                }

                // PRIVATE: requires property ownership on the tile
                if (ownsCurrentTile && state.credits() > 400) {
                    // Connect to resource tiles
                    for (Tile nearby : worldDao.getTilesInRange(state.location(), 6)) {
                        if (!nearby.resources().isEmpty() && !nearby.coord().equals(state.location())) {
                            boolean alreadyConnected = infraDao.getConnectionsTo(state.location()).stream()
                                    .anyMatch(i -> nearby.coord().equals(i.connectedTo()));
                            if (!alreadyConnected) {
                                candidates.add(new ScoredAction(
                                        new AgentAction.BuildInfrastructure(
                                                "Private pipeline to " + nearby.coord(),
                                                state.location(), nearby.coord()),
                                        nearby.resources().size() * 15.0 + profile.ambition() * 10));
                                break;
                            }
                        }
                    }

                    // Local facility
                    if (infraDao.getAtTile(state.location()).isEmpty() && !current.resources().isEmpty()) {
                        candidates.add(new ScoredAction(
                                new AgentAction.BuildInfrastructure("Private extraction facility",
                                        state.location(), null),
                                profile.ambition() * 15));
                    }
                }
            }
        }

        // Purchase robots (risk-adjusted)
        double robotCost = config.robotInitialCost();
        if (profile.ambition() > 0.5 && state.credits() > robotCost * 1.5 && state.ownedRobots() < 10) {
            double robotUtility = profile.ambition() * 20
                    + (profile.archetype() == Archetype.AUTOMATOR ? 30 : 0);
            robotUtility *= riskDiscount(agent, "robot_operations");
            candidates.add(new ScoredAction(new AgentAction.PurchaseRobot(), robotUtility));
        }

        // Research (risk-adjusted — speculative research can fail)
        if (profile.creativity() > 0.4 && state.credits() > 1500) {
            double investment = state.credits() * 0.1;
            double researchUtility = profile.creativity() * 25 + profile.ambition() * 10;
            researchUtility *= riskDiscount(agent, "research");
            candidates.add(new ScoredAction(
                    new AgentAction.InvestResearch("general", investment), researchUtility));
        }

        // Commons contribution (altruistic agents)
        if (profile.altruism() > 0.6 && state.credits() > 800) {
            candidates.add(new ScoredAction(
                    new AgentAction.ContributeCommons("public goods", state.credits() * 0.05),
                    profile.altruism() * 20));
        }

        // Consume available services (strategic choice — agent picks the best one)
        if (state.credits() > 30) {
            var nearbyServices = serviceDao.getInstancesNear(state.location(), 10);
            for (ServiceInstance svc : nearbyServices) {
                if (!svc.isActive() || !svc.hasCapacity()) continue;
                if (state.credits() < svc.type().pricePerUse()) continue;

                double serviceUtility = switch (svc.type().category()) {
                    case HEALTHCARE -> state.satisfaction() < 0.4 ? 30 : 5;
                    case EDUCATION -> profile.creativity() * 15;
                    case FINANCIAL -> profile.ambition() > 0.6 ? 20 : 5;
                    case ENTERTAINMENT -> state.satisfaction() < 0.6 ? 15 : 3;
                    case MAINTENANCE -> 10;
                    default -> 5;
                } * svc.effectiveQuality();

                candidates.add(new ScoredAction(
                        new AgentAction.ConsumeService(svc.id()), serviceUtility));
                break; // consider one service per tick
            }
        }

        // Trade with nearby agents: offer surplus items for things we need
        int range = commRange.getEffectiveRange(agent);
        var nearbyAgents = agentDao.getAllAgents().stream()
                .filter(a -> !a.id().equals(agent.id()))
                .filter(a -> a.state().location().distanceTo(state.location()) <= range)
                .toList();

        if (!nearbyAgents.isEmpty()) {
            // What do I have surplus of?
            Map<ItemType, Integer> surplus = new HashMap<>();
            for (var entry : state.inventory().entrySet()) {
                if (entry.getValue() > 3) { // keep 3 of each for self
                    surplus.put(entry.getKey(), entry.getValue() - 3);
                }
            }

            // What do I need? (food is always high priority, then production inputs)
            ItemType foodItem = ItemType.of(com.measim.model.economy.ProductType.FOOD);
            boolean needFood = state.getInventoryCount(foodItem) < 2;

            for (Agent neighbor : nearbyAgents) {
                if (surplus.isEmpty()) break;

                // Check what neighbor has that I need
                if (needFood && neighbor.state().getInventoryCount(foodItem) > 3) {
                    // Offer my surplus for their food
                    for (var entry : surplus.entrySet()) {
                        if (entry.getValue() > 0 && !entry.getKey().equals(foodItem)) {
                            Map<ItemType, Integer> offering = Map.of(entry.getKey(), Math.min(2, entry.getValue()));
                            Map<ItemType, Integer> requesting = Map.of(foodItem, 2);
                            double tradeUtility = 25.0; // food is critical
                            candidates.add(new ScoredAction(
                                    new AgentAction.OfferTrade(neighbor.id(), offering, requesting,
                                            0, 0, "Trading " + entry.getKey() + " for FOOD"),
                                    tradeUtility));
                            break;
                        }
                    }
                }

                // Offer surplus resources for credits (basic commerce)
                if (!needFood && state.credits() < 500) {
                    for (var entry : surplus.entrySet()) {
                        if (entry.getValue() > 0 && neighbor.state().credits() > 20) {
                            Map<ItemType, Integer> offering = Map.of(entry.getKey(), Math.min(3, entry.getValue()));
                            double tradeUtility = 10.0 + profile.ambition() * 5;
                            candidates.add(new ScoredAction(
                                    new AgentAction.OfferTrade(neighbor.id(), offering, Map.of(),
                                            0, entry.getValue() * 3.0, // ask ~3 credits per item
                                            "Selling " + entry.getKey()),
                                    tradeUtility));
                            break;
                        }
                    }
                }
            }
        }

        return candidates.stream().max(Comparator.comparingDouble(ScoredAction::utility))
                .map(ScoredAction::action).orElse(new AgentAction.Idle());
    }

    /**
     * Risk discount factor: reduces utility of risky actions based on agent's PERCEIVED risk.
     * Agents don't see true risk — they estimate based on personality, experience, and info.
     *
     * Returns 0.0-1.0: 1.0 = no perceived risk, 0.1 = agent thinks this is very risky.
     * High risk-tolerance agents discount less. Cautious agents discount more.
     */
    private double riskDiscount(Agent agent, String targetEntityId) {
        IdentityProfile profile = agent.identity();

        // Check if agent has a perceived risk for this entity
        Optional<PerceivedRisk> perception = riskDao.getAgentPerceptionOf(agent.id(), targetEntityId);
        double perceivedProb;
        double perceivedSev;

        if (perception.isPresent()) {
            perceivedProb = perception.get().perceivedProbability();
            perceivedSev = perception.get().perceivedSeverity();
        } else {
            // No data — use personality-based intuition
            // High risk tolerance → assume low risk. High compliance → assume higher risk.
            perceivedProb = 0.05 * (1.0 + profile.complianceDisposition() - profile.riskTolerance());
            perceivedSev = 0.2 * (1.0 + profile.complianceDisposition() - profile.riskTolerance());
        }

        // Expected loss = probability × severity
        double expectedLoss = perceivedProb * perceivedSev;

        // Risk-tolerant agents discount less (they accept more risk)
        double riskAversion = 1.0 - profile.riskTolerance() * 0.7;

        return Math.max(0.1, 1.0 - expectedLoss * riskAversion * 3.0);
    }

    private HexCoord pickStepToward(HexCoord from, HexCoord to) {
        // Pick the neighbor of 'from' closest to 'to'
        return from.neighbors().stream()
                .filter(worldDao::inBounds)
                .filter(n -> { Tile t = worldDao.getTile(n); return t != null && t.terrain().isPassable(); })
                .min(Comparator.comparingInt(n -> n.distanceTo(to)))
                .orElse(from);
    }

    private record ScoredAction(AgentAction action, double utility) {}
}
