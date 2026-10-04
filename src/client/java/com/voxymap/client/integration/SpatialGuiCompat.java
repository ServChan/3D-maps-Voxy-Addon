package com.voxymap.client.integration;

import com.voxymap.client.map.VoxyMapSettings;
import net.fabricmc.loader.api.FabricLoader;

public final class SpatialGuiCompat {
    private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("spatial-gui");

    private SpatialGuiCompat() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean opensFlatMap() {
        return AVAILABLE && VoxyMapSettings.spatialGuiFlatMap();
    }
}
