package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

/**
 * Makes Solas 3.7's rainy Overworld fog usable from VoxyMap's kilometre-high perspective.
 * The normal shader view is byte-for-byte unchanged because the multiplier is 1 outside the map.
 */
public final class SolasMapFogPatcher {
    private static final String SENTINEL = "uniform float voxymapMapView;";
    private static final String FUNCTION_MARKER =
            "void getNormalFog(inout vec3 color, in vec3 atmosphereColor, in vec3 viewPos, in vec3 worldPos, in float lViewPos, in float lWorldPos, in float z0) {\n";
    private static final String DISTANCE_MARKER =
            "float distanceFactor = 25.0 * (0.5 + timeBrightness) + FOG_DISTANCE * (0.75 + caveFactor * 0.25) - wetness * 25.0;";
    private static final String ALTITUDE_MARKER =
            "exp2(FOG_HEIGHT_FALLOFF + moonVisibility + timeBrightness + wetness - isJungle - isSwamp)";
    private static final String DENSITY_MARKER =
            "+ wetness * 0.25;";
    private static final String FINAL_FOG_MARKER =
            "fog = clamp(fog * density * altitude, 0.0, 1.0);";
    private static boolean mismatchLogged;

    private SolasMapFogPatcher() {
    }

    public static String patchSafely(String source) {
        try {
            return patch(source);
        } catch (IllegalStateException exception) {
            if (!mismatchLogged) {
                mismatchLogged = true;
                VoxyMapClient.LOGGER.info(
                        "[VoxyMap] Solas fog layout was not recognized; leaving shader-pack fog unchanged.");
            }
            return source;
        }
    }

    public static String patch(String source) {
        if (source.contains(SENTINEL)) {
            return source;
        }
        if (!source.contains(FUNCTION_MARKER)
                || !source.contains(DISTANCE_MARKER)
                || !source.contains(ALTITUDE_MARKER)
                || !source.contains(DENSITY_MARKER)
                || !source.contains(FINAL_FOG_MARKER)) {
            throw new IllegalStateException("Unsupported Solas fog shader layout");
        }

        String patched = SENTINEL + "\n\n" + source;
        patched = patched.replace(FUNCTION_MARKER, FUNCTION_MARKER
                + "    float voxymapView = clamp(voxymapMapView, 0.0, 1.0);\n"
                + "    float voxymapWetness = wetness * mix(1.0, 0.22, voxymapView);\n"
                + "    float voxymapRainHaze = clamp(wetness * voxymapView, 0.0, 1.0);\n");
        patched = patched.replace(DISTANCE_MARKER, DISTANCE_MARKER.replace("wetness", "voxymapWetness"));
        patched = patched.replace(ALTITUDE_MARKER, ALTITUDE_MARKER.replace("wetness", "voxymapWetness"));
        patched = patched.replace(DENSITY_MARKER, "+ voxymapWetness * 0.25;");
        patched = patched.replace(FINAL_FOG_MARKER, FINAL_FOG_MARKER
                + "\n            fog = min(fog, mix(1.0, 0.72, voxymapRainHaze));");
        return patched;
    }
}
