package com.voxymap.client.map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** Render-thread owner of the completed world-space RGB cache texture. */
public final class ColoredLightTexture {
    public static final int TEXTURE_UNIT = 13;
    private static int textureId;
    private static int originX;
    private static int originZ;
    private static boolean ready;

    private ColoredLightTexture() {
    }

    public static void uploadReady() {
        ColoredLightCache.Frame frame = ColoredLightCache.takeReady();
        if (frame == null) return;
        ensureCreated();
        ByteBuffer pixels = MemoryUtil.memAlloc(frame.rgb().length);
        try {
            pixels.put(frame.rgb()).flip();
            GL45C.glTextureSubImage2D(textureId, 0, 0, 0, ColoredLightCache.GRID, ColoredLightCache.GRID,
                    GL11C.GL_RGB, GL11C.GL_UNSIGNED_BYTE, pixels);
        } finally {
            MemoryUtil.memFree(pixels);
        }
        originX = frame.originX();
        originZ = frame.originZ();
        ready = true;
    }

    private static void ensureCreated() {
        if (textureId != 0) return;
        textureId = GL45C.glCreateTextures(GL33C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(textureId, 1, GL30C.GL_RGB8, ColoredLightCache.GRID, ColoredLightCache.GRID);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
    }

    public static boolean isReady() { return ready; }
    public static int textureId() { return textureId; }
    public static int originX() { return originX; }
    public static int originZ() { return originZ; }
}
