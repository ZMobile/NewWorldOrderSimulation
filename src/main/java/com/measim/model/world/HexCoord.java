package com.measim.model.world;

import java.util.List;

public record HexCoord(int q, int r) {

    private static final int[][] NEIGHBOR_OFFSETS = {
            {+1, 0}, {+1, -1}, {0, -1},
            {-1, 0}, {-1, +1}, {0, +1}
    };

    public int s() {
        return -q - r;
    }

    public int distanceTo(HexCoord other) {
        return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs(s() - other.s())) / 2;
    }

    public List<HexCoord> neighbors() {
        return List.of(
                new HexCoord(q + 1, r),
                new HexCoord(q + 1, r - 1),
                new HexCoord(q, r - 1),
                new HexCoord(q - 1, r),
                new HexCoord(q - 1, r + 1),
                new HexCoord(q, r + 1)
        );
    }

    public List<HexCoord> ring(int radius) {
        if (radius <= 0) return List.of(this);
        var results = new java.util.ArrayList<HexCoord>();
        HexCoord current = new HexCoord(q - radius, r + radius);
        for (int dir = 0; dir < 6; dir++) {
            for (int step = 0; step < radius; step++) {
                results.add(current);
                current = new HexCoord(
                        current.q + NEIGHBOR_OFFSETS[dir][0],
                        current.r + NEIGHBOR_OFFSETS[dir][1]
                );
            }
        }
        return results;
    }

    public List<HexCoord> range(int radius) {
        var results = new java.util.ArrayList<HexCoord>();
        for (int dq = -radius; dq <= radius; dq++) {
            for (int dr = Math.max(-radius, -dq - radius); dr <= Math.min(radius, -dq + radius); dr++) {
                results.add(new HexCoord(q + dq, r + dr));
            }
        }
        return results;
    }

    public double toPixelX(double hexSize) {
        return hexSize * (Math.sqrt(3.0) * q + Math.sqrt(3.0) / 2.0 * r);
    }

    public double toPixelY(double hexSize) {
        return hexSize * (3.0 / 2.0 * r);
    }
}
