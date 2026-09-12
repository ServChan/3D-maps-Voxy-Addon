package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

/**
 * Patches Voxy's native LOD shaders with a global horizontal cut plane and an optional, more precise
 * local room mask. In the Nether the global plane removes only the top roof layer across the whole
 * map, preserving complete builds below it. The per-column texture is built by
 * {@link RoomCutawayComputer} from the connected air space around the player and may lower that plane
 * locally to reveal a room ceiling.
 */
public final class RoomCutawayShaderPatcher {
    public static final int ENABLED_UNIFORM_LOCATION = 30;
    public static final int ORIGIN_X_UNIFORM_LOCATION = 31;
    public static final int ORIGIN_Z_UNIFORM_LOCATION = 32;
    public static final int GLOBAL_CUTOFF_UNIFORM_LOCATION = 33;

    private static final String VERTEX_DECLARATIONS = """
            layout(location = 12) out vec3 voxymapWorldPos;

            """;
    private static final String FRAGMENT_DECLARATIONS = """
            layout(location = 12) in vec3 voxymapWorldPos;
            layout(location = 30) uniform int voxymapCutawayEnabled;
            layout(location = 31) uniform float voxymapCutawayOriginX;
            layout(location = 32) uniform float voxymapCutawayOriginZ;
            layout(location = 33) uniform float voxymapGlobalCutawayY;
            layout(binding = %d) uniform sampler2D voxymapRoomHeightMap;

            """.formatted(RoomCutawayTexture.TEXTURE_UNIT);
    private static final String VERTEX_ASSIGNMENT = """
                vec2 voxymapCornerMask = vec2((cornerId >> 1) & 1u, cornerId & 1u) * quad.lodScale;
                vec3 voxymapPoint = quad.basePoint + swizzelDataAxis(quad.axis, vec3(quad.quadSizeAddin * voxymapCornerMask, 0));
                voxymapWorldPos = voxymapPoint + vec3(baseSectionPos << 5);
            """;
    private static final String FRAGMENT_CLIP_ANCHOR =
            "if (DEPTH_SCALAR_COMPARE(gl_FragCoord.z, texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r)) {\n"
            + "        discard;\n"
            + "        return;\n"
            + "    }";
    private static final String FRAGMENT_CLIP = """


                if (voxymapCutawayEnabled != 0) {
                    float voxymapCutY = voxymapGlobalCutawayY;
                    ivec2 voxymapTexel = ivec2(int(floor(voxymapWorldPos.x)) - int(voxymapCutawayOriginX),
                                                int(floor(voxymapWorldPos.z)) - int(voxymapCutawayOriginZ));
                    if (voxymapTexel.x >= 0 && voxymapTexel.x < %1$d && voxymapTexel.y >= 0 && voxymapTexel.y < %1$d) {
                        voxymapCutY = min(voxymapCutY, texelFetch(voxymapRoomHeightMap, voxymapTexel, 0).r);
                    }
                    if (voxymapWorldPos.y > voxymapCutY) {
                        discard;
                        return;
                    }
                }""".formatted(RoomCutawayComputer.GRID_SIZE);

    private static volatile boolean unsupported = false;

    private RoomCutawayShaderPatcher() {
    }

    /** Whether the shaders were successfully patched; false if a Voxy update changed their layout. */
    public static boolean isSupported() {
        return !unsupported;
    }

    /**
     * Same as {@link #patch}, but never throws: a Voxy update that changes the shader layout must
     * disable the room-cutaway feature and let the rest of Voxy keep loading, not crash shader
     * compilation for the whole client.
     */
    public static String patchSafely(String resource, String source) {
        try {
            return patch(resource, source);
        } catch (IllegalStateException exception) {
            unsupported = true;
            VoxyMapClient.LOGGER.warn(
                    "[VoxyMap] Voxy shader layout changed; disabling the room-cutaway feature.", exception);
            return source;
        }
    }

    public static String patch(String resource, String source) {
        if (source == null) {
            return source;
        }
        if ("voxy:lod/gl46/quads3.vert".equals(resource)) {
            return source.contains("voxymapWorldPos") ? source : patchVertex(source);
        }
        if ("voxy:lod/gl46/quads.frag".equals(resource)) {
            return source.contains("voxymapCutawayEnabled") ? source : patchFragment(source);
        }
        return source;
    }

    static String patchVertex(String source) {
        String mainMarker = "void main() {";
        String cornerMarker = "    uint cornerId = gl_VertexID&3;";
        if (!source.contains(mainMarker) || !source.contains(cornerMarker)) {
            throw new IllegalStateException("Unsupported Voxy vertex shader layout");
        }
        return source.replace(mainMarker, VERTEX_DECLARATIONS + mainMarker)
                .replace(cornerMarker, cornerMarker + "\n\n" + VERTEX_ASSIGNMENT);
    }

    static String patchFragment(String source) {
        String mainMarker = "void main() {";
        if (!source.contains(mainMarker) || !source.contains(FRAGMENT_CLIP_ANCHOR)) {
            throw new IllegalStateException("Unsupported Voxy fragment shader layout");
        }
        String withDeclarations = source.replace(mainMarker, FRAGMENT_DECLARATIONS + mainMarker);
        // Insert after the existing depth-bounds discard, i.e. once texture derivatives
        // (dFdx/dFdy/textureGrad) have already been resolved - discarding earlier leaves derivatives
        // undefined for fragments neighbouring the cut and corrupts their mip selection/texturing.
        return withDeclarations.replace(FRAGMENT_CLIP_ANCHOR, FRAGMENT_CLIP_ANCHOR + FRAGMENT_CLIP);
    }
}
