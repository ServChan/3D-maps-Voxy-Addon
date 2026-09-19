package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplementaryMapFogPatcherTest {
    private static final String MAIN_FOG_PATH = "/lib/atmospherics/fog/mainFog.glsl";
    private static final String BLOOM_FOG_PATH = "/lib/atmospherics/fog/bloomFog.glsl";
    private static final String NETHER_STORM_PATH = "/lib/atmospherics/netherStorm.glsl";

    private static final String MAIN_FOG = """
            void DoBorderFog(inout vec4 color, inout float skyFade, float lPos) {
                #ifdef NETHER
                    float farM = min(renderDistance, NETHER_VIEW_LIMIT);
                    float fog = lPos / farM;
                    fog = mix(fog, min1(lPos / min(farM, 256.0)), 0.5);
                #endif
                if (fog > 0.01) {}
            }
            """;
    private static final String BLOOM_FOG = """
            float GetBloomFog(float lViewPos) {
                float bloomFog = 1.0 - exp(-8.0 * lViewPos);
                bloomFog *= float(isEyeInWater == 0);
                return 1.0 + bloomFog;
            }
            """;
    private static final String NETHER_STORM = """
            vec4 GetNetherStorm() {
                if (isEyeInWater != 0) return vec4(0.0);
                return vec4(1.0);
            }
            """;

    @Test
    void removesBorderFogFromTheMapCameraAfterTheNetherDistanceIsComputed() {
        String patched = ComplementaryMapFogPatcher.patch(MAIN_FOG_PATH, MAIN_FOG);

        assertTrue(patched.contains("uniform float voxymapMapView;"));
        assertTrue(patched.indexOf("min1(lPos / min(farM, 256.0))")
                < patched.indexOf("fog *= 1.0 - clamp(voxymapMapView, 0.0, 1.0);"));
        assertEquals(patched, ComplementaryMapFogPatcher.patch(MAIN_FOG_PATH, patched));
    }

    @Test
    void removesBloomFogAndNetherStormFromTheMapCamera() {
        String bloom = ComplementaryMapFogPatcher.patch(BLOOM_FOG_PATH, BLOOM_FOG);
        assertTrue(bloom.contains("bloomFog *= 1.0 - clamp(voxymapMapView, 0.0, 1.0);"));

        String storm = ComplementaryMapFogPatcher.patch(NETHER_STORM_PATH, NETHER_STORM);
        assertTrue(storm.contains("isEyeInWater != 0 || voxymapMapView > 0.5"));
    }

    @Test
    void ignoresUnrelatedIncludes() {
        assertFalse(ComplementaryMapFogPatcher.handles("/lib/common.glsl"));
        assertEquals("x", ComplementaryMapFogPatcher.patch("/lib/common.glsl", "x"));
    }

    @Test
    void rejectsUnknownLayoutsAndSafePatchLeavesThemUntouched() {
        assertThrows(IllegalStateException.class,
                () -> ComplementaryMapFogPatcher.patch(MAIN_FOG_PATH, "void main() {}"));
        assertEquals("void main() {}", ComplementaryMapFogPatcher.patchSafely(MAIN_FOG_PATH, "void main() {}"));
    }
}
