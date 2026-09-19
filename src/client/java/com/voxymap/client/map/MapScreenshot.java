package com.voxymap.client.map;

import com.voxymap.client.gui.MapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Turns the screenshot key into a clean map capture: while the map is open, the key hides the interface
 * for exactly one frame, saves that frame, and brings the interface back on the next one. By default every
 * 2D layer goes (map panels, hints, markers, HUD, chat, toasts) and only the world view remains; with the
 * "with markers" setting the map's own player, waypoint and territory markers stay while the panels, HUD
 * and chat are hidden.
 *
 * <p>The frame has to be captured between two frames rather than during one. Minecraft extracts the
 * whole GUI before it renders the level, so the main render target still holds the previous frame
 * while the GUI is being built; it only holds the clean frame once {@code GameRenderer.render} has
 * returned, i.e. at the start of the next {@code Minecraft.runTick}. That is the same moment vanilla
 * takes its own screenshot, so the file is identical to a normal one apart from the missing interface.
 *
 * <p>Sequence: {@link #begin} (key pressed) -> {@link #afterGuiExtracted} clears the GUI of the next
 * frame -> that frame renders -> {@link #takeDueCapture} at the start of the following tick.
 */
public final class MapScreenshot {
    private enum Phase {
        IDLE,
        /** Key pressed; the next extracted GUI is discarded. */
        HIDING,
        /** The GUI-free frame is rendered and sitting in the main render target. */
        CAPTURE_DUE
    }

    private static Phase phase = Phase.IDLE;
    private static boolean keepMarkers;
    private static boolean capturing;

    private MapScreenshot() {
    }

    /**
     * Called when vanilla is about to take a screenshot.
     *
     * @param keepMarkers whether the map's own markers stay on the capture (only read when a capture starts)
     * @return true if vanilla's screenshot must be skipped because the map is taking over
     */
    public static synchronized boolean begin(boolean mapOpen, boolean keepMarkers) {
        if (capturing || !mapOpen) {
            return false;
        }
        if (phase == Phase.IDLE) {
            phase = Phase.HIDING;
            MapScreenshot.keepMarkers = keepMarkers;
        }
        // Swallow presses made while a capture is already in flight instead of stacking a second one.
        return true;
    }

    /**
     * Called once Minecraft has finished extracting a frame's GUI.
     *
     * @return true if that GUI must be thrown away so the frame renders without any interface; false when
     *         markers are kept, because the HUD and panels were already left out while it was being built
     */
    public static synchronized boolean afterGuiExtracted(boolean mapOpen) {
        if (phase == Phase.IDLE) {
            return false;
        }
        if (!mapOpen) {
            // The map was closed between the key press and the frame; never blank the normal game UI.
            phase = Phase.IDLE;
            return false;
        }
        phase = Phase.CAPTURE_DUE;
        return !keepMarkers;
    }

    /**
     * True while the frame that will be captured is being built and it should keep the map's markers:
     * the map then draws only its markers, and the HUD and chat are skipped.
     */
    public static synchronized boolean markersOnlyFrame() {
        return phase == Phase.HIDING && keepMarkers;
    }

    /** Called at the start of every tick; true when the clean frame must be saved right now. */
    public static synchronized boolean takeDueCapture(boolean mapOpen) {
        if (phase != Phase.CAPTURE_DUE) {
            return false;
        }
        phase = Phase.IDLE;
        return mapOpen;
    }

    /** Runs the pending capture through vanilla's own screenshot path, if one is due. */
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
