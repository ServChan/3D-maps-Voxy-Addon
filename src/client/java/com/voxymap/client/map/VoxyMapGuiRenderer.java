package com.voxymap.client.map;

import net.minecraft.client.Minecraft;

public final class VoxyMapGuiRenderer {
    private static boolean active;

    private VoxyMapGuiRenderer() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void open(Minecraft minecraft) {
        active = minecraft != null && minecraft.level != null;
    }

    public static void close(Minecraft minecraft) {
        active = false;
    }
}
