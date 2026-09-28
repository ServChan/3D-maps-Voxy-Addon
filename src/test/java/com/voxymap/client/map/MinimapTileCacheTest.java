package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapTileCacheTest {
    @Test
    void surfaceHeightReturnsOneAboveTheTopmostSolidBlock() {

        int height = MinimapTileCache.surfaceHeight((x, y, z) -> y > 10, 0, 0, 20, -64);
        assertEquals(11, height);
    }

    @Test
    void surfaceHeightScansDownFromStartYNotFromTheWorldTop() {

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

        int height = MinimapTileCache.surfaceHeight((x, y, z) -> y != 20, 0, 0, 20, -64);
        assertEquals(21, height);
    }

    @Test
    void tileKeysRoundTripNegativeAndLargeChunkCoordinates() {
        for (int[] chunk : new int[][]{{0, 0}, {-1, 1}, {1, -1}, {-1_875_000, 1_875_000}, {Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
            long key = MinimapTileCache.key(chunk[0], chunk[1]);
            assertEquals(chunk[0], MinimapTileCache.keyX(key));
            assertEquals(chunk[1], MinimapTileCache.keyZ(key));
        }
    }

    @Test
    void aCaveTileComputedAtAnotherHeightIsStale() {
        MinimapTileCache.ScanMode cave = MinimapTileCache.ScanMode.CAVE;
        assertEquals(true, MinimapTileCache.isFresh(cave, 64, cave, 64 + MinimapTileCache.Y_TOLERANCE_BLOCKS - 1));
        assertEquals(false, MinimapTileCache.isFresh(cave, 64, cave, 64 + MinimapTileCache.Y_TOLERANCE_BLOCKS));
        assertEquals(false, MinimapTileCache.isFresh(cave, 64, cave, 20));
    }

    @Test
    void aSurfaceTileStaysFreshAtAnyHeightButNotAcrossModes() {
        MinimapTileCache.ScanMode surface = MinimapTileCache.ScanMode.SURFACE;
        MinimapTileCache.ScanMode cave = MinimapTileCache.ScanMode.CAVE;
        assertEquals(true, MinimapTileCache.isFresh(surface, 150, surface, 269));
        assertEquals(false, MinimapTileCache.isFresh(surface, 64, cave, 64));
        assertEquals(false, MinimapTileCache.isFresh(cave, 64, surface, 64));
    }

    @Test
    void theMinimapUsesTheSurfaceUnlessThereIsASolidRoofOverThePlayer() {
        assertEquals(MinimapTileCache.ScanMode.SURFACE, MinimapTileCache.scanMode(false, 269, 200),
                "flying above a mountain must show its surface, not the rock at the player's height");
        assertEquals(MinimapTileCache.ScanMode.SURFACE, MinimapTileCache.scanMode(false, 64, 65));
        assertEquals(MinimapTileCache.ScanMode.SURFACE, MinimapTileCache.scanMode(false, 64, 67));
        assertEquals(MinimapTileCache.ScanMode.CAVE, MinimapTileCache.scanMode(false, 40, 90));
        assertEquals(MinimapTileCache.ScanMode.CAVE, MinimapTileCache.scanMode(true, 100, 0),
                "a dimension with a ceiling always scans from the player's height");
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
