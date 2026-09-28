package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxyShaderPatcherTest {
    private static final String DEPTH_ANCHOR =
            "if (DEPTH_SCALAR_COMPARE(gl_FragCoord.z, texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r)) {\n"
                    + "        discard;\n        return;\n    }";

    private static int count(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) count++;
        }
        return count;
    }

    @Test
    void patchesVertexQuadBoundsExactlyOnce() {
        String source = "uint helper;\nvoid main() {\n    uint cornerId = gl_VertexID&3;\n}\n";

        String patched = VoxyShaderPatcher.patch("voxy:lod/gl46/quads3.vert", source);

        assertTrue(patched.contains("voxymapQuadMinY = quad.basePoint.y + float(baseSectionPos.y << 5)"));
        assertTrue(patched.contains("voxymapQuadCenterXZ"));
        assertEquals(patched, VoxyShaderPatcher.patch("voxy:lod/gl46/quads3.vert", patched));
    }

    @Test
    void patchesFragmentAfterDepthAndDerivativeWork() {
        String source = "void main() {\n" + DEPTH_ANCHOR + "\n}\n";

        String patched = VoxyShaderPatcher.patch("voxy:lod/gl46/quads.frag", source);

        assertTrue(patched.indexOf(DEPTH_ANCHOR) < patched.indexOf("voxymapCutawayEnabled != 0"));
        assertTrue(patched.contains("voxymapQuadMinY >= voxymapRoofCutY"),
                "The dynamic Nether threshold must reject complete roof quads");
        assertTrue(patched.contains("smoothstep("));
        assertTrue(patched.contains("mix(voxymapApertureInnerY"),
                "The whole-quad threshold must deepen smoothly around the inspected map area");
        assertFalse(patched.contains("min(voxymapCutY"),
                "A global fragment plane would expose anti-xray cache cross-sections");
        assertTrue(patched.contains("voxymapOreMaskEnabled != 0"), "the ore mask stays in the shader");
        assertEquals(patched, VoxyShaderPatcher.patch("voxy:lod/gl46/quads.frag", patched));
    }

    @Test
    void fragmentPatchNoLongerCarriesTheRemovedRoomCutaway() {
        String patched = VoxyShaderPatcher.patch("voxy:lod/gl46/quads.frag",
                "void main() {\n" + DEPTH_ANCHOR + "\n}\n");

        for (String removed : new String[] {
                "voxymapRoomHeightMap", "voxymapSphere", "voxymapCutawayOrigin", "voxymapWorldPos", "voxymapCutY"}) {
            assertFalse(patched.contains(removed), removed + " must be gone");
        }
        assertFalse(VoxyShaderPatcher.patch("voxy:lod/gl46/quads3.vert",
                "void main() {\n    uint cornerId = gl_VertexID&3;\n}\n").contains("voxymapWorldPos"));
    }

    @Test
    void patchedFragmentIsStructurallyBalanced() {
        String patched = VoxyShaderPatcher.patch("voxy:lod/gl46/quads.frag",
                "void main() {\n" + DEPTH_ANCHOR + "\n}\n");

        assertEquals(count(patched, '{'), count(patched, '}'), "unbalanced braces in the injected GLSL");
        assertEquals(count(patched, '('), count(patched, ')'), "unbalanced parentheses in the injected GLSL");
        assertFalse(patched.contains("%"), "an unformatted placeholder leaked into the shader");
    }

    @Test
    void everyDeclaredUniformIsInAUniqueLocation() {
        String patched = VoxyShaderPatcher.patch("voxy:lod/gl46/quads.frag",
                "void main() {\n" + DEPTH_ANCHOR + "\n}\n");

        java.util.Set<String> locations = new java.util.HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("layout\\(location = (\\d+)\\) (?:uniform|in flat|in)").matcher(patched);
        while (matcher.find()) {

            assertTrue(locations.add(matcher.group(1)), "location " + matcher.group(1) + " declared twice");
        }
        assertTrue(locations.contains(Integer.toString(VoxyShaderPatcher.ENABLED_UNIFORM_LOCATION)));
        assertTrue(locations.contains(Integer.toString(VoxyShaderPatcher.ORE_MASK_ENABLED_UNIFORM_LOCATION)));
    }

    @Test
    void rejectsChangedVoxyShaderLayoutInsteadOfSilentlyProducingWrongGeometry() {
        assertThrows(IllegalStateException.class, () ->
                VoxyShaderPatcher.patch("voxy:lod/gl46/quads3.vert", "void main() {}"));
    }

    @Test
    void patchSafelyDisablesTheFeatureInsteadOfCrashingOnAnIncompatibleShader() {
        String source = "void main() {}";

        String result = VoxyShaderPatcher.patchSafely("voxy:lod/gl46/quads3.vert", source);

        assertEquals(source, result);
        assertFalse(VoxyShaderPatcher.isSupported());
    }

    @Test
    void leavesOtherShadersUntouched() {
        String source = "void main() {}";
        assertEquals(source, VoxyShaderPatcher.patch("voxy:post/noop.frag", source));
    }
}
