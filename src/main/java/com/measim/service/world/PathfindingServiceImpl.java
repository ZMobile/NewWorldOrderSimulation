package com.measim.service.world;

import com.measim.dao.WorldDao;
import com.measim.model.world.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class PathfindingServiceImpl implements PathfindingService {

    private final WorldDao worldDao;

    @Inject
    public PathfindingServiceImpl(WorldDao worldDao) { this.worldDao = worldDao; }

    @Override
    public List<HexCoord> findPath(HexCoord start, HexCoord goal) {
        if (!worldDao.inBounds(start) || !worldDao.inBounds(goal)) return List.of();
        Tile goalTile = worldDao.getTile(goal);
        if (goalTile == null || !goalTile.terrain().isPassable()) return List.of();

        Map<HexCoord, Double> gScore = new HashMap<>();
        Map<HexCoord, HexCoord> cameFrom = new HashMap<>();
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));

        gScore.put(start, 0.0);
        openSet.add(new Node(start, start.distanceTo(goal)));

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.coord.equals(goal)) return reconstructPath(cameFrom, goal);
            for (HexCoord neighbor : current.coord.neighbors()) {
                if (!worldDao.inBounds(neighbor)) continue;
                Tile neighborTile = worldDao.getTile(neighbor);
                if (neighborTile == null || !neighborTile.terrain().isPassable()) continue;
                double tentativeG = gScore.get(current.coord) + neighborTile.terrain().movementCost();
                if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    cameFrom.put(neighbor, current.coord);
                    gScore.put(neighbor, tentativeG);
                    openSet.add(new Node(neighbor, tentativeG + neighbor.distanceTo(goal)));
                }
            }
        }
        return List.of();
    }

    @Override
    public double pathCost(List<HexCoord> path) {
        double cost = 0;
        for (int i = 1; i < path.size(); i++) {
            Tile tile = worldDao.getTile(path.get(i));
            if (tile != null) cost += tile.terrain().movementCost();
        }
        return cost;
    }

    @Override
    public double tradeCost(HexCoord from, HexCoord to, double baseRate) {
        List<HexCoord> path = findPath(from, to);
        if (path.isEmpty()) return Double.MAX_VALUE;
        return baseRate * pathCost(path);
    }

    private List<HexCoord> reconstructPath(Map<HexCoord, HexCoord> cameFrom, HexCoord current) {
        List<HexCoord> path = new ArrayList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) { current = cameFrom.get(current); path.addFirst(current); }
        return path;
    }

    private record Node(HexCoord coord, double fScore) {}
}
