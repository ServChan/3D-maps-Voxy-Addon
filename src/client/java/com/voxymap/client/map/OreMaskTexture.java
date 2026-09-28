package com.voxymap.client.map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class OreMaskTexture {

    public static final int TEXTURE_UNIT = 30;
    private static final int GRID = 256;

    private static int textureId = 0;
    private static boolean anyMarked = false;

    private OreMaskTexture() {
    }

    private static void ensureCreated() {
        if (textureId != 0) {
            return;
        }
        textureId = GL45C.glCreateTextures(GL33C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(textureId, 1, GL30C.GL_R8, GRID, GRID);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        clearPixels();
    }

    private static void clearPixels() {

        ByteBuffer zeros = MemoryUtil.memCalloc(GRID * GRID);
        try {
            GlClientTextureUpload.redBytes(textureId, 0, 0, GRID, GRID, zeros);
        } finally {
            MemoryUtil.memFree(zeros);
        }
    }

    static void markOre(int modelId) {
        ensureCreated();
        int x = modelId & 0xFF;
        int y = (modelId >> 8) & 0xFF;
        ByteBuffer one = MemoryUtil.memAlloc(1);
        try {
            one.put(0, (byte) 0xFF).rewind();
            GlClientTextureUpload.redBytes(textureId, x, y, 1, 1, one);
        } finally {
            MemoryUtil.memFree(one);
        }
        anyMarked = true;
    }

    static void reset() {
        if (textureId != 0) {
            clearPixels();
        }
        anyMarked = false;
    }

    public static boolean hasAnyMarked() {
        return anyMarked;
    }

    public static int textureId() {
        ensureCreated();
        return textureId;
    }
}
