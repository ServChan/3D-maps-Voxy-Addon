package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherSliceShaderPatcherTest {
    @Test
    void patchesVertexWorldHeightExactlyOnce() {
        String source = "uint helper;\nvoid main() {\n    uint cornerId = gl_VertexID&3;\n}\n";
        String patched = NetherSliceShaderPatcher.patch("voxy:lod/gl46/quads3.vert", source);
        assertTrue(patched.contains("voxymapWorldY = voxymapPoint.y + float(baseSectionPos.y << 5)"));
        assertEquals(patched, NetherSliceShaderPatcher.patch("voxy:lod/gl46/quads3.vert", patched));
    }

    @Test
    void patchesFragmentWithInterpolatedHeightDiscard() {
        String source = "void main() {\n}\n";
        String patched = NetherSliceShaderPatcher.patch("voxy:lod/gl46/quads.frag", source);
        assertTrue(patched.contains("voxymapWorldY > voxymapSliceY"));
        assertTrue(patched.contains("discard;"));
    }

    @Test
    void leavesOtherShadersUntouched() {
        String source = "void main() {}";
        assertEquals(source, NetherSliceShaderPatcher.patch("voxy:post/noop.frag", source));
    }
}
