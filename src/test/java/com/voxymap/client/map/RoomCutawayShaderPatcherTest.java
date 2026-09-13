package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomCutawayShaderPatcherTest {
    @Test
    void patchesVertexWorldPositionExactlyOnce() {
        String source = "uint helper;\nvoid main() {\n    uint cornerId = gl_VertexID&3;\n}\n";

        String patched = RoomCutawayShaderPatcher.patch("voxy:lod/gl46/quads3.vert", source);

        assertTrue(patched.contains("voxymapWorldPos = voxymapPoint + vec3(baseSectionPos << 5)"));
        assertTrue(patched.contains("voxymapQuadMinY = quad.basePoint.y + float(baseSectionPos.y << 5)"));
        assertTrue(patched.contains("voxymapQuadCenterXZ"));
        assertEquals(patched, RoomCutawayShaderPatcher.patch("voxy:lod/gl46/quads3.vert", patched));
    }

    @Test
    void patchesFragmentAfterDepthAndDerivativeWork() {
        String depthAnchor = "if (DEPTH_SCALAR_COMPARE(gl_FragCoord.z, texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r)) {\n"
                + "        discard;\n        return;\n    }";
        String source = "void main() {\n" + depthAnchor + "\n}\n";

        String patched = RoomCutawayShaderPatcher.patch("voxy:lod/gl46/quads.frag", source);

        assertTrue(patched.indexOf(depthAnchor) < patched.indexOf("voxymapCutawayEnabled != 0"));
        assertTrue(patched.contains("texelFetch(voxymapRoomHeightMap"));
        assertTrue(patched.contains("voxymapQuadMinY >= voxymapRoofCutY"),
                "The dynamic Nether threshold must reject complete roof quads");
        assertTrue(patched.contains("smoothstep("));
        assertTrue(patched.contains("mix(voxymapApertureInnerY"),
                "The whole-quad threshold must deepen smoothly around the inspected map area");
        assertFalse(patched.contains("min(voxymapCutY"),
                "A global fragment plane would expose anti-xray cache cross-sections");
        assertTrue(patched.indexOf("if (voxymapWorldPos.y > voxymapCutY)")
                > patched.indexOf("voxymapTexel.x >= 0"),
                "Only the local verified-room texture may perform per-fragment clipping");
    }

    @Test
    void rejectsChangedVoxyShaderLayoutInsteadOfSilentlyProducingWrongGeometry() {
        assertThrows(IllegalStateException.class, () ->
                RoomCutawayShaderPatcher.patch("voxy:lod/gl46/quads3.vert", "void main() {}"));
    }

    @Test
    void patchSafelyDisablesTheFeatureInsteadOfCrashingOnAnIncompatibleShader() {
        String source = "void main() {}";

        String result = RoomCutawayShaderPatcher.patchSafely("voxy:lod/gl46/quads3.vert", source);

        assertEquals(source, result);
        assertFalse(RoomCutawayShaderPatcher.isSupported());
    }

    @Test
    void leavesOtherShadersUntouched() {
        String source = "void main() {}";
        assertEquals(source, RoomCutawayShaderPatcher.patch("voxy:post/noop.frag", source));
    }
}
