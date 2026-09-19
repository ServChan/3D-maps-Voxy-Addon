package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

import java.util.List;
import java.util.Map;

/**
 * Makes the "Fog suppression" setting work with Complementary. With a shader pack loaded, Iris draws
 * fog inside the pack's own shaders, so neither Voxy's fog option nor vanilla's fog buffer has any
 * effect on it. Complementary measures every kind of fog from the camera, and the detached map camera
 * sits hundreds of blocks from the terrain it shows:
 * <ul>
 *   <li>border fog: in the Nether it is capped at {@code NETHER_VIEW_LIMIT} (256 blocks), which fully
 *       fogs every Voxy pixel and turns the screen flat orange; elsewhere it follows the render distance;</li>
 *   <li>atmospheric fog (Overworld and End): a distance haze that is heavy at map distances;</li>
 *   <li>cave fog, bloom fog and the Nether storm volume: further distance-based tints and brightening.</li>
 * </ul>
 * Each is scaled to zero while {@code voxymapMapView} is 1 (the map is open with fog suppression on).
 * Outside the map the uniform is 0, so the normal shader view is byte-for-byte unchanged.
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

    private record Edit(String anchor, String replacement) {
    }

    // The threshold check is the last thing both DoBorderFog and DoAtmosphericFog do to their fog amount
    // before blending it in, so scaling right in front of it covers all three dimensions at once. The
    // anchor occurs in both functions and both get the scaling.
    private static final String FOG_THRESHOLD = "if (fog > 0.01 + 0.0025 * dither) {";
    private static final String CAVE_FOG_BLEND = "color = mix(color, vec4(caveFogColor, 0.0), fog);";
    private static final String BLOOM_FOG_RESULT = "return 1.0 + bloomFog * bloomFogMult;";
    private static final String NETHER_STORM_GUARD = "if (isEyeInWater != 0) return vec4(0.0);";

    private static final Map<String, List<Edit>> EDITS_BY_PATH = Map.of(
            MAIN_FOG_PATH, List.of(
                    new Edit(FOG_THRESHOLD, "fog *= " + NO_FOG_IN_MAP + ";\n        " + FOG_THRESHOLD),
                    new Edit(CAVE_FOG_BLEND, "fog *= " + NO_FOG_IN_MAP + ";\n        " + CAVE_FOG_BLEND)),
            BLOOM_FOG_PATH, List.of(
                    new Edit(BLOOM_FOG_RESULT, "return 1.0 + bloomFog * bloomFogMult * (" + NO_FOG_IN_MAP + ");")),
            NETHER_STORM_PATH, List.of(
                    new Edit(NETHER_STORM_GUARD,
                            "if (isEyeInWater != 0 || voxymapMapView > 0.5) return vec4(0.0);")));

    private static boolean mismatchLogged;

    private ComplementaryMapFogPatcher() {
    }

    /** Whether {@code path} is one of the includes this patcher rewrites. */
    public static boolean handles(String path) {
        return EDITS_BY_PATH.containsKey(path);
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
        List<Edit> edits = EDITS_BY_PATH.get(path);
        if (edits == null || source.contains(SENTINEL)) {
            return source;
        }
        // Check every anchor before touching anything so a partly recognised layout is left untouched
        // rather than half patched.
        for (Edit edit : edits) {
            if (!source.contains(edit.anchor())) {
                throw new IllegalStateException("Unsupported Complementary fog shader layout: " + path);
            }
        }
        String patched = source;
        for (Edit edit : edits) {
            patched = patched.replace(edit.anchor(), edit.replacement());
        }
        return UNIFORM_DECLARATION + "\n" + patched;
    }
}
