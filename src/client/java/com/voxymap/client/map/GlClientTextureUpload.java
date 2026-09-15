package com.voxymap.client.map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL45C;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/** Uploads client-memory pixels without inheriting Voxy's transient pixel-unpack-buffer binding. */
final class GlClientTextureUpload {
    private GlClientTextureUpload() {
    }

    static void redBytes(int texture, int x, int y, int width, int height, ByteBuffer pixels) {
        int previous = beginClientMemoryUpload();
        try {
            GL45C.glTextureSubImage2D(texture, 0, x, y, width, height,
                    GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, pixels);
        } finally {
            restorePixelUnpackBuffer(previous);
        }
    }

    static void redFloats(int texture, int x, int y, int width, int height, FloatBuffer pixels) {
        int previous = beginClientMemoryUpload();
        try {
            GL45C.glTextureSubImage2D(texture, 0, x, y, width, height,
                    GL11C.GL_RED, GL11C.GL_FLOAT, pixels);
        } finally {
            restorePixelUnpackBuffer(previous);
        }
    }

    private static int beginClientMemoryUpload() {
        int previous = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (previous != 0) {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        return previous;
    }

    private static void restorePixelUnpackBuffer(int previous) {
        if (previous != 0) {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, previous);
        }
    }
}
