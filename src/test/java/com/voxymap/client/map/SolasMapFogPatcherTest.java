package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolasMapFogPatcherTest {
    private static final String SOLAS_37_FOG = """
            void getNormalFog(inout vec3 color, in vec3 atmosphereColor, in vec3 viewPos, in vec3 worldPos, in float lViewPos, in float lWorldPos, in float z0) {
                float farPlane = far;
                float distanceFactor = 25.0 * (0.5 + timeBrightness) + FOG_DISTANCE * (0.75 + caveFactor * 0.25) - wetness * 25.0;
                float altitude = exp2(FOG_HEIGHT_FALLOFF + moonVisibility + timeBrightness + wetness - isJungle - isSwamp);
                density += isLushCaves * 0.3 + wetness * 0.25;
                fog = clamp(fog * density * altitude, 0.0, 1.0);
            }
            """;

    @Test
    void attenuatesOnlyTheMapRainContributionAndCapsWhiteout() {
        String patched = SolasMapFogPatcher.patch(SOLAS_37_FOG);

        assertTrue(patched.contains("uniform float voxymapMapView;"));
        assertTrue(patched.contains("wetness * mix(1.0, 0.22, voxymapView)"));
        assertTrue(patched.contains("mix(1.0, 0.72, voxymapRainHaze)"));
        assertTrue(patched.contains("- voxymapWetness * 25.0"));
        assertEquals(patched, SolasMapFogPatcher.patch(patched));
    }

    @Test
    void rejectsUnknownLayoutsInsteadOfPartiallyPatchingThem() {
        assertThrows(IllegalStateException.class, () -> SolasMapFogPatcher.patch("void main() {}"));
    }

    @Test
    void safePatchLeavesUnknownShaderSourceUntouched() {
        String source = "void main() {}";
        assertEquals(source, SolasMapFogPatcher.patchSafely(source));
    }
}
