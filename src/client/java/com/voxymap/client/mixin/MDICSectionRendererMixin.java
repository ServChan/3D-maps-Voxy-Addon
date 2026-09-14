package com.voxymap.client.mixin;

import com.voxymap.client.map.OreMaskTexture;
import com.voxymap.client.map.RoomCutawayController;
import com.voxymap.client.map.RoomCutawayShaderPatcher;
import com.voxymap.client.map.RoomCutawayTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICViewport;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MDICSectionRenderer.class, remap = false)
public abstract class MDICSectionRendererMixin {
    @Shadow @Final private Shader terrainShader;
    @Shadow @Final private Shader translucentTerrainShader;
    private int voxymap$opaquePreviousTexture = -1;
    private int voxymap$translucentPreviousTexture = -1;
    private int voxymap$opaquePreviousOreTexture = -1;
    private int voxymap$translucentPreviousOreTexture = -1;

    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("HEAD"))
    private void voxymap$setOpaqueCutaway(MDICViewport viewport, CallbackInfo ci) {
        voxymap$opaquePreviousTexture = voxymap$setCutawayUniforms(terrainShader);
        voxymap$opaquePreviousOreTexture = voxymap$setOreMaskUniforms(terrainShader);
    }

    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("RETURN"))
    private void voxymap$restoreOpaqueTexture(MDICViewport viewport, CallbackInfo ci) {
        voxymap$restoreTexture(voxymap$opaquePreviousTexture, RoomCutawayTexture.TEXTURE_UNIT);
        voxymap$opaquePreviousTexture = -1;
        voxymap$restoreTexture(voxymap$opaquePreviousOreTexture, OreMaskTexture.TEXTURE_UNIT);
        voxymap$opaquePreviousOreTexture = -1;
    }

    @Inject(method = "renderTranslucent(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("HEAD"))
    private void voxymap$setTranslucentCutaway(MDICViewport viewport, CallbackInfo ci) {
        voxymap$translucentPreviousTexture = voxymap$setCutawayUniforms(translucentTerrainShader);
        voxymap$translucentPreviousOreTexture = voxymap$setOreMaskUniforms(translucentTerrainShader);
    }

    @Inject(method = "renderTranslucent(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("RETURN"))
    private void voxymap$restoreTranslucentTexture(MDICViewport viewport, CallbackInfo ci) {
        voxymap$restoreTexture(voxymap$translucentPreviousTexture, RoomCutawayTexture.TEXTURE_UNIT);
        voxymap$translucentPreviousTexture = -1;
        voxymap$restoreTexture(voxymap$translucentPreviousOreTexture, OreMaskTexture.TEXTURE_UNIT);
        voxymap$translucentPreviousOreTexture = -1;
    }

    private static int voxymap$setCutawayUniforms(Shader shader) {
        boolean enabled = RoomCutawayShaderPatcher.isSupported()
                && RoomCutawayController.isActive() && RoomCutawayTexture.isEnabled();
        GL41C.glProgramUniform1i(shader.id(), RoomCutawayShaderPatcher.ENABLED_UNIFORM_LOCATION, enabled ? 1 : 0);
        if (enabled) {
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.ORIGIN_X_UNIFORM_LOCATION, RoomCutawayTexture.originX());
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.ORIGIN_Z_UNIFORM_LOCATION, RoomCutawayTexture.originZ());
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.GLOBAL_CUTOFF_UNIFORM_LOCATION, RoomCutawayTexture.uniformCutoff());
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.APERTURE_CENTER_X_UNIFORM_LOCATION, RoomCutawayTexture.apertureCenterX());
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.APERTURE_CENTER_Z_UNIFORM_LOCATION, RoomCutawayTexture.apertureCenterZ());
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.APERTURE_INNER_CUTOFF_UNIFORM_LOCATION, RoomCutawayTexture.apertureInnerCutoff());
            GL41C.glProgramUniform1f(shader.id(), RoomCutawayShaderPatcher.APERTURE_RADIUS_UNIFORM_LOCATION, RoomCutawayTexture.apertureRadius());
            int previousTexture = GL30C.glGetIntegeri(GL11C.GL_TEXTURE_BINDING_2D, RoomCutawayTexture.TEXTURE_UNIT);
            GL45C.glBindTextureUnit(RoomCutawayTexture.TEXTURE_UNIT, RoomCutawayTexture.textureId());
            return previousTexture;
        }
        return -1;
    }

    /** Independent of {@link #voxymap$setCutawayUniforms} - ore masking is a fairness safeguard against
     * the x-ray described in {@link OreMaskController}, not a cosmetic preference, so unlike the other
     * map overlays it has no user-facing toggle: it applies whenever any ore has been discovered,
     * regardless of whether the room-cutaway effect is active. */
    private static int voxymap$setOreMaskUniforms(Shader shader) {
        boolean enabled = RoomCutawayShaderPatcher.isSupported() && OreMaskTexture.hasAnyMarked();
        GL41C.glProgramUniform1i(shader.id(), RoomCutawayShaderPatcher.ORE_MASK_ENABLED_UNIFORM_LOCATION, enabled ? 1 : 0);
        if (enabled) {
            int previousTexture = GL30C.glGetIntegeri(GL11C.GL_TEXTURE_BINDING_2D, OreMaskTexture.TEXTURE_UNIT);
            GL45C.glBindTextureUnit(OreMaskTexture.TEXTURE_UNIT, OreMaskTexture.textureId());
            return previousTexture;
        }
        return -1;
    }

    private static void voxymap$restoreTexture(int previousTexture, int textureUnit) {
        if (previousTexture >= 0) {
            GL45C.glBindTextureUnit(textureUnit, previousTexture);
        }
    }
}
