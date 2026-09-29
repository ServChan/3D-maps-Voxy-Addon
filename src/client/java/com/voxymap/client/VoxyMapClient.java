package com.voxymap.client;

import com.voxymap.client.gui.MapScreen;
import com.voxymap.client.compat.MinecraftCompat;
import com.voxymap.client.map.AtomicPropertiesWriter;
import com.voxymap.client.map.DeathWaypointTracker;
import com.voxymap.client.map.MapShareClient;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.MobMarkerCache;
import com.voxymap.client.map.OreMaskController;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WaypointBeaconRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxyMapClient implements ClientModInitializer {

    public static final String MOD_ID = "voxymap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping openMapKey;
    private static KeyMapping minimapZoomKey;

    private static final int HUD_HOOK_WATCHDOG_TICKS = 100;
    private static int ticksInWorld = -1;
    private static boolean hudHookWatchdogReported = false;

    @Override
    public void onInitializeClient() {
        VoxyMapSettings.load();
        openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.open_map", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY));
        minimapZoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.minimap_zoom", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY));
        WaypointBeaconRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMapKey.consumeClick()) {
                if (MinecraftCompat.currentScreen(client) != null || client.level == null || client.player == null) {
                    continue;
                }
                MinecraftCompat.showScreen(client, new MapScreen());
            }

            MapShareClient.stopIfWorldChanged(client);

            OreMaskController.tick(client);
            DeathWaypointTracker.tick(client);
            MobMarkerCache.tick(client);
            watchHudHook(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AtomicPropertiesWriter.flush());

        LOGGER.info("[VoxyMap] Loaded. Press M to open the map.");
    }

    private static void watchHudHook(Minecraft client) {
        if (client.level == null || client.player == null) {
            ticksInWorld = -1;
            return;
        }
        if (hudHookWatchdogReported || MinimapRenderer.hudHookFired()) {
            return;
        }
        if (ticksInWorld < 0) {
            ticksInWorld = 0;
            return;
        }
        ticksInWorld++;
        if (ticksInWorld >= HUD_HOOK_WATCHDOG_TICKS) {
            hudHookWatchdogReported = true;
            LOGGER.error("[VoxyMap] Minimap HUD hook never fired after {} ticks in-world - HudMixin failed to "
                    + "apply (Minecraft update or mapping mismatch?); the minimap overlay will not render.",
                    HUD_HOOK_WATCHDOG_TICKS);
        }
    }

    public static boolean isMinimapZoomHeld() {
        return minimapZoomKey != null && minimapZoomKey.isDown();
    }
}
