package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Map2dGeometryTest {
    @Test
    void levelGrowsOnePerDoublingOfBlocksPerPixel() {
        assertEquals(0, Map2dGeometry.levelFor(0.125));
        assertEquals(0, Map2dGeometry.levelFor(1.0));
        assertEquals(0, Map2dGeometry.levelFor(1.9));
        assertEquals(1, Map2dGeometry.levelFor(2.0));
        assertEquals(4, Map2dGeometry.levelFor(16.0));
        assertEquals(Map2dGeometry.MAX_LEVEL, Map2dGeometry.levelFor(4096.0));
        assertEquals(0, Map2dGeometry.levelFor(Double.NaN));
    }

    @Test
    void voxyLevelsCoverEightSectionsPerTileSide() {
        for (int level = 0; level <= Map2dGeometry.VOXY_MAX_LEVEL; level++) {
            assertEquals(Map2dGeometry.tileBlocks(level),
                    Map2dGeometry.SECTIONS_PER_TILE * Map2dGeometry.sectionBlocks(level));
            assertEquals(Map2dGeometry.TILE_PIXELS * Map2dGeometry.voxelBlocks(level), Map2dGeometry.tileBlocks(level));
        }
    }

    @Test
    void tileCoordinateRoundsTowardsNegativeInfinity() {
        assertEquals(0, Map2dGeometry.tileCoordinate(0.0, 0));
        assertEquals(0, Map2dGeometry.tileCoordinate(255.9, 0));
        assertEquals(-1, Map2dGeometry.tileCoordinate(-0.1, 0));
        assertEquals(-1, Map2dGeometry.tileCoordinate(-512.0, 1));
        assertEquals(-2, Map2dGeometry.tileCoordinate(-513.0, 1));
    }

    @Test
    void sectionRangeCoversTheWholeWorldHeight() {
        assertEquals(-2, Map2dGeometry.lowestSection(-64, 0));
        assertEquals(9, Map2dGeometry.highestSection(320, 0));
        assertEquals(-1, Map2dGeometry.lowestSection(-64, 4));
        assertEquals(0, Map2dGeometry.highestSection(320, 4));
    }

    @Test
    void caveScanAllowsTheSameEightSolidBlocksAsTheMinimapAndAtLeastOneVoxel() {
        assertEquals(8, Map2dGeometry.caveSolidVoxels(0));
        assertEquals(2, Map2dGeometry.caveSolidVoxels(2));
        assertEquals(1, Map2dGeometry.caveSolidVoxels(3));
        assertEquals(1, Map2dGeometry.caveSolidVoxels(4));
    }

    @Test
    void caveSliceSnapsToSixteenBlockBands() {
        assertEquals(72, Map2dGeometry.caveSlice(64.0));
        assertEquals(72, Map2dGeometry.caveSlice(79.9));
        assertEquals(-8, Map2dGeometry.caveSlice(-0.5));
    }

    @Test
    void slopeIsMeasuredPerBlockAtEveryLevel() {
        double fine = Map2dGeometry.slopeFactor(65, 64, 64, 0);
        double coarse = Map2dGeometry.slopeFactor(80, 64, 64, 4);
        assertEquals(fine, coarse, 1.0e-9);
        assertEquals(1.0, Map2dGeometry.slopeFactor(70, 70, 70, 2), 1.0e-9);
        assertTrue(Map2dGeometry.slopeFactor(0, 200, 200, 0) >= MapColorMath.MIN_SLOPE_FACTOR);
    }

    @Test
    void averageSkipsTransparentPixels() {
        assertEquals(0, Map2dGeometry.average(0, 0, 0, 0));
        assertEquals(0xFF102030, Map2dGeometry.average(0xFF102030, 0, 0, 0));
        assertEquals(0xFF080808, Map2dGeometry.average(0xFF000000, 0xFF101010, 0, 0));
    }

    @Test
    void downsamplePlacesEachChildInItsQuarter() {
        int size = Map2dGeometry.TILE_PIXELS;
        int[] red = filled(0xFFFF0000);
        int[] blue = filled(0xFF0000FF);
        int[] result = Map2dGeometry.downsample(red, null, null, blue);
        assertEquals(0xFFFF0000, result[0]);
        assertEquals(0, result[size - 1]);
        assertEquals(0, result[(size - 1) * size]);
        assertEquals(0xFF0000FF, result[size * size - 1]);
        assertFalse(Map2dGeometry.empty(result));
        assertTrue(Map2dGeometry.empty(new int[16]));
    }

    @Test
    void screenAndWorldCoordinatesAreInverse() {
        double screen = Map2dGeometry.screenX(1234.5, 1000.0, 4.0, 800);
        assertEquals(400.0 + 234.5 / 4.0, screen, 1.0e-9);
        assertEquals(1234.5, Map2dGeometry.worldX(screen, 1000.0, 4.0, 800), 1.0e-9);
        double screenY = Map2dGeometry.screenY(-50.0, 10.0, 0.5, 600);
        assertEquals(-50.0, Map2dGeometry.worldZ(screenY, 10.0, 0.5, 600), 1.0e-9);
    }

    private static int[] filled(int color) {
        int[] pixels = new int[Map2dGeometry.TILE_PIXELS * Map2dGeometry.TILE_PIXELS];
        java.util.Arrays.fill(pixels, color);
        return pixels;
    }
}
