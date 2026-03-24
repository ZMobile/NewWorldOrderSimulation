package com.measim.service.infrastructure;

import com.measim.dao.AgentDao;
import com.measim.dao.InfrastructureDao;
import com.measim.dao.WorldDao;
import com.measim.model.economy.ItemType;
import com.measim.model.infrastructure.*;
import com.measim.model.world.HexCoord;
import com.measim.model.world.ResourceNode;
import com.measim.model.world.Tile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class InfrastructureServiceImpl implements InfrastructureService {

    private final InfrastructureDao infraDao;
    private final WorldDao worldDao;
    private final AgentDao agentDao;

    @Inject
    public InfrastructureServiceImpl(InfrastructureDao infraDao, WorldDao worldDao, AgentDao agentDao) {
        this.infraDao = infraDao;
        this.worldDao = worldDao;
        this.agentDao = agentDao;
    }

    @Override
    public BuildResult build(String agentId, String typeId, HexCoord location,
                              HexCoord connectTo, int currentTick) {
        // Validate type exists
        var typeOpt = infraDao.getType(typeId);
        if (typeOpt.isEmpty()) return new BuildResult(false, "Unknown infrastructure type: " + typeId, null);
        InfrastructureType type = typeOpt.get();

        // Validate tile
        Tile tile = worldDao.getTile(location);
        if (tile == null) return new BuildResult(false, "Invalid location", null);

        // Terrain check
        if (!type.constraints().allowOnWater() && !tile.terrain().isPassable())
            return new BuildResult(false, "Cannot build on " + tile.terrain(), null);
        if (!type.constraints().allowedTerrains().contains(tile.terrain()))
            return new BuildResult(false, "Terrain " + tile.terrain() + " not allowed for " + type.name(), null);

        // Capacity check: total footprint on tile
        List<Infrastructure> existing = infraDao.getAtTile(location);
        int usedCapacity = existing.stream().mapToInt(i -> i.type().constraints().footprint()).sum();
        int maxCapacity = InfrastructureConstraints.terrainCapacity(tile.terrain());
        if (usedCapacity + type.constraints().footprint() > maxCapacity)
            return new BuildResult(false, "Tile at capacity (" + usedCapacity + "/" + maxCapacity + ")", null);

        // Compatibility check
        for (Infrastructure inf : existing) {
            if (type.constraints().incompatibleTypeIds().contains(inf.type().id()))
                return new BuildResult(false, "Incompatible with existing " + inf.type().name(), null);
        }

        // Range check for point-to-point
        if (type.connectionMode() == InfrastructureType.ConnectionMode.POINT_TO_POINT) {
            if (connectTo == null) return new BuildResult(false, "Point-to-point requires a connection target", null);
            if (location.distanceTo(connectTo) > type.maxRange())
                return new BuildResult(false, "Target too far: " + location.distanceTo(connectTo) + " > " + type.maxRange(), null);
        }

        // Agent can afford
        var agent = agentDao.getAgent(agentId);
        if (agent == null) return new BuildResult(false, "Agent not found", null);
        if (!agent.state().spendCredits(type.constructionCost()))
            return new BuildResult(false, "Cannot afford " + type.constructionCost() + " credits", null);

        // Build it — construction time proportional to cost
        // Cheap (<200): instant. Medium (200-500): 1-2 ticks. Expensive (500+): 3+ ticks.
        int constructionTicks = (int) Math.max(0, Math.floor(type.constructionCost() / 200.0) - 1);
        String id = "infra_" + UUID.randomUUID().toString().substring(0, 8);
        Infrastructure infra = new Infrastructure(id, type, agentId, location, connectTo, currentTick, constructionTicks);
        infraDao.place(infra);
        tile.addStructure(id);

        return new BuildResult(true, null, infra);
    }

    @Override
    public Map<ItemType, Double> getAccessibleResources(HexCoord tile) {
        Map<ItemType, Double> accessible = new HashMap<>();

        // Resources on the tile itself
        Tile localTile = worldDao.getTile(tile);
        if (localTile != null) {
            for (ResourceNode res : localTile.resources()) {
                if (!res.isDepleted()) {
                    accessible.merge(ItemType.of(res.type()), res.abundance(), Double::sum);
                }
            }
        }

        // Resources transported via infrastructure
        for (Infrastructure infra : infraDao.getConnectionsTo(tile)) {
            if (!infra.isActive() || infra.connectedTo() == null) continue;
            Tile sourceTile = worldDao.getTile(infra.connectedTo());
            if (sourceTile == null) continue;

            double stackingEff = InfrastructureConstraints.stackingEfficiency(
                    infraDao.getAtTile(tile).size());

            for (InfrastructureEffect effect : infra.type().effects()) {
                if (effect.type() == InfrastructureEffect.EffectType.RESOURCE_TRANSPORT) {
                    double capacity = infra.effectiveMagnitude(effect) * stackingEff;
                    // Transport from source: either all resources or specific type
                    for (ResourceNode res : sourceTile.resources()) {
                        if (res.isDepleted()) continue;
                        if (effect.targetResourceId() != null
                                && !res.type().name().equals(effect.targetResourceId())) continue;
                        double transported = Math.min(capacity, res.abundance());
                        accessible.merge(ItemType.of(res.type()), transported, Double::sum);
                    }
                }
            }
        }
        return accessible;
    }

    @Override
    public double getExtractionMultiplier(HexCoord tile) {
        double boost = infraDao.getEffectMagnitudeAt(tile, InfrastructureEffect.EffectType.EXTRACTION_BOOST);
        return boost > 0 ? boost : 1.0;
    }

    @Override
    public double getProductionSpeedMultiplier(HexCoord tile) {
        double boost = infraDao.getEffectMagnitudeAt(tile, InfrastructureEffect.EffectType.PRODUCTION_SPEED_BOOST);
        return boost > 0 ? boost : 1.0;
    }

    @Override
    public double getTradeCostMultiplier(HexCoord from, HexCoord to) {
        // Check for trade route infrastructure between the tiles
        double reduction = 0;
        for (Infrastructure infra : infraDao.getAllActive()) {
            if (!infra.isPointToPoint()) continue;
            // Check if this infra connects from/to in either direction
            boolean connects = (infra.location().equals(from) && to.equals(infra.connectedTo()))
                    || (infra.location().equals(to) && from.equals(infra.connectedTo()));
            if (connects) {
                for (var effect : infra.type().effects()) {
                    if (effect.type() == InfrastructureEffect.EffectType.TRADE_COST_REDUCTION) {
                        reduction += infra.effectiveMagnitude(effect);
                    }
                }
            }
        }
        return Math.max(0.2, 1.0 - reduction); // Floor at 20% of base cost
    }

    @Override
    public double getPollutionReduction(HexCoord tile) {
        return infraDao.getEffectMagnitudeAt(tile, InfrastructureEffect.EffectType.POLLUTION_REDUCTION);
    }

    @Override
    public double getRemediationBoost(HexCoord tile) {
        return infraDao.getEffectMagnitudeAt(tile, InfrastructureEffect.EffectType.ENVIRONMENTAL_REMEDIATION);
    }

    @Override
    public void tickMaintenance(int currentTick) {
        // Check construction completion for all infrastructure
        for (Infrastructure infra : infraDao.getAll()) {
            infra.checkConstruction(currentTick);
        }

        for (Infrastructure infra : infraDao.getAllActive()) {
            var agent = agentDao.getAgent(infra.ownerId());
            boolean canPay = agent != null && agent.state().credits() >= infra.type().maintenanceCostPerTick();
            double cost = infra.tickMaintenance(canPay);
            if (canPay && cost > 0 && agent != null) {
                agent.state().spendCredits(cost);
            }

            // Environmental pressure from infrastructure
            Tile tile = worldDao.getTile(infra.location());
            if (tile != null && infra.isActive()) {
                tile.environment().applyPollution(infra.type().constraints().environmentalPressurePerTick());
            }
        }
    }

    @Override
    public void registerCustomType(InfrastructureType type) {
        infraDao.registerType(type);
    }

    @Override
    public List<InfrastructureType> getAvailableTypes() {
        return infraDao.getAllTypes();
    }
}
