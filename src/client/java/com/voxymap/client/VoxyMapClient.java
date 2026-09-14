package com.voxymap.client;

import com.voxymap.client.gui.MapScreen;
import com.voxymap.client.compat.MinecraftCompat;
import com.voxymap.client.map.VoxyMapSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxyMapClient implements ClientModInitializer {

    public static final String MOD_ID = "voxymap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping openMapKey;
    private static KeyMapping minimapZoomKey;

    @Override
    public void onInitializeClient() {
        VoxyMapSettings.load();
        openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        minimapZoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voxymap.minimap_zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMapKey.consumeClick()) {
                if (MinecraftCompat.currentScreen(client) != null || client.level == null || client.player == null) {
                    continue;
                }
                MinecraftCompat.showScreen(client, new MapScreen());
            }
        });

        LOGGER.info("[VoxyMap] Loaded. Press M to open the map.");
    }

    public static boolean isMinimapZoomHeld() {
        return minimapZoomKey != null && minimapZoomKey.isDown();
    }
}
