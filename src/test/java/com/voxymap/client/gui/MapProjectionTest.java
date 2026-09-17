package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MapProjectionTest {
    @Test
    void projectsForwardPointToViewportCenterAndRejectsBehindCamera() {
        MapProjection.Point center = MapProjection.project(
                0, 0, 10, 0, 0, 0,
                0, 0, 1, 1, 0, 0, 1, 0,
                1, 1, 800, 600);
        assertEquals(new MapProjection.Point(400, 300), center);
        assertNull(MapProjection.project(
                0, 0, -1, 0, 0, 0,
                0, 0, 1, 1, 0, 0, 1, 0,
                1, 1, 800, 600));
    }

    @Test
    void clampsEdgeOnProjectionBeforeIntegerNarrowing() {
        MapProjection.Point point = MapProjection.project(
                1.0E12, 0, 0.1001, 0, 0, 0,
                0, 0, 1, 1, 0, 0, 1, 0,
                1, 1, 800, 600);
        assertEquals(-1_000_000, point.x());
    }

    @Test
    void clipsOrdinaryAndPathologicalInputToABoundedViewportSpan() {
        assertArrayEquals(new double[]{-2, 50, 102, 50},
                MapProjection.clipLine(-100, 50, 200, 50, 100, 100), 0.0001);
        double[] pathological = MapProjection.clipLine(-1_000_000, 50, 1_000_000, 50, 100, 100);
        assertNotNull(pathological);
        assertEquals(104.0, pathological[2] - pathological[0], 0.0001);
    }
}
