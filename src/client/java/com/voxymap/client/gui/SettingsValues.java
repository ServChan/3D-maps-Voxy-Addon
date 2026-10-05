package com.voxymap.client.gui;

import com.voxymap.client.map.MapOverlay;
import com.voxymap.client.map.MinimapOverlays;
import com.voxymap.client.map.OreMaskController;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.VoxyShaderPatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class SettingsValues {
    private SettingsValues() {
    }

    static boolean slimeSeedMissing() {
        return VoxyMapSettings.overlayShown(MapOverlay.SLIME_CHUNKS)
                && !MinimapOverlays.slimeSeedKnown(Minecraft.getInstance());
    }

    static String minimapZoomLabel() {
        double zoom = VoxyMapSettings.minimapPixelsPerBlock();
        return zoom == Math.rint(zoom) ? "x" + (int) zoom : "x" + zoom;
    }

    static String waypointRangeLabel() {
        VoxyMapSettings.WaypointRange range = VoxyMapSettings.waypointRange();
        return range.unlimited()
                ? Component.translatable("screen.voxymap.settings.waypointrange.unlimited").getString()
                : Component.translatable("screen.voxymap.waypoints.distance", range.blocks()).getString();
    }

    static String diagnosticsKey() {
        if (!VoxyShaderPatcher.isSupported()) {
            return "screen.voxymap.diagnostics.shader_unsupported";
        }
        if (!OreMaskController.isSupported()) {
            return "screen.voxymap.diagnostics.ore_mask_unsupported";
        }
        return "screen.voxymap.diagnostics.ok";
    }
}
