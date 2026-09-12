package com.voxymap.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.voxymap.client.VoxyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class MinecraftCompat {
    private static final ScreenAccessor SCREEN_ACCESSOR = resolveScreenAccessor();
    private static final RenderTargetAccessor RENDER_TARGET_ACCESSOR = resolveRenderTargetAccessor();

    private MinecraftCompat() {
    }

    public static Screen currentScreen(Minecraft minecraft) {
        try {
            return SCREEN_ACCESSOR.get(minecraft);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            VoxyMapClient.LOGGER.debug("[VoxyMap] Could not read the active screen.", unwrap(exception));
            return null;
        }
    }

    public static void showScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreenAndShow(screen);
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        try {
            return RENDER_TARGET_ACCESSOR.get(minecraft);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException("Unsupported Minecraft render-target API", unwrap(exception));
        }
    }

    private static ScreenAccessor resolveScreenAccessor() {
        try {
            Field field = Minecraft.class.getField("screen");
            return minecraft -> (Screen) field.get(minecraft);
        } catch (NoSuchFieldException ignored) {
            try {
                Method method = Class.forName("net.minecraft.client.gui.Gui").getMethod("screen");
                return minecraft -> (Screen) method.invoke(minecraft.gui);
            } catch (ReflectiveOperationException exception) {
                VoxyMapClient.LOGGER.error("[VoxyMap] Unsupported Minecraft screen API.", exception);
                return minecraft -> null;
            }
        }
    }

    private static RenderTargetAccessor resolveRenderTargetAccessor() {
        try {
            Method method = Minecraft.class.getMethod("getMainRenderTarget");
            return minecraft -> (RenderTarget) method.invoke(minecraft);
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = Class.forName("net.minecraft.client.renderer.GameRenderer").getMethod("mainRenderTarget");
                return minecraft -> (RenderTarget) method.invoke(minecraft.gameRenderer);
            } catch (ReflectiveOperationException exception) {
                VoxyMapClient.LOGGER.error("[VoxyMap] Unsupported Minecraft render-target API.", exception);
                return minecraft -> {
                    throw new NoSuchMethodException("No compatible main render target accessor");
                };
            }
        }
    }

    private static Throwable unwrap(Exception exception) {
        return exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : exception;
    }

    @FunctionalInterface
    private interface ScreenAccessor {
        Screen get(Minecraft minecraft) throws ReflectiveOperationException;
    }

    @FunctionalInterface
    private interface RenderTargetAccessor {
        RenderTarget get(Minecraft minecraft) throws ReflectiveOperationException;
    }
}
