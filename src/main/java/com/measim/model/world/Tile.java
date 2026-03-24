package com.measim.model.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Tile {

    private final HexCoord coord;
    private final TerrainType terrain;
    private final TileEnvironment environment;
    private final TileHistory history;
    private final List<ResourceNode> resources;
    private final List<String> structureIds;
    private String governmentId;
    private boolean isSettlementZone;

    public Tile(HexCoord coord, TerrainType terrain) {
        this.coord = coord;
        this.terrain = terrain;
        this.environment = new TileEnvironment(terrain);
        this.history = new TileHistory();
        this.resources = new ArrayList<>();
        this.structureIds = new ArrayList<>();
    }

    public void addResource(ResourceNode resource) { resources.add(resource); }
    public void addStructure(String structureId) { structureIds.add(structureId); }
    public void removeStructure(String structureId) { structureIds.remove(structureId); }
    public boolean hasActiveProduction() { return !structureIds.isEmpty(); }

    public HexCoord coord() { return coord; }
    public TerrainType terrain() { return terrain; }
    public TileEnvironment environment() { return environment; }
    public TileHistory history() { return history; }
    public List<ResourceNode> resources() { return Collections.unmodifiableList(resources); }
    public List<String> structureIds() { return Collections.unmodifiableList(structureIds); }
    public String governmentId() { return governmentId; }
    public void setGovernmentId(String governmentId) { this.governmentId = governmentId; }
    public boolean isSettlementZone() { return isSettlementZone; }
    public void setSettlementZone(boolean settlementZone) { isSettlementZone = settlementZone; }
}
