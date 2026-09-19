package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes the "Fog suppression" setting work with the shader packs in use. With a shader pack loaded, Iris
 * draws fog inside the pack's own shaders, so neither Voxy's fog option nor vanilla's fog buffer has any
 * effect on it, and every pack measures its fog from the camera. The detached map camera sits hundreds of
 * blocks from the terrain it shows, so without this everything is fogged: in the Nether it is a flat
 * orange screen, elsewhere a heavy distance haze.
 *
 * <p>Each supported pack has its fog code in a small include that Iris hands to
 * {@code IncludeProcessor.getIncludedFile}; this class rewrites those includes so the fog does nothing
 * while the {@code voxymapMapView} uniform is 1 (map open with suppression active). Outside the map the
 * uniform is 0 and every patched function behaves exactly as before.
 *
 * <p>Two kinds of edit are used. {@link Replace} scales a fog amount right in front of where it is blended
 * in (Complementary and Rethinking Voxels, where several functions share one blend check).
 * {@link EntryReturn} makes a whole fog function return "no fog" as its first statement, which is more
 * robust for packs whose fog is a single function (Solas, BSL, Mellow, Photon). Supported: Complementary
 * Reimagined/Unbound, Rethinking Voxels, Solas, BSL, Mellow and Photon. A pack whose layout is not
 * recognised is left untouched.
 */
public final class ShaderPackFogPatcher {
    private static final String UNIFORM_DECLARATION = """
            #ifndef VOXYMAP_MAP_VIEW_UNIFORM
            #define VOXYMAP_MAP_VIEW_UNIFORM
            uniform float voxymapMapView;
            #endif
            """;
    private static final String SENTINEL = "VOXYMAP_MAP_VIEW_UNIFORM";
    private static final String NO_FOG_IN_MAP = "1.0 - clamp(voxymapMapView, 0.0, 1.0)";
    private static final String IN_MAP = "voxymapMapView > 0.5";

    private interface Edit {
        boolean matches(String source);

        String apply(String source);
    }

    /** Replaces every occurrence of {@code anchor}; at least one must exist. */
    private record Replace(String anchor, String replacement) implements Edit {
        @Override
        public boolean matches(String source) {
            return source.contains(anchor);
        }

        @Override
        public String apply(String source) {
            return source.replace(anchor, replacement);
        }
    }

    /** Inserts {@code statement} as the first statement of the function whose header starts with {@code prefix}. */
    private record EntryReturn(String prefix, String statement) implements Edit {
        private int bodyStart(String source) {
            int header = source.indexOf(prefix);
            if (header < 0) {
                return -1;
            }
            int brace = source.indexOf('{', header + prefix.length());
            return brace < 0 ? -1 : brace + 1;
        }

        @Override
        public boolean matches(String source) {
            return bodyStart(source) >= 0;
        }

        @Override
        public String apply(String source) {
            int body = bodyStart(source);
            return source.substring(0, body) + "\n    " + statement + source.substring(body);
        }
    }

    /** One pack's edits for one include; all of them have to match for the flavour to apply. */
    private record Flavor(String pack, List<Edit> edits) {
        boolean matches(String source) {
            return edits.stream().allMatch(edit -> edit.matches(source));
        }

        String apply(String source) {
            String patched = source;
            for (Edit edit : edits) {
                patched = edit.apply(patched);
            }
            return patched;
        }
    }

    private static final String NO_MAT = "return mat2x3(vec3(0.0), vec3(1.0));";
    private static final String RETURN_VOID = "if (" + IN_MAP + ") return;";

    // Complementary blends fog only if it is above a threshold; scaling in front of that check covers
    // the border fog in every dimension and the atmospheric fog in the Overworld and End.
    private static final String COMPLEMENTARY_THRESHOLD = "if (fog > 0.01 + 0.0025 * dither) {";
    private static final String COMPLEMENTARY_CAVE = "color = mix(color, vec4(caveFogColor, 0.0), fog);";
    private static final String RETHINKING_THRESHOLD = "if (fog > 0.0) {";
    private static final String RETHINKING_CAVE = "color = mix(color, caveFogColor, fog);";
    private static final String BLOOM_FOG_RESULT = "return 1.0 + bloomFog * bloomFogMult;";
    private static final String NETHER_STORM_GUARD = "if (isEyeInWater != 0) return vec4(0.0);";

    private static final Map<String, List<Flavor>> FLAVORS_BY_PATH = Map.ofEntries(
            Map.entry("/lib/atmospherics/fog/mainFog.glsl", List.of(
                    new Flavor("Complementary", List.of(
                            scaleBefore(COMPLEMENTARY_THRESHOLD), scaleBefore(COMPLEMENTARY_CAVE))),
                    new Flavor("Rethinking Voxels", List.of(
                            scaleBefore(RETHINKING_THRESHOLD), scaleBefore(RETHINKING_CAVE))))),
            Map.entry("/lib/atmospherics/fog/bloomFog.glsl", List.of(
                    new Flavor("Complementary / Rethinking Voxels", List.of(new Replace(BLOOM_FOG_RESULT,
                            "return 1.0 + bloomFog * bloomFogMult * (" + NO_FOG_IN_MAP + ");"))))),
            Map.entry("/lib/atmospherics/netherStorm.glsl", List.of(
                    new Flavor("Complementary / Rethinking Voxels", List.of(new Replace(NETHER_STORM_GUARD,
                            "if (isEyeInWater != 0 || " + IN_MAP + ") return vec4(0.0);"))))),

            Map.entry("/lib/atmosphere/fog.glsl", List.of(
                    new Flavor("Solas", List.of(new EntryReturn("void getNormalFog(", RETURN_VOID))))),
            Map.entry("/lib/atmospherics/fog.glsl", List.of(
                    new Flavor("BSL", List.of(new EntryReturn("void NormalFog(", RETURN_VOID))))),
            Map.entry("/global/fog.glsl", List.of(
                    new Flavor("Mellow", List.of(new EntryReturn("vec3 get_fog_main(",
                            "if (" + IN_MAP + ") return Color;"))))),

            Map.entry("/include/fog/simple_fog.glsl", List.of(
                    new Flavor("Photon", List.of(
                            new EntryReturn("float border_fog(", "if (" + IN_MAP + ") return 1.0;"),
                            new EntryReturn("vec4 common_fog(", "if (" + IN_MAP + ") return vec4(vec3(0.0), 1.0);"),
                            new EntryReturn("float common_fog_alpha(", "if (" + IN_MAP + ") return 1.0;"))))),
            Map.entry("/include/fog/overworld/analytic.glsl", List.of(
                    new Flavor("Photon", List.of(
                            new EntryReturn("mat2x3 air_fog_analytic(", "if (" + IN_MAP + ") " + NO_MAT))))),
            Map.entry("/include/fog/overworld/raymarched.glsl", List.of(
                    new Flavor("Photon", List.of(
                            new EntryReturn("mat2x3 raymarch_air_fog(", "if (" + IN_MAP + ") " + NO_MAT))))),
            Map.entry("/include/fog/end_fog_vl.glsl", List.of(
                    new Flavor("Photon", List.of(
                            new EntryReturn("mat2x3 raymarch_end_fog(", "if (" + IN_MAP + ") " + NO_MAT))))));

    private static Replace scaleBefore(String anchor) {
        return new Replace(anchor, "fog *= " + NO_FOG_IN_MAP + ";\n        " + anchor);
    }

    private static boolean mismatchLogged;

    private ShaderPackFogPatcher() {
    }

    /** Whether {@code path} is one of the includes this patcher rewrites. */
    public static boolean handles(String path) {
        return FLAVORS_BY_PATH.containsKey(path);
    }

    public static String patchSafely(String path, String source) {
        try {
            return patch(path, source);
        } catch (IllegalStateException exception) {
            if (!mismatchLogged) {
                mismatchLogged = true;
                VoxyMapClient.LOGGER.info(
                        "[VoxyMap] The shader pack's fog layout was not recognized ({}); leaving its fog unchanged.",
                        path);
            }
            return source;
        }
    }

    public static String patch(String path, String source) {
        List<Flavor> flavors = FLAVORS_BY_PATH.get(path);
        if (flavors == null || source.contains(SENTINEL)) {
            return source;
        }
        // A flavour applies only if every one of its anchors is present, so a partly recognised layout is
        // left untouched rather than half patched.
        List<String> tried = new ArrayList<>();
        for (Flavor flavor : flavors) {
            if (flavor.matches(source)) {
                return UNIFORM_DECLARATION + "\n" + flavor.apply(source);
            }
            tried.add(flavor.pack());
        }
        throw new IllegalStateException("Unsupported shader-pack fog layout: " + path + " (tried " + tried + ")");
    }
}
