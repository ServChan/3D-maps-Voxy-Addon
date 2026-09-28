package com.voxymap.client.map;

import net.minecraft.client.Minecraft;

public final class MinimapHeads {
    private MinimapHeads() {
    }

    public static boolean shown(Minecraft minecraft) {
        return minecraft != null && minecraft.options != null && minecraft.options.keyPlayerList.isDown();
    }
}
