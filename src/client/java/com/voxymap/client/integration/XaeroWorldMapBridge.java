package com.voxymap.client.integration;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.compat.MinecraftCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class XaeroWorldMapBridge {
    private static final String XAERO_MOD_ID = "xaeroworldmap";

    private XaeroWorldMapBridge() {
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(XAERO_MOD_ID);
    }

    public static boolean openWorldMap(Minecraft client) {
        if (!isAvailable() || client == null || client.player == null) {
            return false;
        }

        try {
            Class<?> sessionClass = Class.forName("xaero.map.WorldMapSession");
            Method getCurrentSession = sessionClass.getMethod("getCurrentSession");
            Object session = getCurrentSession.invoke(null);
            if (session == null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map session is not ready yet.");
                return false;
            }

            Object mapProcessor = sessionClass.getMethod("getMapProcessor").invoke(session);
            if (mapProcessor == null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map session has no active map processor.");
                return false;
            }

            Class<?> guiMapClass = Class.forName("xaero.map.gui.GuiMap");
            Constructor<?> constructor = findGuiMapConstructor(guiMapClass, mapProcessor.getClass());
            if (constructor == null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map GUI constructor is incompatible.");
                return false;
            }

            Object screen = constructor.newInstance(null, null, mapProcessor, client.player);
            MinecraftCompat.showScreen(client, (Screen) screen);
            return true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not open Xaero World Map from VoxyMap: {}", exception.toString());
            return false;
        }
    }

    private static Constructor<?> findGuiMapConstructor(Class<?> guiMapClass, Class<?> processorClass) {
        for (Constructor<?> constructor : guiMapClass.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 4
                    && Screen.class.isAssignableFrom(parameters[0])
                    && Screen.class.isAssignableFrom(parameters[1])
                    && parameters[2].isAssignableFrom(processorClass)) {
                return constructor;
            }
        }
        return null;
    }
}
