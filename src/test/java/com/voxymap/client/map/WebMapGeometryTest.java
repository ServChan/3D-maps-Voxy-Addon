package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMapGeometryTest {
    @Test
    void picksTheNativeZoomWhenOneBlockIsAtLeastOneScreenPixel() {
        assertEquals(3, WebMapGeometry.zoomFor(1.0, 3));
        assertEquals(3, WebMapGeometry.zoomFor(0.25, 3));
        assertEquals(3, WebMapGeometry.zoomFor(Double.NaN, 3));
    }

    @Test
    void stepsDownOneZoomPerDoublingOfBlocksPerPixel() {
        assertEquals(2, WebMapGeometry.zoomFor(2.0, 3));
        assertEquals(1, WebMapGeometry.zoomFor(4.0, 3));
        assertEquals(0, WebMapGeometry.zoomFor(8.0, 3));
        assertEquals(0, WebMapGeometry.zoomFor(4096.0, 3));
    }

    @Test
    void tilesCoverFiveHundredTwelveBlocksAtTheNativeZoomAndDoubleBelowIt() {
        assertEquals(512, WebMapGeometry.tileBlocks(3, 3));
        assertEquals(1024, WebMapGeometry.tileBlocks(2, 3));
        assertEquals(4096, WebMapGeometry.tileBlocks(0, 3));
    }

    @Test
    void tileIndexRoundsTowardsNegativeInfinity() {
        assertEquals(0, WebMapGeometry.tileIndex(0.0, 512));
        assertEquals(0, WebMapGeometry.tileIndex(511.9, 512));
        assertEquals(-1, WebMapGeometry.tileIndex(-0.5, 512));
        assertEquals(-2, WebMapGeometry.tileIndex(-513.0, 512));
        assertEquals(WebMapGeometry.tileIndex(30_000_000.0, 512), WebMapGeometry.tileIndex(1.0E300, 512));
    }

    @Test
    void parentTileOfANegativeTileIsTheFloorHalf() {
        assertEquals(-1, -1 >> 1);
        assertEquals(-1, -2 >> 1);
        assertEquals(-2, -3 >> 1);
    }

    @Test
    void convertsSquaremapWorldNamesToDimensionIds() {
        assertEquals("minecraft:overworld", WebMapGeometry.dimensionOf("minecraft_overworld"));
        assertEquals("minecraft:the_nether", WebMapGeometry.dimensionOf("minecraft_the_nether"));
        assertEquals("minecraft_the_nether", WebMapGeometry.worldNameOf("minecraft:the_nether"));
    }

    @Test
    void scalesCoordinatesBetweenOverworldAndNether() {
        assertEquals(0.125, WebMapGeometry.scaleBetween("minecraft:overworld", "minecraft:the_nether"));
        assertEquals(8.0, WebMapGeometry.scaleBetween("minecraft:the_nether", "minecraft:overworld"));
        assertEquals(1.0, WebMapGeometry.scaleBetween("minecraft:overworld", "minecraft:overworld"));
        assertEquals(1.0, WebMapGeometry.scaleBetween("minecraft:the_end", "minecraft:the_nether"));
    }

    @Test
    void recognisesPngDataAndRejectsTheEmptyMissingTileResponse() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
        assertTrue(WebMapGeometry.isPng(png));
        assertFalse(WebMapGeometry.isPng(new byte[0]));
        assertFalse(WebMapGeometry.isPng("<html></html>".getBytes()));
    }

    @Test
    void clipsLinesToTheViewWithoutRejectingLongEdges() {
        assertArrayEquals(new double[]{0.0, 50.0, 100.0, 50.0},
                WebMapGeometry.clipLine(-5000.0, 50.0, 5000.0, 50.0, 0.0, 0.0, 100.0, 100.0), 1.0E-9);
        assertNull(WebMapGeometry.clipLine(-10.0, -10.0, -5.0, -1.0, 0.0, 0.0, 100.0, 100.0));
        assertNull(WebMapGeometry.clipLine(Double.NaN, 0.0, 1.0, 1.0, 0.0, 0.0, 100.0, 100.0));
    }

    @Test
    void clampsScreenCoordinatesBeforeNarrowing() {
        assertEquals(1_000_000, WebMapGeometry.screenCoordinate(1.0E12));
        assertEquals(-1_000_000, WebMapGeometry.screenCoordinate(-1.0E12));
        assertEquals(0, WebMapGeometry.screenCoordinate(Double.POSITIVE_INFINITY));
    }
}
