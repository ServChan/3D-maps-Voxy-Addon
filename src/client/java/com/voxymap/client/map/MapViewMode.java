package com.voxymap.client.map;

public final class MapViewMode {
    private static volatile boolean flat;

    private MapViewMode() {
    }

    public static boolean flat() {
        return flat && VoxyMapCameraController.isActive();
    }

    public static void setFlat(boolean value) {
        flat = value;
    }
}
