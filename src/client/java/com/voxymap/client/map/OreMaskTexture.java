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
 * discovers new ore ids over the course of exploring. Model ids belong to Voxy's current
 * {@code ModelFactory}; shader/resource reloads can rebuild that factory and assign the same ids to
 * different blocks, so {@link #reset()} clears the texture whenever that owner changes.
 */
public final class OreMaskTexture {
    // Historically this used unit 14 on the assumption that
    // "nothing in Voxy's own shaders declares a binding above 6" - true for the unpatched shader, but
    // wrong under Iris: Voxy's Iris integration (IrisVoxyRenderPipeline) assigns the active shaderpack's
    // gbuffers_terrain samplers to units BASE_SAMPLER_BINDING_INDEX(6)..6+N-1, where N is however many
    // samplers that pack's terrain program declares. A sufficiently complex pack (colortex buffers,
    // shadow maps, noise texture, custom samplers - e.g. deferred/PBR packs like Mellow Shader) can claim
    // N >= 9, reaching unit 14 and colliding with this texture: our glBindTextureUnit(14, ...) call in
    // MDICSectionRendererMixin then stomps the texture Iris's compiled shader is actively sampling for
    // one of its own inputs on that same draw call, which reads back as garbage (typically blown-out
    // white terrain, since our GL_R8 0/1 mask gets sampled as
    // if it were a [0,1] lighting/shadow/albedo term). Unit 30 sits far above any known shaderpack's
    // sampler count while staying under the widely-supported 32 per-stage texture image unit floor.
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
        // glTextureStorage2D leaves contents undefined - zero it explicitly so every unmarked id reads
        // as "not ore" from the first frame, not whatever garbage the driver handed back. The same
        // upload also invalidates ids left behind by a previous Voxy model factory.
        ByteBuffer zeros = MemoryUtil.memCalloc(GRID * GRID);
        try {
            GlClientTextureUpload.redBytes(textureId, 0, 0, GRID, GRID, zeros);
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
            GlClientTextureUpload.redBytes(textureId, x, y, 1, 1, one);
        } finally {
            MemoryUtil.memFree(one);
        }
        anyMarked = true;
    }

    /** Invalidates model ids owned by a disposed/rebuilt Voxy model factory. Render-thread only. */
    static void reset() {
        if (textureId != 0) {
            clearPixels();
        }
        anyMarked = false;
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
