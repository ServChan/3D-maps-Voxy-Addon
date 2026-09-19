package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackFogPatcherTest {
    private static final String MAIN_FOG = "/lib/atmospherics/fog/mainFog.glsl";
    private static final String BLOOM_FOG = "/lib/atmospherics/fog/bloomFog.glsl";
    private static final String NETHER_STORM = "/lib/atmospherics/netherStorm.glsl";
    private static final String NO_FOG = "fog *= 1.0 - clamp(voxymapMapView, 0.0, 1.0);";
    private static final String DECLARATION = "uniform float voxymapMapView;";

    private static int count(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + 1)) {
            count++;
        }
        return count;
    }

    /** The first statement of the function whose header starts with {@code header}. */
    private static String firstStatement(String source, String header) {
        int body = source.indexOf('{', source.indexOf(header)) + 1;
        return source.substring(body, source.indexOf('\n', body + 2)).strip();
    }

    // ---- Complementary Reimagined / Unbound ----

    private static final String COMPLEMENTARY_MAIN = """
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

    @Test
    void complementaryScalesBorderAtmosphericAndCaveFogToZeroInTheMapView() {
        String patched = ShaderPackFogPatcher.patch(MAIN_FOG, COMPLEMENTARY_MAIN);

        assertTrue(patched.contains(DECLARATION));
        assertEquals(3, count(patched, NO_FOG), "border, atmospheric and cave fog are each scaled");
        // The scaling has to land after the value is computed and before it is blended in.
        assertTrue(patched.indexOf("fog *= altitudeFactor;") < patched.lastIndexOf(NO_FOG));
        int borderFog = patched.indexOf("float fog = lPos");
        assertTrue(patched.indexOf(NO_FOG, borderFog) < patched.indexOf("if (fog > 0.01", borderFog));
        assertTrue(patched.indexOf(NO_FOG) < patched.indexOf("color = mix(color, vec4(caveFogColor"));
        assertEquals(patched, ShaderPackFogPatcher.patch(MAIN_FOG, patched));
    }

    @Test
    void complementaryBloomFogAndNetherStormAreRemovedFromTheMapCamera() {
        String bloom = ShaderPackFogPatcher.patch(BLOOM_FOG,
                "float GetBloomFog(float d) {\n    return 1.0 + bloomFog * bloomFogMult;\n}\n");
        assertTrue(bloom.contains("bloomFog * bloomFogMult * (1.0 - clamp(voxymapMapView, 0.0, 1.0));"));

        String storm = ShaderPackFogPatcher.patch(NETHER_STORM,
                "vec4 GetNetherStorm() {\n    if (isEyeInWater != 0) return vec4(0.0);\n    return vec4(1.0);\n}\n");
        assertTrue(storm.contains("isEyeInWater != 0 || voxymapMapView > 0.5"));
    }

    // ---- Rethinking Voxels (a Complementary fork with vec3 colours and a different threshold) ----

    private static final String RETHINKING_MAIN = """
            void DoBorderFog(inout vec3 color, inout float skyFade, float lPos) {
                float fog = lPos / renderDistance;
                if (fog > 0.0) {
                    color = mix(color, fogColorM, fog);
                }
            }

            void DoCaveFog(inout vec3 color, float lViewPos) {
                float fog = GetCaveFactor() * (0.9 - 0.9 * exp(- lViewPos * 0.015));

                color = mix(color, caveFogColor, fog);
            }

            void DoAtmosphericFog(inout vec3 color, float lViewPos) {
                float fog = 1.0 - exp2(-max0(lViewPos - 40.0));
                fog *= altitudeFactor;

                if (fog > 0.0) {
                    color = mix(color, fogColorM, fog);
                }
            }
            """;

    @Test
    void rethinkingVoxelsIsPatchedThroughItsOwnFlavour() {
        String patched = ShaderPackFogPatcher.patch(MAIN_FOG, RETHINKING_MAIN);

        assertEquals(3, count(patched, NO_FOG));
        assertTrue(patched.indexOf(NO_FOG) < patched.indexOf("if (fog > 0.0) {"));
        assertTrue(patched.contains(DECLARATION));
    }

    // ---- packs whose fog is one function: the function returns "no fog" first ----

    @Test
    void solasNormalFogReturnsImmediatelyInTheMapView() {
        String source = """
                void getNormalFog(inout vec3 color, in vec3 atmosphereColor, in vec3 viewPos, in float z0) {
                    float farPlane = far;
                    color = fmix(color, fogCol, fog);
                }
                """;
        String patched = ShaderPackFogPatcher.patch("/lib/atmosphere/fog.glsl", source);

        assertEquals("if (voxymapMapView > 0.5) return;", firstStatement(patched, "void getNormalFog("));
        assertTrue(patched.contains(DECLARATION));
        assertEquals(patched, ShaderPackFogPatcher.patch("/lib/atmosphere/fog.glsl", patched));
    }

    @Test
    void bslNormalFogReturnsImmediatelyInTheMapView() {
        String patched = ShaderPackFogPatcher.patch("/lib/atmospherics/fog.glsl",
                "void NormalFog(inout vec3 color, vec3 viewPos) {\n    color = mix(color, fogColor, fog);\n}\n");

        assertEquals("if (voxymapMapView > 0.5) return;", firstStatement(patched, "void NormalFog("));
    }

    @Test
    void mellowFogMainKeepsTheColourUntouchedInTheMapView() {
        String patched = ShaderPackFogPatcher.patch("/global/fog.glsl",
                "vec3 get_fog_main(vec3 ScreenPos, vec3 Color, float Dither, bool IsDH) {\n    return Color;\n}\n");

        assertEquals("if (voxymapMapView > 0.5) return Color;", firstStatement(patched, "vec3 get_fog_main("));
    }

    @Test
    void photonBorderCommonAndVolumetricFogAreAllBypassed() {
        String simple = ShaderPackFogPatcher.patch("/include/fog/simple_fog.glsl", """
                #if !defined INCLUDE_FOG_SIMPLE_FOG
                #define INCLUDE_FOG_SIMPLE_FOG
                float border_fog(vec3 scene_pos, vec3 world_dir) {
                    return 0.5;
                }
                vec4 common_fog(float view_dist, const bool sky) {
                    return vec4(1.0);
                }
                float common_fog_alpha(float view_dist, bool sky) {
                    return 0.5;
                }
                #endif
                """);
        assertEquals("if (voxymapMapView > 0.5) return 1.0;", firstStatement(simple, "float border_fog("));
        assertEquals("if (voxymapMapView > 0.5) return vec4(vec3(0.0), 1.0);",
                firstStatement(simple, "vec4 common_fog("));
        assertEquals("if (voxymapMapView > 0.5) return 1.0;", firstStatement(simple, "float common_fog_alpha("));
        assertTrue(simple.indexOf(DECLARATION) < simple.indexOf("#if !defined INCLUDE_FOG_SIMPLE_FOG"),
                "the uniform is declared before the include guard");

        String multiLine = """
                mat2x3 raymarch_air_fog(
                    vec3 world_start_pos,
                    float dither
                ) {
                    return mat2x3(1.0);
                }
                """;
        String raymarched = ShaderPackFogPatcher.patch("/include/fog/overworld/raymarched.glsl", multiLine);
        assertEquals("if (voxymapMapView > 0.5) return mat2x3(vec3(0.0), vec3(1.0));",
                firstStatement(raymarched, "mat2x3 raymarch_air_fog("),
                "no scattering and full transmittance");
        assertTrue(ShaderPackFogPatcher.handles("/include/fog/overworld/analytic.glsl"));
        assertTrue(ShaderPackFogPatcher.handles("/include/fog/end_fog_vl.glsl"));
    }

    // ---- safety ----

    @Test
    void ignoresUnrelatedIncludes() {
        assertFalse(ShaderPackFogPatcher.handles("/lib/common.glsl"));
        assertEquals("x", ShaderPackFogPatcher.patch("/lib/common.glsl", "x"));
    }

    @Test
    void rejectsUnknownLayoutsAndSafePatchLeavesThemUntouched() {
        for (String path : new String[] {MAIN_FOG, "/lib/atmosphere/fog.glsl", "/global/fog.glsl",
                "/include/fog/simple_fog.glsl"}) {
            assertThrows(IllegalStateException.class, () -> ShaderPackFogPatcher.patch(path, "void main() {}"));
            assertEquals("void main() {}", ShaderPackFogPatcher.patchSafely(path, "void main() {}"));
        }
    }

    @Test
    void aPartlyRecognisedLayoutIsNotHalfPatched() {
        // Complementary's border fog anchor without its cave fog anchor.
        String onlyBorderFog = "if (fog > 0.01 + 0.0025 * dither) {";
        assertThrows(IllegalStateException.class, () -> ShaderPackFogPatcher.patch(MAIN_FOG, onlyBorderFog));
        assertEquals(onlyBorderFog, ShaderPackFogPatcher.patchSafely(MAIN_FOG, onlyBorderFog));

        // Photon's include with only one of its three functions.
        String onlyBorder = "float border_fog(vec3 a, vec3 b) {\n    return 1.0;\n}\n";
        assertThrows(IllegalStateException.class,
                () -> ShaderPackFogPatcher.patch("/include/fog/simple_fog.glsl", onlyBorder));
    }
}
