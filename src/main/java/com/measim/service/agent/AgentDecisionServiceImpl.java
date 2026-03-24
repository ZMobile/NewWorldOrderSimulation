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
    private final com.measim.service.trade.TradeService tradeService;
    private final SimulationConfig config;

    @Inject
    public AgentDecisionServiceImpl(WorldDao worldDao, AgentDao agentDao,
                                     ProductionChainDao chainDao,
                                     InfrastructureDao infraDao, RiskDao riskDao,
                                     ServiceDao serviceDao,
                                     com.measim.service.property.PropertyService propertyService,
                                     com.measim.service.trade.CommunicationRangeService commRange,
                                     com.measim.service.trade.TradeService tradeService,
                                     SimulationConfig config) {
        this.worldDao = worldDao;
        this.agentDao = agentDao;
        this.chainDao = chainDao;
        this.infraDao = infraDao;
        this.riskDao = riskDao;
        this.serviceDao = serviceDao;
        this.propertyService = propertyService;
        this.commRange = commRange;
        this.tradeService = tradeService;
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

        // No autoBuy — all commerce is agent-to-agent via LLM negotiation.

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

        // Trade (offer/accept/reject) is LLM-only — agents negotiate as their archetype.
        // Tier 1 does NOT generate trade actions. A Free Rider might refuse fair deals,
        // an Exploiter might accept bad deals to build trust, a Philanthropist might give
        // things away. Only LLM reasoning can capture these personality-driven decisions.

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
