package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapColorMathTest {
    @Test
    void averageIgnoresTransparentPixelsAndWeighsByAlpha() {
        int[] pixels = {0xFFFF0000, 0x00FFFFFF, 0xFF0000FF, 0x10FFFFFF};
        assertEquals(0xFF7F007F, MapColorMath.averageArgb(pixels));
    }

    @Test
    void averageOfAFullyTransparentSpriteIsUnknown() {
        assertEquals(0, MapColorMath.averageArgb(new int[]{0x00123456, 0x05FFFFFF}));
    }

    @Test
    void tintMultipliesEachChannelAndKeepsAlpha() {
        assertEquals(0xFF7F7F7F, MapColorMath.multiply(0xFFFFFFFF, 0x7F7F7F));
        assertEquals(0x80000000, MapColorMath.multiply(0x80FFFFFF, 0x000000));
        assertEquals(0xFF91BD59, MapColorMath.multiply(0xFFFFFFFF, 0x91BD59));
    }

    @Test
    void scaleClampsChannelsInsteadOfOverflowing() {
        assertEquals(0xFFFFFFFF, MapColorMath.scale(0xFFF0F0F0, 2.0));
        assertEquals(0xFF000000, MapColorMath.scale(0xFF808080, 0.0));
    }

    @Test
    void lerpGoesFromTheFirstColourToTheSecond() {
        assertEquals(0xFF000000, MapColorMath.lerp(0xFF000000, 0xFFFFFFFF, 0.0));
        assertEquals(0xFFFFFFFF, MapColorMath.lerp(0xFF000000, 0xFFFFFFFF, 1.0));
        assertEquals(0xFF808080, MapColorMath.lerp(0xFF000000, 0xFFFFFFFF, 0.5));
        assertEquals(0xFFFFFFFF, MapColorMath.lerp(0xFF000000, 0xFFFFFFFF, 7.0));
    }

    @Test
    void flatGroundIsNeitherLitNorShaded() {
        assertEquals(1.0, MapColorMath.slopeFactor(64, 64, 64));
    }

    @Test
    void slopesFacingNorthWestAreLitAndTheOthersShaded() {
        assertTrue(MapColorMath.slopeFactor(65, 64, 64) > 1.0);
        assertTrue(MapColorMath.slopeFactor(63, 64, 64) < 1.0);
        assertEquals(MapColorMath.MAX_SLOPE_FACTOR, MapColorMath.slopeFactor(100, 0, 0));
        assertEquals(MapColorMath.MIN_SLOPE_FACTOR, MapColorMath.slopeFactor(0, 100, 100));
    }

    @Test
    void deeperWaterHidesMoreOfTheFloorUpToALimit() {
        assertTrue(MapColorMath.waterOpacity(1) < MapColorMath.waterOpacity(6));
        assertEquals(0.92, MapColorMath.waterOpacity(MapColorMath.MAX_WATER_DEPTH));
    }

    @Test
    void hsvCoversThePrimaryHues() {
        assertEquals(0xFFFF0000, MapColorMath.hsv(0.0f, 1.0f, 1.0f));
        assertEquals(0xFF00FF00, MapColorMath.hsv(1.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000FF, MapColorMath.hsv(2.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0xFF808080, MapColorMath.hsv(0.5f, 0.0f, 0.5f));
    }
}
