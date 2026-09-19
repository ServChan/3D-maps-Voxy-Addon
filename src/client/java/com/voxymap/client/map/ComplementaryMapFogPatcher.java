package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

import java.util.Map;

/**
 * Removes Complementary's Nether distance haze from the detached map camera. Its Nether border fog,
 * bloom fog and "Nether storm" volume are all measured from the camera and capped at
 * {@code NETHER_VIEW_LIMIT} (256 blocks by default), so from a map camera hundreds of blocks above
 * the terrain every Voxy pixel is fully fogged and the screen is one flat orange. Overworld and End
 * fog scale with the render distance instead, which Voxy extends, so they need no change.
 * The normal shader view is byte-for-byte unchanged because {@code voxymapMapView} is 0 outside the map.
 */
public final class ComplementaryMapFogPatcher {
    private static final String UNIFORM_DECLARATION = """
            #ifndef VOXYMAP_MAP_VIEW_UNIFORM
            #define VOXYMAP_MAP_VIEW_UNIFORM
            uniform float voxymapMapView;
            #endif
            """;
    private static final String SENTINEL = "VOXYMAP_MAP_VIEW_UNIFORM";
    private static final String NO_FOG_IN_MAP = "1.0 - clamp(voxymapMapView, 0.0, 1.0)";

    private static final String MAIN_FOG_PATH = "/lib/atmospherics/fog/mainFog.glsl";
    private static final String BLOOM_FOG_PATH = "/lib/atmospherics/fog/bloomFog.glsl";
    private static final String NETHER_STORM_PATH = "/lib/atmospherics/netherStorm.glsl";

    private static final String BORDER_FOG_ANCHOR =
            "fog = mix(fog, min1(lPos / min(farM, 256.0)), 0.5);";
    private static final String BLOOM_FOG_ANCHOR =
            "bloomFog *= float(isEyeInWater == 0);";
    private static final String NETHER_STORM_ANCHOR =
            "if (isEyeInWater != 0) return vec4(0.0);";

    private static final Map<String, String[]> ANCHOR_BY_PATH = Map.of(
            MAIN_FOG_PATH, new String[] {BORDER_FOG_ANCHOR,
                    BORDER_FOG_ANCHOR + "\n            fog *= " + NO_FOG_IN_MAP + ";"},
            BLOOM_FOG_PATH, new String[] {BLOOM_FOG_ANCHOR,
                    BLOOM_FOG_ANCHOR + "\n        bloomFog *= " + NO_FOG_IN_MAP + ";"},
            NETHER_STORM_PATH, new String[] {NETHER_STORM_ANCHOR,
                    "if (isEyeInWater != 0 || voxymapMapView > 0.5) return vec4(0.0);"});

    private static boolean mismatchLogged;

    private ComplementaryMapFogPatcher() {
    }

    /** Whether {@code path} is one of the includes this patcher rewrites. */
    public static boolean handles(String path) {
        return ANCHOR_BY_PATH.containsKey(path);
    }

    public static String patchSafely(String path, String source) {
        try {
            return patch(path, source);
        } catch (IllegalStateException exception) {
            if (!mismatchLogged) {
                mismatchLogged = true;
                VoxyMapClient.LOGGER.info(
                        "[VoxyMap] Complementary fog layout was not recognized; leaving shader-pack fog unchanged.");
            }
            return source;
        }
    }

    public static String patch(String path, String source) {
        String[] edit = ANCHOR_BY_PATH.get(path);
        if (edit == null || source.contains(SENTINEL)) {
            return source;
        }
        if (!source.contains(edit[0])) {
            throw new IllegalStateException("Unsupported Complementary fog shader layout: " + path);
        }
        return UNIFORM_DECLARATION + "\n" + source.replace(edit[0], edit[1]);
    }
}
