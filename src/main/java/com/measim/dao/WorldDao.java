package com.measim.dao;

import com.measim.model.world.*;

import java.util.List;

public interface WorldDao {
    void initialize(HexGrid grid);
    HexGrid getGrid();
    Tile getTile(HexCoord coord);
    boolean inBounds(HexCoord coord);
    List<Tile> getNeighborTiles(HexCoord coord);
    List<Tile> getTilesInRange(HexCoord center, int radius);
    List<Tile> getAllTiles();
    List<Tile> getSettlementZones();
}
