package com.voxymap.client.map;

import com.voxymap.client.gui.MapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

public final class MapScreenshot {
    private enum Phase {
        IDLE,

        HIDING,

        CAPTURE_DUE
    }

    private static Phase phase = Phase.IDLE;
    private static boolean keepMarkers;
    private static boolean capturing;

    private MapScreenshot() {
    }

    public static synchronized boolean begin(boolean mapOpen, boolean keepMarkers) {
        if (capturing || !mapOpen) {
            return false;
        }
        if (phase == Phase.IDLE) {
            phase = Phase.HIDING;
            MapScreenshot.keepMarkers = keepMarkers;
        }

        return true;
    }

    public static synchronized boolean afterGuiExtracted(boolean mapOpen) {
        if (phase == Phase.IDLE) {
            return false;
        }
        if (!mapOpen) {

            phase = Phase.IDLE;
            return false;
        }
        phase = Phase.CAPTURE_DUE;
        return !keepMarkers;
    }

    public static synchronized boolean markersOnlyFrame() {
        return phase == Phase.HIDING && keepMarkers;
    }

    public static synchronized boolean takeDueCapture(boolean mapOpen) {
        if (phase != Phase.CAPTURE_DUE) {
            return false;
        }
        phase = Phase.IDLE;
        return mapOpen;
    }

    public static void captureIfDue(Minecraft minecraft) {
        if (!takeDueCapture(isMapOpen(minecraft))) {
            return;
        }
        capturing = true;
        try {
            Screenshot.grab(minecraft, false);
        } finally {
            capturing = false;
        }
    }

    public static boolean isMapOpen(Minecraft minecraft) {
        return minecraft != null && minecraft.gui.screen() instanceof MapScreen;
    }

    static synchronized void resetForTest() {
        phase = Phase.IDLE;
        capturing = false;
    }
}
