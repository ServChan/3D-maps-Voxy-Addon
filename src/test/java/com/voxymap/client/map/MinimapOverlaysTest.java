package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MinimapOverlaysTest {
    private static boolean wikiSlimeChunk(long seed, int x, int z) {
        Random random = new Random(seed + (int) (x * x * 0x4c1906) + (int) (x * 0x5ac0db)
                + (int) (z * z) * 0x4307a7L + (int) (z * 0x5f24f) ^ 0x3ad8025fL);
        return random.nextInt(10) == 0;
    }

    @Test
    void slimeChunksMatchTheKnownJavaEditionFormula() {
        int slimeChunks = 0;
        for (long seed : new long[]{0L, 12345L, -8_413_216_337_224_001L}) {
            for (int x = -40; x <= 40; x += 3) {
                for (int z = -40; z <= 40; z += 3) {
                    boolean expected = wikiSlimeChunk(seed, x, z);
                    assertEquals(expected, MinimapOverlays.isSlimeChunk(seed, x, z), seed + " " + x + " " + z);
                    slimeChunks += expected ? 1 : 0;
                }
            }
        }
        assertNotEquals(0, slimeChunks);
    }

    @Test
    void seedsAreReadLikeTheVanillaWorldCreationScreen() {
        assertEquals(-123L, MinimapOverlays.parseSeed(" -123 "));
        assertEquals("glacier".hashCode(), MinimapOverlays.parseSeed("glacier"));
    }

    @Test
    void biomeColoursAreStableAndOpaque() {
        int plains = MinimapOverlays.biomeColor("minecraft:plains");
        assertEquals(plains, MinimapOverlays.biomeColor("minecraft:plains"));
        assertEquals(0xFF, plains >>> 24);
        assertNotEquals(plains, MinimapOverlays.biomeColor("minecraft:desert"));
    }
}
