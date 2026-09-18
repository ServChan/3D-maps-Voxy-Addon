package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

/**
 * Patches Voxy's native LOD shaders with a whole-quad Nether roof remover and an optional, more
 * precise local room mask. Removing complete roof quads avoids manufacturing cross-sections through
 * server anti-xray decoy LODs. The per-column texture is built by
 * {@link RoomCutawayComputer} from the connected air space around the player and may lower that plane
 * locally to reveal a room ceiling.
 */
public final class RoomCutawayShaderPatcher {
    public static final int ENABLED_UNIFORM_LOCATION = 30;
    public static final int ORIGIN_X_UNIFORM_LOCATION = 31;
    public static final int ORIGIN_Z_UNIFORM_LOCATION = 32;
    public static final int GLOBAL_CUTOFF_UNIFORM_LOCATION = 33;
    public static final int APERTURE_CENTER_X_UNIFORM_LOCATION = 34;
    public static final int APERTURE_CENTER_Z_UNIFORM_LOCATION = 35;
    public static final int APERTURE_INNER_CUTOFF_UNIFORM_LOCATION = 36;
    public static final int APERTURE_RADIUS_UNIFORM_LOCATION = 37;
    public static final int ORE_MASK_ENABLED_UNIFORM_LOCATION = 38;
    public static final int SPHERE_CENTER_X_UNIFORM_LOCATION = 39;
    public static final int SPHERE_CENTER_Y_UNIFORM_LOCATION = 40;
    public static final int SPHERE_CENTER_Z_UNIFORM_LOCATION = 41;
    public static final int SPHERE_RADIUS_UNIFORM_LOCATION = 42;

    private static final String VERTEX_DECLARATIONS = """
            layout(location = 12) out vec3 voxymapWorldPos;
            layout(location = 13) out flat float voxymapQuadMinY;
            layout(location = 14) out flat vec2 voxymapQuadCenterXZ;

            """;
    private static final String FRAGMENT_DECLARATIONS = """
            layout(location = 12) in vec3 voxymapWorldPos;
            layout(location = 13) in flat float voxymapQuadMinY;
            layout(location = 14) in flat vec2 voxymapQuadCenterXZ;
            layout(location = 30) uniform int voxymapCutawayEnabled;
            layout(location = 31) uniform float voxymapCutawayOriginX;
            layout(location = 32) uniform float voxymapCutawayOriginZ;
            layout(location = 33) uniform float voxymapGlobalCutawayY;
            layout(location = 34) uniform float voxymapApertureCenterX;
            layout(location = 35) uniform float voxymapApertureCenterZ;
            layout(location = 36) uniform float voxymapApertureInnerY;
            layout(location = 37) uniform float voxymapApertureRadius;
            layout(location = 38) uniform int voxymapOreMaskEnabled;
            layout(location = 39) uniform float voxymapSphereCenterX;
            layout(location = 40) uniform float voxymapSphereCenterY;
            layout(location = 41) uniform float voxymapSphereCenterZ;
            layout(location = 42) uniform float voxymapSphereRadius;
            layout(binding = %d) uniform sampler2D voxymapRoomHeightMap;
            layout(binding = %d) uniform sampler2D voxymapOreMask;

            """.formatted(RoomCutawayTexture.TEXTURE_UNIT, OreMaskTexture.TEXTURE_UNIT);
    private static final String VERTEX_ASSIGNMENT = """
                vec2 voxymapCornerMask = vec2((cornerId >> 1) & 1u, cornerId & 1u) * quad.lodScale;
                vec3 voxymapPoint = quad.basePoint + swizzelDataAxis(quad.axis, vec3(quad.quadSizeAddin * voxymapCornerMask, 0));
                voxymapWorldPos = voxymapPoint + vec3(baseSectionPos << 5);
                voxymapQuadMinY = quad.basePoint.y + float(baseSectionPos.y << 5);
                vec3 voxymapQuadFarCorner = quad.basePoint
                    + swizzelDataAxis(quad.axis, vec3(quad.quadSizeAddin * quad.lodScale, 0));
                voxymapQuadCenterXZ = ((quad.basePoint + voxymapQuadFarCorner) * 0.5
                    + vec3(baseSectionPos << 5)).xz;
            """;
    private static final String FRAGMENT_CLIP_ANCHOR =
            "if (DEPTH_SCALAR_COMPARE(gl_FragCoord.z, texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r)) {\n"
            + "        discard;\n"
            + "        return;\n"
            + "    }";
    private static final String FRAGMENT_CLIP = """


                // TEMPORARY, maximally blunt diagnostic: paints every fragment that reaches this point
                // solid green, unconditionally - no uniform, no camera state, no Java-side logic
                // involved at all. If the in-game view does NOT turn solid green while this build is
                // running, the patched quads.frag source is conclusively not the shader actually bound
                // for these draws (a build/deploy mismatch, a shader cache, or something replacing the
                // program before the real render), regardless of anything else this file does. Remove
                // this block first once that is confirmed either way.
                colour = vec4(0.0, 1.0, 0.0, 1.0);

                if (voxymapCutawayEnabled != 0) {
                    // A simple camera-centered "reveal bubble", independent of the room-enclosure
                    // search above: always on while the map is open (voxymapSphereRadius is 0 - and
                    // this a no-op - whenever it isn't), so the player's own position stays visible
                    // from the detached map camera regardless of whether it sits in a fully enclosed
                    // pocket. Never touches anti-xray-sensitive data - it is a pure distance cut from
                    // the camera, not a guess about unverified geometry.
                    if (voxymapSphereRadius > 0.0) {
                        vec3 voxymapSphereCenter = vec3(voxymapSphereCenterX, voxymapSphereCenterY, voxymapSphereCenterZ);
                        float voxymapSphereDist = distance(voxymapWorldPos, voxymapSphereCenter);
                        if (voxymapSphereDist < voxymapSphereRadius) {
                            discard;
                            return;
                        }
                        // TEMPORARY diagnostic halo: paints a bright magenta band just outside the cut
                        // sphere instead of discarding, so "does our patched shader code run and affect
                        // the frame at all" is answerable from a single screenshot, independent of
                        // whether discard itself behaves as expected. Remove once cutaway is confirmed
                        // visible in-game.
                        if (voxymapSphereDist < voxymapSphereRadius * 1.15) {
                            colour = vec4(1.0, 0.0, 1.0, 1.0);
                        }
                    }

                    // The global Nether threshold removes only complete quads belonging to the roof
                    // band. Per-fragment clipping here would expose fake anti-xray volume as a tiled
                    // cross-section. Local verified-air columns may still use a precise clip plane.
                    float voxymapRoofCutY = voxymapGlobalCutawayY;
                    if (voxymapApertureRadius > 0.0) {
                        float voxymapDistance = distance(voxymapQuadCenterXZ,
                            vec2(voxymapApertureCenterX, voxymapApertureCenterZ));
                        float voxymapApertureBlend = smoothstep(
                            voxymapApertureRadius * 0.55, voxymapApertureRadius, voxymapDistance);
                        voxymapRoofCutY = mix(voxymapApertureInnerY,
                            voxymapGlobalCutawayY, voxymapApertureBlend);
                    }
                    if (voxymapGlobalCutawayY < %2$s && voxymapQuadMinY >= voxymapRoofCutY) {
                        discard;
                        return;
                    }
                    float voxymapCutY = %2$s;
                    ivec2 voxymapTexel = ivec2(int(floor(voxymapWorldPos.x)) - int(voxymapCutawayOriginX),
                                                int(floor(voxymapWorldPos.z)) - int(voxymapCutawayOriginZ));
                    if (voxymapTexel.x >= 0 && voxymapTexel.x < %1$d && voxymapTexel.y >= 0 && voxymapTexel.y < %1$d) {
                        voxymapCutY = texelFetch(voxymapRoomHeightMap, voxymapTexel, 0).r;
                    }
                    if (voxymapWorldPos.y > voxymapCutY) {
                        discard;
                        return;
                    }
                }

                if (voxymapOreMaskEnabled != 0) {
                    // Independent of voxymapCutawayEnabled above: an ore can be exposed by the room
                    // cutaway, by the Nether roof trim, or simply by Voxy meshing the boundary of its
                    // cached data as an ordinary opaque wall (see OreMaskController) - there is no
                    // signal here to tell those apart, so this masks every rendered ore uniformly.
                    uint voxymapModelId = getModelId();
                    ivec2 voxymapOreTexel = ivec2(voxymapModelId & 0xFFu, (voxymapModelId >> 8) & 0xFFu);
                    if (texelFetch(voxymapOreMask, voxymapOreTexel, 0).r > 0.5) {
                        colour = vec4(0.55, 0.55, 0.55, colour.a);
                    }
                }""".formatted(RoomCutawayComputer.GRID_SIZE,
                    Float.toString(RoomCutawayTexture.NO_CUT_VALUE));

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
