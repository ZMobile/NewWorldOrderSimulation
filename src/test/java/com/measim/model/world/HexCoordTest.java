package com.measim.model.world;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HexCoordTest {

    @Test void cubeCoordinateConstraint() {
        HexCoord coord = new HexCoord(3, -1);
        assertEquals(-2, coord.s());
        assertEquals(0, coord.q() + coord.r() + coord.s());
    }

    @Test void distanceToSelf() { assertEquals(0, new HexCoord(0, 0).distanceTo(new HexCoord(0, 0))); }

    @Test void distanceToNeighbor() {
        HexCoord a = new HexCoord(0, 0);
        for (HexCoord neighbor : a.neighbors()) assertEquals(1, a.distanceTo(neighbor));
    }

    @Test void distanceSymmetric() {
        HexCoord a = new HexCoord(3, -1), b = new HexCoord(-2, 4);
        assertEquals(a.distanceTo(b), b.distanceTo(a));
    }

    @Test void neighborsCount() { assertEquals(6, new HexCoord(5, 5).neighbors().size()); }
    @Test void rangeContainsCenter() { assertTrue(new HexCoord(0, 0).range(2).contains(new HexCoord(0, 0))); }
    @Test void rangeRadius1Has7Tiles() { assertEquals(7, new HexCoord(0, 0).range(1).size()); }
    @Test void rangeRadius2Has19Tiles() { assertEquals(19, new HexCoord(0, 0).range(2).size()); }
    @Test void ringRadius1Has6Tiles() { assertEquals(6, new HexCoord(0, 0).ring(1).size()); }

    @Test void ringRadius0ReturnsSelf() {
        HexCoord center = new HexCoord(3, 4);
        List<HexCoord> ring = center.ring(0);
        assertEquals(1, ring.size());
        assertEquals(center, ring.getFirst());
    }
}
