package com.measim.dao;

import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.infrastructure.InfrastructureEffect;
import com.measim.model.infrastructure.InfrastructureType;
import com.measim.model.world.HexCoord;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class InfrastructureDaoImpl implements InfrastructureDao {

    private final Map<String, Infrastructure> instances = new LinkedHashMap<>();
    private final Map<String, InfrastructureType> types = new LinkedHashMap<>();

    // Spatial indices for fast lookup
    private final Map<HexCoord, List<String>> byLocation = new HashMap<>();
    private final Map<HexCoord, List<String>> byConnection = new HashMap<>();

    @Override
    public void place(Infrastructure infra) {
        instances.put(infra.id(), infra);
        byLocation.computeIfAbsent(infra.location(), k -> new ArrayList<>()).add(infra.id());
        if (infra.connectedTo() != null) {
            byConnection.computeIfAbsent(infra.connectedTo(), k -> new ArrayList<>()).add(infra.id());
        }
    }

    @Override
    public Optional<Infrastructure> getById(String id) { return Optional.ofNullable(instances.get(id)); }

    @Override
    public List<Infrastructure> getAtTile(HexCoord coord) {
        return lookupIds(byLocation.getOrDefault(coord, List.of()));
    }

    @Override
    public List<Infrastructure> getByOwner(String agentId) {
        return instances.values().stream().filter(i -> i.ownerId().equals(agentId)).toList();
    }

    @Override
    public List<Infrastructure> getAll() { return List.copyOf(instances.values()); }

    @Override
    public List<Infrastructure> getAllActive() {
        return instances.values().stream().filter(Infrastructure::isActive).toList();
    }

    @Override
    public List<Infrastructure> getConnectionsTo(HexCoord tile) {
        // Infrastructure whose location IS this tile (resources flow TO here)
        return getAtTile(tile).stream()
                .filter(i -> i.isPointToPoint() && i.isActive())
                .toList();
    }

    @Override
    public List<Infrastructure> getConnectionsFrom(HexCoord tile) {
        // Infrastructure that connects FROM this tile to somewhere else
        return lookupIds(byConnection.getOrDefault(tile, List.of())).stream()
                .filter(Infrastructure::isActive)
                .toList();
    }

    @Override
    public List<HexCoord> getResourceSourcesFor(HexCoord tile) {
        // All tiles that have active infrastructure transporting resources TO this tile
        return getConnectionsTo(tile).stream()
                .filter(i -> i.type().effects().stream()
                        .anyMatch(e -> e.type() == InfrastructureEffect.EffectType.RESOURCE_TRANSPORT))
                .map(Infrastructure::connectedTo)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public double getEffectMagnitudeAt(HexCoord tile, InfrastructureEffect.EffectType effectType) {
        double total = 0;

        // Effects from infrastructure ON this tile
        for (Infrastructure infra : getAtTile(tile)) {
            if (!infra.isActive()) continue;
            for (var effect : infra.type().effects()) {
                if (effect.type() == effectType) {
                    total += infra.effectiveMagnitude(effect);
                }
            }
        }

        // Area-of-effect infrastructure on nearby tiles
        for (Infrastructure infra : getAllActive()) {
            if (infra.isAreaOfEffect() && infra.location().distanceTo(tile) <= infra.type().maxRange()) {
                for (var effect : infra.type().effects()) {
                    if (effect.type() == effectType) {
                        // Falloff by distance
                        int dist = infra.location().distanceTo(tile);
                        double falloff = dist == 0 ? 1.0 : 1.0 / (1.0 + dist * 0.3);
                        total += infra.effectiveMagnitude(effect) * falloff;
                    }
                }
            }
        }

        return total;
    }

    @Override
    public void registerType(InfrastructureType type) { types.put(type.id(), type); }

    @Override
    public Optional<InfrastructureType> getType(String typeId) { return Optional.ofNullable(types.get(typeId)); }

    @Override
    public List<InfrastructureType> getAllTypes() { return List.copyOf(types.values()); }

    private List<Infrastructure> lookupIds(List<String> ids) {
        return ids.stream()
                .map(instances::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
