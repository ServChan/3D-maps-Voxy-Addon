package com.voxymap.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static Screen currentScreen(Minecraft minecraft) {
        return minecraft.gui.screen();
    }

    public static void showScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreenAndShow(screen);
    }

    public static RenderTarget mainRenderTarget(Minecraft minecraft) {
        return minecraft.gameRenderer.mainRenderTarget();
    }
}
