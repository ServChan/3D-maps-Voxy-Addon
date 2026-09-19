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
    private static final String NO_FOG = "fog *= 1.0 - clamp(voxymapMapView, 0.0, 1.0);";

    private static final String MAIN_FOG = """
            void DoCaveFog(inout vec4 color, float lViewPos) {
                float fog = GetCaveFactor() * (0.9 - 0.9 * exp(- lViewPos * 0.015));

                color = mix(color, vec4(caveFogColor, 0.0), fog);
            }

            void DoBorderFog(inout vec4 color, inout float skyFade, float lPos) {
                float fog = lPos / renderDistance;
                if (fog > 0.01 + 0.0025 * dither) {
                    color = mix(color, vec4(fogColorM, 0.0), fog);
                }
            }

            void DoAtmosphericFog(inout vec4 color, float lViewPos) {
                float fog = 1.0 - exp2(-max0(lViewPos - 40.0));
                fog *= altitudeFactor;

                if (fog > 0.01 + 0.0025 * dither) {
                    color = mix(color, vec4(fogColorM, 0.0), fog);
                }
            }
            """;
    private static final String BLOOM_FOG = """
            float GetBloomFog(float lViewPos) {
                float bloomFog = 1.0 - exp(-8.0 * lViewPos);
                float bloomFogMult = netherBloomAdd;
                return 1.0 + bloomFog * bloomFogMult;
            }
            """;
    private static final String NETHER_STORM = """
            vec4 GetNetherStorm() {
                if (isEyeInWater != 0) return vec4(0.0);
                return vec4(1.0);
            }
            """;

    private static int count(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + 1)) {
            count++;
        }
        return count;
    }

    @Test
    void scalesBorderAtmosphericAndCaveFogToZeroInTheMapView() {
        String patched = ComplementaryMapFogPatcher.patch(MAIN_FOG_PATH, MAIN_FOG);

        assertTrue(patched.contains("uniform float voxymapMapView;"));
        assertEquals(3, count(patched, NO_FOG), "border, atmospheric and cave fog are each scaled");
        // The scaling has to land after the value is computed and before it is blended in.
        assertTrue(patched.indexOf("fog *= altitudeFactor;") < patched.lastIndexOf(NO_FOG));
        assertTrue(patched.indexOf(NO_FOG, patched.indexOf("float fog = lPos"))
                < patched.indexOf("if (fog > 0.01", patched.indexOf("float fog = lPos")));
        assertTrue(patched.indexOf(NO_FOG) < patched.indexOf("color = mix(color, vec4(caveFogColor"));
        assertEquals(patched, ComplementaryMapFogPatcher.patch(MAIN_FOG_PATH, patched));
    }

    @Test
    void removesBloomFogAndNetherStormFromTheMapCamera() {
        String bloom = ComplementaryMapFogPatcher.patch(BLOOM_FOG_PATH, BLOOM_FOG);
        assertTrue(bloom.contains("bloomFog * bloomFogMult * (1.0 - clamp(voxymapMapView, 0.0, 1.0));"));

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

    @Test
    void aPartlyRecognisedLayoutIsNotHalfPatched() {
        String onlyBorderFog = "if (fog > 0.01 + 0.0025 * dither) {";
        assertThrows(IllegalStateException.class,
                () -> ComplementaryMapFogPatcher.patch(MAIN_FOG_PATH, onlyBorderFog));
        assertEquals(onlyBorderFog, ComplementaryMapFogPatcher.patchSafely(MAIN_FOG_PATH, onlyBorderFog));
    }
}
