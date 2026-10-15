package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapEntityOcclusionTest {
    private static final double CAP = MapEntityOcclusion.MAX_RAY_BLOCKS;

    @Test
    void entityInTheOpenSeesTheCamera() {
        assertTrue(MapEntityOcclusion.clearTowards(0.5, 65.0, 0.5, 200.5, 260.0, 0.5, CAP, (x, y, z) -> y < 64));
    }

    @Test
    void wallBetweenEntityAndCameraHidesIt() {
        assertFalse(MapEntityOcclusion.clearTowards(0.5, 65.0, 0.5, 200.5, 260.0, 0.5, CAP,
                (x, y, z) -> y < 64 || x == 3 && y < 75));
    }

    @Test
    void wallBehindTheEntityDoesNotHideIt() {
        assertTrue(MapEntityOcclusion.clearTowards(0.5, 65.0, 0.5, 200.5, 260.0, 0.5, CAP,
                (x, y, z) -> y < 64 || x == -3 && y < 75));
    }

    @Test
    void roofAboveAnUndergroundRoomHidesItFromAnObliqueCamera() {
        assertFalse(MapEntityOcclusion.clearTowards(0.5, 52.0, 0.5, 150.5, 300.0, -150.5, CAP,
                (x, y, z) -> y >= 54 && y <= 70));
    }

    @Test
    void occludersBeyondTheRayLimitAreIgnored() {
        assertTrue(MapEntityOcclusion.clearTowards(0.5, 65.0, 0.5, 400.5, 65.0, 0.5, CAP,
                (x, y, z) -> x == 300));
    }

    @Test
    void halfBlocksCoverTheCellButThinBlocksDoNot() {
        assertTrue(MapEntityOcclusion.coversCell(1.0));
        assertTrue(MapEntityOcclusion.coversCell(0.5));
        assertFalse(MapEntityOcclusion.coversCell(0.25 * 0.25));
        assertFalse(MapEntityOcclusion.coversCell(3.0 / 16.0));
    }
}
