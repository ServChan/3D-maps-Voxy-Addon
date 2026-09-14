package com.voxymap.client.map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Uploads a per-model-id "is this an ore block" flag as a 256x256 GL_R8 texture, indexed the same way
 * the patched Voxy fragment shader already decodes a model id into atlas tile coordinates
 * ({@code (modelId & 0xFF, (modelId >> 8) & 0xFF)}), so the shader can reuse that same decomposition to
 * look this up. A single persistent texture, grown one texel at a time as {@link OreMaskController}
 * discovers new ore ids over the course of exploring - it never shrinks or resets except on client
 * restart, mirroring {@link RoomCutawayTexture}'s lifecycle.
 */
public final class OreMaskTexture {
    // RoomCutawayTexture already claims unit 15; Voxy's own post-process buffer claims unit 3 (see that
    // class's comment) - nothing in Voxy's own shaders declares a binding above 6, so 14 is clear.
    public static final int TEXTURE_UNIT = 14;
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
        // glTextureStorage2D leaves contents undefined - zero it explicitly so every unmarked id reads
        // as "not ore" from the first frame, not whatever garbage the driver handed back.
        ByteBuffer zeros = MemoryUtil.memCalloc(GRID * GRID);
        try {
            GL45C.glTextureSubImage2D(textureId, 0, 0, 0, GRID, GRID, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, zeros);
        } finally {
            MemoryUtil.memFree(zeros);
        }
    }

    /** Marks one model id as an ore for the life of this texture - never unmarked. Package-visible:
     * only {@link OreMaskController} discovers and classifies new ids. */
    static void markOre(int modelId) {
        ensureCreated();
        int x = modelId & 0xFF;
        int y = (modelId >> 8) & 0xFF;
        ByteBuffer one = MemoryUtil.memAlloc(1);
        try {
            one.put(0, (byte) 0xFF).rewind();
            GL45C.glTextureSubImage2D(textureId, 0, x, y, 1, 1, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, one);
        } finally {
            MemoryUtil.memFree(one);
        }
        anyMarked = true;
    }

    /** False until at least one ore id has been discovered and marked - lets the render mixin skip
     * binding the texture and setting the enabled uniform entirely until there is anything to mask. */
    public static boolean hasAnyMarked() {
        return anyMarked;
    }

    public static int textureId() {
        ensureCreated();
        return textureId;
    }
}
