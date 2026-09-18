package com.voxymap.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * Draws a player's face (base skin layer + hat/hair overlay) the same way the game's own tab list and
 * multiplayer server list do, using whatever skin the client already has cached for that UUID via the
 * server's tab list — no extra network fetch of our own.
 */
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

    public static void draw(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size) {
        draw(g, skin, x, y, size, 0xFFFFFFFF);
    }

    /** As {@link #draw(GuiGraphicsExtractor, Identifier, int, int, int)}, but tinted/blended by
     * {@code argbTint} - the minimap uses this to fade a distant player's head icon the same way it
     * fades their name and marker dot, instead of the head staying at full opacity while everything
     * else around it dims. */
    public static void draw(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size, int argbTint) {
        // Use the explicit pixel-region overload used by the game's player-head renderers. The
        // compact normalized-UV overload has destination-bounds and UV ordering that are easy to
        // confuse; both mistakes previously exposed strips or the full 64x64 skin sheet here.
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f,
                size, size, 8, 8, 64, 64, argbTint);
        g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f,
                size, size, 8, 8, 64, 64, argbTint);
    }
}
