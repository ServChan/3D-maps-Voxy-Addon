package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;

/** Adds the cached world-space emitter chroma to Solas' Voxy-only block-light path. */
public final class SolasColoredLightCachePatcher {
    private static final String SENTINEL = "uniform sampler2D voxymapColoredLightCache;";
    private static final String MARKER = "    vec3 blockLighting = blockLightCol * blockLightMap * (1.0 - emission);";
    private static boolean mismatchLogged;

    private SolasColoredLightCachePatcher() {
    }

    public static String patchSafely(String source) {
        try {
            return patch(source);
        } catch (IllegalStateException exception) {
            if (!mismatchLogged) {
                mismatchLogged = true;
                VoxyMapClient.LOGGER.info("[VoxyMap] Solas lighting layout was not recognized; RGB cache is disabled.");
            }
            return source;
        }
    }

    public static String patch(String source) {
        if (source.contains(SENTINEL)) return source;
        if (!source.contains(MARKER)) throw new IllegalStateException("Unsupported Solas lighting shader layout");
        String declarations = """
                layout(binding = %d) uniform sampler2D voxymapColoredLightCache;
                uniform int voxymapColoredLightEnabled;
                uniform vec2 voxymapColoredLightOrigin;
                uniform float voxymapColoredLightSpan;

                """.formatted(ColoredLightTexture.TEXTURE_UNIT);
        String application = """

                    #if defined VOXY_OPAQUE || defined VOXY_TRANSLUCENT
                    if (voxymapColoredLightEnabled != 0) {
                        vec2 voxymapAbsoluteXZ = (worldPos + cameraPosition).xz;
                        vec2 voxymapCacheUv = (voxymapAbsoluteXZ - voxymapColoredLightOrigin) / voxymapColoredLightSpan;
                        if (all(greaterThanEqual(voxymapCacheUv, vec2(0.0))) && all(lessThanEqual(voxymapCacheUv, vec2(1.0)))) {
                            vec3 voxymapCachedLight = texture(voxymapColoredLightCache, voxymapCacheUv).rgb;
                            float voxymapCachedPeak = maxOf(voxymapCachedLight);
                            if (voxymapCachedPeak > 0.01) {
                                vec3 voxymapChroma = voxymapCachedLight / voxymapCachedPeak;
                                float voxymapScalarLight = maxOf(blockLighting);
                                float voxymapBlend = smoothstep(0.08, 0.42, lightmap.x) * 0.92;
                                blockLighting = fmix(blockLighting, voxymapChroma * voxymapScalarLight, voxymapBlend);
                            }
                        }
                    }
                    #endif
                """;
        return declarations + source.replace(MARKER, MARKER + application);
    }
}
