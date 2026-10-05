package com.voxymap.client;

import com.voxymap.client.gui.WaypointEditScreen;
import com.voxymap.client.gui.WaypointListScreen;
import com.voxymap.client.gui.WindowMapScreen;
import com.voxymap.client.gui.MapScreen;
import com.voxymap.client.integration.SpatialGuiCompat;
import com.voxymap.client.compat.MinecraftCompat;
import com.voxymap.client.map.AtomicPropertiesWriter;
import com.voxymap.client.map.DeathWaypointTracker;
import com.voxymap.client.map.DimensionLinks;
import com.voxymap.client.map.MapShareClient;
import com.voxymap.client.map.MinimapOverlays;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.MobMarkerCache;
import com.voxymap.client.map.OreMaskController;
import com.voxymap.client.map.TemporaryWaypoints;
import com.voxymap.client.map.WaypointChatShare;
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
    private static KeyMapping minimapZoomInKey;
    private static KeyMapping minimapZoomOutKey;
    private static KeyMapping addWaypointKey;
    private static KeyMapping waypointListKey;
    private static KeyMapping temporaryWaypointKey;

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
        minimapZoomInKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.minimap_zoom_in", InputConstants.Type.KEYBOARD, InputConstants.KEY_EQUALS, CATEGORY));
        minimapZoomOutKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.minimap_zoom_out", InputConstants.Type.KEYBOARD, InputConstants.KEY_MINUS, CATEGORY));
        addWaypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.add_waypoint", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY));
        waypointListKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.waypoint_list", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));
        temporaryWaypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.temporary_waypoint", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        WaypointBeaconRenderer.register();
        WaypointChatShare.register();
        MinimapOverlays.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMapKey.consumeClick()) {
                if (MinecraftCompat.currentScreen(client) != null || client.level == null || client.player == null) {
                    continue;
                }
                MinecraftCompat.showScreen(client, SpatialGuiCompat.opensFlatMap() ? new WindowMapScreen() : new MapScreen());
            }

            while (minimapZoomInKey.consumeClick()) {
                VoxyMapSettings.zoomMinimap(1);
            }
            while (minimapZoomOutKey.consumeClick()) {
                VoxyMapSettings.zoomMinimap(-1);
            }

            while (addWaypointKey.consumeClick()) {
                String worldId = MinimapRenderer.currentWorldId(client);
                if (MinecraftCompat.currentScreen(client) == null && client.player != null && worldId != null) {
                    MinecraftCompat.showScreen(client, WaypointEditScreen.create(null, worldId,
                            client.player.getX(), client.player.getY(), client.player.getZ()));
                }
            }
            while (waypointListKey.consumeClick()) {
                if (MinecraftCompat.currentScreen(client) == null && client.player != null) {
                    MinecraftCompat.showScreen(client, new WaypointListScreen(null, MinimapRenderer.currentWorldId(client)));
                }
            }
            while (temporaryWaypointKey.consumeClick()) {
                if (MinecraftCompat.currentScreen(client) == null) {
                    TemporaryWaypoints.placeAtLookTarget(client);
                }
            }
            DimensionLinks.tick(client);
            TemporaryWaypoints.tick(client);
            WaypointChatShare.tick(client);

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
