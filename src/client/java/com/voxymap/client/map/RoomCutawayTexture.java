package com.voxymap.client.map;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Uploads {@link RoomCutawayComputer.Result} as a small per-column "ceiling height" texture and keeps
 * the independent global cut plane used by the patched Voxy shaders. A single persistent GL_R32F
 * texture is reused for the life of the client; its contents change only when the room is recomputed.
 */
public final class RoomCutawayTexture {
    // Voxy itself uses texture unit 3 for its post-process colour buffer. Overwriting that binding
    // turns the height map into the post-process input and produces screen-sized coloured rectangles.
    // Kept well clear of Iris's dynamically-sized sampler range too - see the longer explanation on
    // OreMaskTexture.TEXTURE_UNIT, which used to sit at 14 (one below this) and collided with it for
    // sufficiently complex shaderpacks.
    public static final int TEXTURE_UNIT = 31;
    public static final float NO_CUT_VALUE = 1.0e8f;

    private static int textureId = 0;
    private static boolean enabled = false;
    private static float originX;
    private static float originZ;
    private static float uniformCutoff = NO_CUT_VALUE;
    private static float apertureCenterX;
    private static float apertureCenterZ;
    private static float apertureInnerCutoff = NO_CUT_VALUE;
    private static float apertureRadius;

    private RoomCutawayTexture() {
    }

    private static void ensureCreated() {
        if (textureId != 0) {
            return;
        }
        int size = RoomCutawayComputer.GRID_SIZE;
        textureId = GL45C.glCreateTextures(GL33C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(textureId, 1, GL30C.GL_R32F, size, size);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL45C.glTextureParameteri(textureId, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
    }

    /**
     * @param result            the locally detected room, or {@link RoomCutawayComputer.Result#OPEN}
     * @param globalCutoff a cutoff applied globally regardless of the local texture bounds, or
     *                     {@link #NO_CUT_VALUE} to skip it
     */
    public static void update(RoomCutawayComputer.Result result, float globalCutoff) {
        ensureCreated();
        int size = RoomCutawayComputer.GRID_SIZE;
        int originGridX = result.hasCutaway() ? result.minX : 0;
        int originGridZ = result.hasCutaway() ? result.minZ : 0;
        FloatBuffer buffer = MemoryUtil.memAllocFloat(size * size);
        try {
            for (int gx = 0; gx < size; gx++) {
                for (int gz = 0; gz < size; gz++) {
                    float value = NO_CUT_VALUE;
                    if (result.hasCutaway()) {
                        int ceiling = result.topY[gx][gz];
                        if (ceiling != RoomCutawayComputer.NO_CEILING) {
                            value = (float) ceiling + RoomCutawayComputer.CEILING_MARGIN;
                        }
                    }
                    buffer.put(gz * size + gx, value);
                }
            }
            GlClientTextureUpload.redFloats(textureId, 0, 0, size, size, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }

        originX = originGridX;
        originZ = originGridZ;
        uniformCutoff = globalCutoff;
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static void setUniformCutoff(float cutoff) {
        uniformCutoff = cutoff;
    }

    public static void setAperture(double centerX, double centerZ, float innerCutoff, float radius) {
        apertureCenterX = (float) centerX;
        apertureCenterZ = (float) centerZ;
        apertureInnerCutoff = innerCutoff;
        apertureRadius = radius;
    }

    public static void clearAperture() {
        apertureInnerCutoff = NO_CUT_VALUE;
        apertureRadius = 0.0f;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int textureId() {
        ensureCreated();
        return textureId;
    }

    public static float originX() {
        return originX;
    }

    public static float originZ() {
        return originZ;
    }

    /** Global horizontal cut plane; unlike the room texture, this applies at every X/Z coordinate. */
    public static float uniformCutoff() {
        return uniformCutoff;
    }

    public static float apertureCenterX() {
        return apertureCenterX;
    }

    public static float apertureCenterZ() {
        return apertureCenterZ;
    }

    public static float apertureInnerCutoff() {
        return apertureInnerCutoff;
    }

    public static float apertureRadius() {
        return apertureRadius;
    }
}
