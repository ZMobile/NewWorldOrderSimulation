package com.measim.dao;

import com.measim.model.world.*;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class WorldDaoImpl implements WorldDao {

    private HexGrid grid;

    @Override
    public void initialize(HexGrid grid) { this.grid = grid; }

    @Override
    public HexGrid getGrid() { return grid; }

    @Override
    public Tile getTile(HexCoord coord) { return grid.getTile(coord); }

    @Override
    public boolean inBounds(HexCoord coord) { return grid.inBounds(coord); }

    @Override
    public List<Tile> getNeighborTiles(HexCoord coord) { return grid.getNeighborTiles(coord); }

    @Override
    public List<Tile> getTilesInRange(HexCoord center, int radius) { return grid.getTilesInRange(center, radius); }

    @Override
    public List<Tile> getAllTiles() { return grid.getAllTiles(); }

    @Override
    public List<Tile> getSettlementZones() { return grid.getSettlementZones(); }
}
