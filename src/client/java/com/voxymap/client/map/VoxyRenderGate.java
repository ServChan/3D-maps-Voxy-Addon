package com.voxymap.client.map;

import net.minecraft.client.renderer.fog.FogData;

public final class VoxyRenderGate {
    static final int HIDDEN_FRAME_STRIDE = 30;
    private static int hiddenFrames;
    private static boolean fogSaved;
    private static float environmentalStart;
    private static float environmentalEnd;
    private static float renderDistanceStart;
    private static float renderDistanceEnd;

    private VoxyRenderGate() {
    }

    public static boolean voxyVisible() {
        if (MapViewMode.flat()) {
            return false;
        }
        return VoxyMapCameraController.isActive() || WindowMapRenderer.rendering() || VoxyMapSettings.voxyInWorld();
    }

    public static boolean skipHiddenFrame() {
        if (voxyVisible()) {
            hiddenFrames = 0;
            return false;
        }
        return hiddenFrames++ % HIDDEN_FRAME_STRIDE != 0;
    }

    public static void rememberFog(FogData data) {
        if (data == null) {
            return;
        }
        environmentalStart = data.environmentalStart;
        environmentalEnd = data.environmentalEnd;
        renderDistanceStart = data.renderDistanceStart;
        renderDistanceEnd = data.renderDistanceEnd;
        fogSaved = true;
    }

    public static void restoreFog(FogData data) {
        if (data != null && fogSaved && !voxyVisible()) {
            data.environmentalStart = environmentalStart;
            data.environmentalEnd = environmentalEnd;
            data.renderDistanceStart = renderDistanceStart;
            data.renderDistanceEnd = renderDistanceEnd;
        }
        fogSaved = false;
    }
}
