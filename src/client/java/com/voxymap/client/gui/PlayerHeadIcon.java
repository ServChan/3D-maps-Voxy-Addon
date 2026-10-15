package com.voxymap.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

public final class PlayerHeadIcon {
    private PlayerHeadIcon() {
    }

    public static Identifier resolveSkinTexture(Minecraft minecraft, UUID uuid) {
        if (minecraft == null || uuid == null || minecraft.getConnection() == null) {
            return null;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
        if (info == null) {
            return null;
        }
        PlayerSkin skin = info.getSkin();
        return skin == null || skin.body() == null ? null : skin.body().texturePath();
    }

    public static boolean isDefaultSkin(UUID uuid, Identifier texture) {
        PlayerSkin fallback = DefaultPlayerSkin.get(uuid);
        return fallback != null && fallback.body() != null && fallback.body().texturePath().equals(texture);
    }

    public static void draw(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size) {
        draw(g, skin, x, y, size, 0xFFFFFFFF);
    }

    public static void draw(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size, int argbTint) {

        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f,
                size, size, 8, 8, 64, 64, argbTint);
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f,
                size, size, 8, 8, 64, 64, argbTint);
    }
}
