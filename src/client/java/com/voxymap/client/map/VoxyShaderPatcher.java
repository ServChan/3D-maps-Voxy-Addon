package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;






public final class VoxyShaderPatcher {
    public static final int ENABLED_UNIFORM_LOCATION = 30;
    public static final int GLOBAL_CUTOFF_UNIFORM_LOCATION = 33;
    public static final int APERTURE_CENTER_X_UNIFORM_LOCATION = 34;
    public static final int APERTURE_CENTER_Z_UNIFORM_LOCATION = 35;
    public static final int APERTURE_INNER_CUTOFF_UNIFORM_LOCATION = 36;
    public static final int APERTURE_RADIUS_UNIFORM_LOCATION = 37;
    public static final int ORE_MASK_ENABLED_UNIFORM_LOCATION = 38;

    private static final String VERTEX_DECLARATIONS = """
            layout(location = 13) out flat float voxymapQuadMinY;
            layout(location = 14) out flat vec2 voxymapQuadCenterXZ;

            """;
    private static final String FRAGMENT_DECLARATIONS = """
            layout(location = 13) in flat float voxymapQuadMinY;
            layout(location = 14) in flat vec2 voxymapQuadCenterXZ;
            layout(location = 30) uniform int voxymapCutawayEnabled;
            layout(location = 33) uniform float voxymapGlobalCutawayY;
            layout(location = 34) uniform float voxymapApertureCenterX;
            layout(location = 35) uniform float voxymapApertureCenterZ;
            layout(location = 36) uniform float voxymapApertureInnerY;
            layout(location = 37) uniform float voxymapApertureRadius;
            layout(location = 38) uniform int voxymapOreMaskEnabled;
            layout(binding = %d) uniform sampler2D voxymapOreMask;

            """.formatted(OreMaskTexture.TEXTURE_UNIT);
    private static final String VERTEX_ASSIGNMENT = """
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


                if (voxymapCutawayEnabled != 0) {
                    float voxymapRoofCutY = voxymapGlobalCutawayY;
                    if (voxymapApertureRadius > 0.0) {
                        float voxymapDistance = distance(voxymapQuadCenterXZ,
                            vec2(voxymapApertureCenterX, voxymapApertureCenterZ));
                        float voxymapApertureBlend = smoothstep(
                            voxymapApertureRadius * 0.55, voxymapApertureRadius, voxymapDistance);
                        voxymapRoofCutY = mix(voxymapApertureInnerY,
                            voxymapGlobalCutawayY, voxymapApertureBlend);
                    }
                    if (voxymapGlobalCutawayY < %s && voxymapQuadMinY >= voxymapRoofCutY) {
                        discard;
                        return;
                    }
                }

                if (voxymapOreMaskEnabled != 0) {
                    uint voxymapModelId = getModelId();
                    ivec2 voxymapOreTexel = ivec2(voxymapModelId & 0xFFu, (voxymapModelId >> 8) & 0xFFu);
                    if (texelFetch(voxymapOreMask, voxymapOreTexel, 0).r > 0.5) {
                        colour = vec4(0.55, 0.55, 0.55, colour.a);
                    }
                }""".formatted(Float.toString(NetherRoofState.NO_CUT_VALUE));

    private static volatile boolean unsupported = false;

    private VoxyShaderPatcher() {
    }


    public static boolean isSupported() {
        return !unsupported;
    }






    public static String patchSafely(String resource, String source) {
        try {
            return patch(resource, source);
        } catch (IllegalStateException exception) {
            unsupported = true;
            VoxyMapClient.LOGGER.warn(
                    "Could not patch Voxy shader {}; Nether roof removal and ore masking are off", resource, exception);
            return source;
        }
    }

    public static String patch(String resource, String source) {
        if (source == null) {
            return source;
        }
        if ("voxy:lod/gl46/quads3.vert".equals(resource)) {
            return source.contains("voxymapQuadMinY") ? source : patchVertex(source);
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



        return withDeclarations.replace(FRAGMENT_CLIP_ANCHOR, FRAGMENT_CLIP_ANCHOR + FRAGMENT_CLIP);
    }
}
