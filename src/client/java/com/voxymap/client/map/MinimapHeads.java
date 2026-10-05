package com.voxymap.client.map;

import net.minecraft.client.Minecraft;

public final class MinimapHeads {
    private MinimapHeads() {
    }

    public static boolean shown(Minecraft minecraft) {
        return switch (VoxyMapSettings.radarIcons()) {
            case HEADS -> true;
            case DOTS -> false;
            case HEADS_ON_TAB -> minecraft != null && minecraft.options != null && minecraft.options.keyPlayerList.isDown();
        };
    }
}
