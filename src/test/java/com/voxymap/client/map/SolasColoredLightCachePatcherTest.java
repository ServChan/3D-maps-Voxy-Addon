package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolasColoredLightCachePatcherTest {
    private static final String SOURCE = """
            void gbuffersLighting() {
                vec3 blockLighting = blockLightCol * blockLightMap * (1.0 - emission);
            }
            """;

    @Test
    void patchesOnlyVoxyLightingAroundTheExactSolasMarker() {
        String patched = SolasColoredLightCachePatcher.patch(SOURCE);
        assertTrue(patched.contains("uniform sampler2D voxymapColoredLightCache;"));
        assertTrue(patched.contains("defined VOXY_OPAQUE || defined VOXY_TRANSLUCENT"));
        assertTrue(patched.contains("worldPos + cameraPosition"));
        assertEquals(patched, SolasColoredLightCachePatcher.patch(patched));
    }

    @Test
    void rejectsUnknownLayoutsWithoutPartialPatch() {
        assertThrows(IllegalStateException.class, () -> SolasColoredLightCachePatcher.patch("void main() {}"));
        assertEquals("void main() {}", SolasColoredLightCachePatcher.patchSafely("void main() {}"));
    }

    @Test
    void classifiesRepresentativeSolasEmitters() {
        SolasLightColor.Rgb soul = SolasLightColor.classify("minecraft:soul_lantern", 10);
        SolasLightColor.Rgb verdant = SolasLightColor.classify("minecraft:verdant_froglight", 15);
        SolasLightColor.Rgb warm = SolasLightColor.classify("minecraft:lantern", 15);
        assertNotNull(soul);
        assertTrue(soul.b() > soul.r());
        assertNotNull(verdant);
        assertTrue(verdant.g() > verdant.r());
        assertNotNull(warm);
        assertTrue(warm.r() > warm.g());
        assertNull(SolasLightColor.classify("minecraft:redstone_lamp", 0));
        SolasLightColor.Rgb redstoneLamp = SolasLightColor.classify("minecraft:redstone_lamp", 15);
        assertTrue(redstoneLamp.g() > redstoneLamp.b());
    }
}
