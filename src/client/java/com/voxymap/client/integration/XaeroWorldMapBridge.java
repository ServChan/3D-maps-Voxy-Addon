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
    private static final Accessor ACCESSOR = resolveAccessor();

    private XaeroWorldMapBridge() {
    }

    public static boolean isAvailable() {
        return ACCESSOR != null;
    }

    public static boolean openWorldMap(Minecraft client) {
        if (!isAvailable() || client == null || client.player == null) {
            return false;
        }

        try {
            Object session = ACCESSOR.getCurrentSession.invoke(null);
            if (session == null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map session is not ready yet.");
                return false;
            }

            Object mapProcessor = ACCESSOR.getMapProcessor.invoke(session);
            if (mapProcessor == null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map session has no active map processor.");
                return false;
            }

            Constructor<?> constructor = findGuiMapConstructor(
                    ACCESSOR.constructors, mapProcessor.getClass(), client.player.getClass());
            if (constructor == null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map GUI constructor is incompatible.");
                return false;
            }

            Object screen = constructor.newInstance(null, null, mapProcessor, client.player);
            MinecraftCompat.showScreen(client, (Screen) screen);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            // Reflection into a soft (optional) dependency must never crash the render loop: an
            // incompatible Xaero build can fail newInstance() with a plain RuntimeException (e.g.
            // IllegalArgumentException on an argument-type mismatch), not just the checked exceptions.
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not open Xaero World Map from VoxyMap: {}", exception.toString());
            return false;
        }
    }

    private static Accessor resolveAccessor() {
        if (!FabricLoader.getInstance().isModLoaded(XAERO_MOD_ID)) {
            return null;
        }
        try {
            Class<?> sessionClass = Class.forName("xaero.map.WorldMapSession");
            Method getCurrentSession = sessionClass.getMethod("getCurrentSession");
            Method getMapProcessor = sessionClass.getMethod("getMapProcessor");
            Constructor<?>[] constructors = Class.forName("xaero.map.gui.GuiMap").getConstructors();
            return new Accessor(getCurrentSession, getMapProcessor, constructors);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("[VoxyMap] Xaero World Map is installed but its API is incompatible: {}",
                    exception.toString());
            return null;
        }
    }

    private static Constructor<?> findGuiMapConstructor(Constructor<?>[] constructors,
                                                         Class<?> processorClass, Class<?> playerClass) {
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 4
                    && Screen.class.isAssignableFrom(parameters[0])
                    && Screen.class.isAssignableFrom(parameters[1])
                    && parameters[2].isAssignableFrom(processorClass)
                    && parameters[3].isAssignableFrom(playerClass)) {
                return constructor;
            }
        }
        return null;
    }

    private record Accessor(Method getCurrentSession, Method getMapProcessor,
                            Constructor<?>[] constructors) {
    }
}
