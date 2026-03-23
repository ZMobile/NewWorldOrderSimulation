package com.measim.model.world;

import java.util.*;
import java.util.stream.Collectors;

public class HexGrid {

    private final int width;
    private final int height;
    private final Tile[][] tiles;

    public HexGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width][height];
    }

    public void setTile(int q, int r, Tile tile) {
        if (inBounds(q, r)) tiles[q][r] = tile;
    }

    public Tile getTile(HexCoord coord) { return getTile(coord.q(), coord.r()); }

    public Tile getTile(int q, int r) {
        if (!inBounds(q, r)) return null;
        return tiles[q][r];
    }

    public boolean inBounds(int q, int r) { return q >= 0 && q < width && r >= 0 && r < height; }
    public boolean inBounds(HexCoord coord) { return inBounds(coord.q(), coord.r()); }

    public List<Tile> getNeighborTiles(HexCoord coord) {
        return coord.neighbors().stream()
                .filter(this::inBounds).map(this::getTile).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Tile> getTilesInRange(HexCoord center, int radius) {
        return center.range(radius).stream()
                .filter(this::inBounds).map(this::getTile).filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Tile> getAllTiles() {
        var result = new ArrayList<Tile>();
        for (int q = 0; q < width; q++)
            for (int r = 0; r < height; r++)
                if (tiles[q][r] != null) result.add(tiles[q][r]);
        return result;
    }

    public List<Tile> getSettlementZones() {
        return getAllTiles().stream().filter(Tile::isSettlementZone).collect(Collectors.toList());
    }

    public int width() { return width; }
    public int height() { return height; }
}
