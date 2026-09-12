package com.voxymap.client.map;

public final class NetherSliceShaderPatcher {
    public static final int ENABLED_UNIFORM_LOCATION = 30;
    public static final int HEIGHT_UNIFORM_LOCATION = 31;

    private static final String VERTEX_DECLARATIONS = """
            layout(location = 12) out float voxymapWorldY;
            layout(location = 30) uniform int voxymapSliceEnabled;
            layout(location = 31) uniform float voxymapSliceY;

            """;
    private static final String FRAGMENT_DECLARATIONS = """
            layout(location = 12) in float voxymapWorldY;
            layout(location = 30) uniform int voxymapSliceEnabled;
            layout(location = 31) uniform float voxymapSliceY;

            """;
    private static final String VERTEX_ASSIGNMENT = """
                vec2 voxymapCornerMask = vec2((cornerId >> 1) & 1u, cornerId & 1u) * quad.lodScale;
                vec3 voxymapPoint = quad.basePoint + swizzelDataAxis(quad.axis, vec3(quad.quadSizeAddin * voxymapCornerMask, 0));
                voxymapWorldY = voxymapPoint.y + float(baseSectionPos.y << 5);
            """;
    private static final String FRAGMENT_CLIP = """
                if (voxymapSliceEnabled != 0 && voxymapWorldY > voxymapSliceY) {
                    discard;
                    return;
                }
            """;

    private NetherSliceShaderPatcher() {
    }

    public static String patch(String resource, String source) {
        if (source == null || source.contains("voxymapSliceEnabled")) {
            return source;
        }
        if ("voxy:lod/gl46/quads3.vert".equals(resource)) {
            return patchVertex(source);
        }
        if ("voxy:lod/gl46/quads.frag".equals(resource)) {
            return patchFragment(source);
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
        if (!source.contains(mainMarker)) {
            throw new IllegalStateException("Unsupported Voxy fragment shader layout");
        }
        return source.replace(mainMarker, FRAGMENT_DECLARATIONS + mainMarker + "\n" + FRAGMENT_CLIP);
    }
}
