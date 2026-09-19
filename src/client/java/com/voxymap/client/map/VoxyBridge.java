package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.client.config.VoxyConfig;

import java.lang.reflect.Field;

public final class VoxyBridge {
    private static final Field FOG_FIELD = resolveFogField();
    private static final boolean STRING_FOG_MODE = FOG_FIELD != null && FOG_FIELD.getType() == String.class;

    private static boolean previousCaptured;
    private static Object previousFogValue;
    private static boolean accessFailureLogged;

    private VoxyBridge() {
    }

    public static void suppressEnvironmentalFogForMap() {
        if (FOG_FIELD == null) return;
        if (!VoxyMapSettings.fogSuppressionActive()) {
            restoreEnvironmentalFogAfterMap();
            return;
        }

        try {
            VoxyConfig config = VoxyConfig.CONFIG;
            if (config == null) return;
            Object current = FOG_FIELD.get(config);
            if (!previousCaptured) {
                previousFogValue = current;
                previousCaptured = true;
            }
            Object disabled = STRING_FOG_MODE ? "off" : Boolean.FALSE;
            if (!disabled.equals(current)) {
                FOG_FIELD.set(config, disabled);
            }
        } catch (IllegalAccessException | RuntimeException exception) {
            logAccessFailure(exception);
        }
    }

    public static void restoreEnvironmentalFogAfterMap() {
        if (!previousCaptured || FOG_FIELD == null) return;

        try {
            VoxyConfig config = VoxyConfig.CONFIG;
            if (config != null) {
                FOG_FIELD.set(config, previousFogValue);
            }
        } catch (IllegalAccessException | RuntimeException exception) {
            logAccessFailure(exception);
        } finally {
            previousCaptured = false;
            previousFogValue = null;
        }
    }

    private static Field resolveFogField() {
        for (String name : new String[]{"useEnvironmentalFog", "fogMode"}) {
            try {
                return VoxyConfig.class.getField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        VoxyMapClient.LOGGER.info("[VoxyMap] Voxy has no configurable environmental fog field; no override is needed.");
        return null;
    }

    private static void logAccessFailure(Exception exception) {
        if (!accessFailureLogged) {
            accessFailureLogged = true;
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not temporarily change Voxy fog settings.", exception);
        }
    }
}
