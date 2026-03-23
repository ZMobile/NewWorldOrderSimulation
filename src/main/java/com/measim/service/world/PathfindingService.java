package com.measim.service.world;

import com.measim.model.world.HexCoord;

import java.util.List;

public interface PathfindingService {
    List<HexCoord> findPath(HexCoord start, HexCoord goal);
    double pathCost(List<HexCoord> path);
    double tradeCost(HexCoord from, HexCoord to, double baseRate);
}
