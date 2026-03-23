package com.measim.dao;

import com.measim.model.infrastructure.Infrastructure;
import com.measim.model.infrastructure.InfrastructureEffect;
import com.measim.model.infrastructure.InfrastructureType;
import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Optional;

public interface InfrastructureDao {

    // Infrastructure instances
    void place(Infrastructure infrastructure);
    Optional<Infrastructure> getById(String id);
    List<Infrastructure> getAtTile(HexCoord coord);
    List<Infrastructure> getByOwner(String agentId);
    List<Infrastructure> getAll();
    List<Infrastructure> getAllActive();

    // Connectivity queries
    List<Infrastructure> getConnectionsTo(HexCoord tile);
    List<Infrastructure> getConnectionsFrom(HexCoord tile);
    List<HexCoord> getResourceSourcesFor(HexCoord tile);
    double getEffectMagnitudeAt(HexCoord tile, InfrastructureEffect.EffectType effectType);

    // Infrastructure type registry (predefined + GM custom)
    void registerType(InfrastructureType type);
    Optional<InfrastructureType> getType(String typeId);
    List<InfrastructureType> getAllTypes();
}
