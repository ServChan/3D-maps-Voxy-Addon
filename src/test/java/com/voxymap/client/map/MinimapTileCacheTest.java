package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapTileCacheTest {
    @Test
    void surfaceHeightReturnsOneAboveTheTopmostSolidBlock() {
        // Solid at y<=10, air above - the surface reading should be one above the block, matching
        // vanilla's own heightmap convention.
        int height = MinimapTileCache.surfaceHeight((x, y, z) -> y > 10, 0, 0, 20, -64);
        assertEquals(11, height);
    }

    @Test
    void surfaceHeightScansDownFromStartYNotFromTheWorldTop() {
        // A solid "ceiling" above startY (e.g. a room roof, or the Nether bedrock ceiling) must not be
        // picked up - the scan only ever looks at or below startY, matching how the caller anchors it to
        // the player's own altitude instead of vanilla's top-down WORLD_SURFACE heightmap.
        int height = MinimapTileCache.surfaceHeight((x, y, z) -> y != 60 && y > 5, 0, 0, 30, -64);
        assertEquals(6, height);
    }

    @Test
    void surfaceHeightReturnsMinYWhenTheColumnIsAirAllTheWayDown() {
        int height = MinimapTileCache.surfaceHeight((x, y, z) -> true, 0, 0, 20, -64);
        assertEquals(-64, height);
    }

    @Test
    void surfaceHeightIncludesStartYItself() {
        // A solid block exactly at startY (the player standing right on a floor) must still register -
        // an off-by-one here would miss the floor directly underfoot.
        int height = MinimapTileCache.surfaceHeight((x, y, z) -> y != 20, 0, 0, 20, -64);
        assertEquals(21, height);
    }

    @Test
    void shadeIsLowWhenTheNorthNeighborIsTaller() {
        assertEquals(MinimapTileCache.Shade.LOW, MinimapTileCache.shadeOf(64, 70));
    }

    @Test
    void shadeIsHighWhenTheNorthNeighborIsShorter() {
        assertEquals(MinimapTileCache.Shade.HIGH, MinimapTileCache.shadeOf(64, 50));
    }

    @Test
    void shadeIsNormalWhenTheNorthNeighborIsTheSameHeight() {
        assertEquals(MinimapTileCache.Shade.NORMAL, MinimapTileCache.shadeOf(64, 64));
    }
}
