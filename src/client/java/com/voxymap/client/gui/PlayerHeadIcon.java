package com.voxymap.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * Draws a player's face (base skin layer + hat/hair overlay) the same way the game's own tab list and
 * multiplayer server list do, using whatever skin the client already has cached for that UUID via the
 * server's tab list — no extra network fetch of our own.
 */
final class PlayerHeadIcon {
    private PlayerHeadIcon() {
    }

    static Identifier resolveSkinTexture(Minecraft minecraft, UUID uuid) {
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

    static void draw(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size) {
        // The 26.2 convenience overload takes destination bounds (x1, y1, x2, y2), not
        // (x, y, width, height). Passing size as x2/y2 stretches every skin across the screen.
        // UVs use (u0, u1, v0, v1), not the more common (u0, v0, u1, v1).
        g.blit(skin, x, y, x + size, y + size, 8f / 64f, 16f / 64f, 8f / 64f, 16f / 64f);
        g.blit(skin, x, y, x + size, y + size, 40f / 64f, 48f / 64f, 8f / 64f, 16f / 64f);
    }
}
