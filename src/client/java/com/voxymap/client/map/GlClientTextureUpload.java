package com.voxymap.client.map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL45C;

import java.nio.ByteBuffer;

final class GlClientTextureUpload {
    private static final int[] UNPACK_PARAMETERS = {
            GL11C.GL_UNPACK_ROW_LENGTH, GL11C.GL_UNPACK_SKIP_PIXELS, GL11C.GL_UNPACK_SKIP_ROWS, GL11C.GL_UNPACK_ALIGNMENT
    };
    private static final int[] TIGHT_UNPACK_VALUES = {0, 0, 0, 1};

    private GlClientTextureUpload() {
    }

    static void redBytes(int texture, int x, int y, int width, int height, ByteBuffer pixels) {
        int previousBuffer = beginClientMemoryUpload();
        int[] previousUnpack = new int[UNPACK_PARAMETERS.length];
        for (int i = 0; i < UNPACK_PARAMETERS.length; i++) {
            previousUnpack[i] = GL11C.glGetInteger(UNPACK_PARAMETERS[i]);
            GL11C.glPixelStorei(UNPACK_PARAMETERS[i], TIGHT_UNPACK_VALUES[i]);
        }
        try {
            GL45C.glTextureSubImage2D(texture, 0, x, y, width, height,
                    GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, pixels);
        } finally {
            for (int i = 0; i < UNPACK_PARAMETERS.length; i++) {
                GL11C.glPixelStorei(UNPACK_PARAMETERS[i], previousUnpack[i]);
            }
            restorePixelUnpackBuffer(previousBuffer);
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
