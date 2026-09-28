package com.voxymap.client.mixin;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.map.NetherRoofController;
import com.voxymap.client.map.NetherRoofState;
import com.voxymap.client.map.OreMaskTexture;
import com.voxymap.client.map.VoxyShaderPatcher;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICViewport;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL45C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = MDICSectionRenderer.class, remap = false)
public abstract class MDICSectionRendererMixin {
    @Shadow @Final private Shader terrainShader;
    @Shadow @Final private Shader translucentTerrainShader;
    private int voxymap$opaquePreviousOreTexture = -1;
    private int voxymap$translucentPreviousOreTexture = -1;

    private static final Map<Integer, VoxymapUniformLocations> voxymap$uniformCache = new HashMap<>();

    private record VoxymapUniformLocations(int enabled, int globalCutoff, int apertureCenterX,
                                            int apertureCenterZ, int apertureInnerCutoff, int apertureRadius,
                                            int oreMaskEnabled) {
    }

    private static VoxymapUniformLocations voxymap$locationsFor(int programId) {
        return voxymap$uniformCache.computeIfAbsent(programId, id -> {
            VoxymapUniformLocations locations = new VoxymapUniformLocations(
                    GL20C.glGetUniformLocation(id, "voxymapCutawayEnabled"),
                    GL20C.glGetUniformLocation(id, "voxymapGlobalCutawayY"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureCenterX"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureCenterZ"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureInnerY"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureRadius"),
                    GL20C.glGetUniformLocation(id, "voxymapOreMaskEnabled"));
            if (locations.enabled() < 0) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Roof-removal uniform 'voxymapCutawayEnabled' was not found "
                        + "in the compiled Voxy shader (program {}, expected location {}) - the shader source "
                        + "patch applied, but the driver's linker did not expose the uniform where expected, so "
                        + "the Nether roof removal will not affect rendering on this shader.",
                        id, VoxyShaderPatcher.ENABLED_UNIFORM_LOCATION);
            }
            return locations;
        });
    }

    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("HEAD"), require = 0)
    private void voxymap$setOpaqueRoofCut(MDICViewport viewport, CallbackInfo ci) {
        voxymap$setRoofCutUniforms(terrainShader);
        voxymap$opaquePreviousOreTexture = voxymap$setOreMaskUniforms(terrainShader);
    }

    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("RETURN"), require = 0)
    private void voxymap$restoreOpaqueTexture(MDICViewport viewport, CallbackInfo ci) {
        voxymap$restoreTexture(voxymap$opaquePreviousOreTexture, OreMaskTexture.TEXTURE_UNIT);
        voxymap$opaquePreviousOreTexture = -1;
    }

    @Inject(method = "renderTranslucent(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("HEAD"), require = 0)
    private void voxymap$setTranslucentRoofCut(MDICViewport viewport, CallbackInfo ci) {
        voxymap$setRoofCutUniforms(translucentTerrainShader);
        voxymap$translucentPreviousOreTexture = voxymap$setOreMaskUniforms(translucentTerrainShader);
    }

    @Inject(method = "renderTranslucent(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("RETURN"), require = 0)
    private void voxymap$restoreTranslucentTexture(MDICViewport viewport, CallbackInfo ci) {
        voxymap$restoreTexture(voxymap$translucentPreviousOreTexture, OreMaskTexture.TEXTURE_UNIT);
        voxymap$translucentPreviousOreTexture = -1;
    }

    private static void voxymap$setRoofCutUniforms(Shader shader) {
        if (!VoxyShaderPatcher.isSupported()) {
            return;
        }
        VoxymapUniformLocations locations = voxymap$locationsFor(shader.id());
        boolean enabled = NetherRoofController.isActive();
        if (locations.enabled() >= 0) {
            GL41C.glProgramUniform1i(shader.id(), locations.enabled(), enabled ? 1 : 0);
        }
        if (enabled) {
            if (locations.globalCutoff() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.globalCutoff(), NetherRoofState.uniformCutoff());
            }
            if (locations.apertureCenterX() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureCenterX(), NetherRoofState.apertureCenterX());
            }
            if (locations.apertureCenterZ() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureCenterZ(), NetherRoofState.apertureCenterZ());
            }
            if (locations.apertureInnerCutoff() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureInnerCutoff(), NetherRoofState.apertureInnerCutoff());
            }
            if (locations.apertureRadius() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureRadius(), NetherRoofState.apertureRadius());
            }
        }
    }

    private static int voxymap$setOreMaskUniforms(Shader shader) {
        if (!VoxyShaderPatcher.isSupported()) {
            return -1;
        }
        VoxymapUniformLocations locations = voxymap$locationsFor(shader.id());
        boolean enabled = OreMaskTexture.hasAnyMarked();
        if (locations.oreMaskEnabled() >= 0) {
            GL41C.glProgramUniform1i(shader.id(), locations.oreMaskEnabled(), enabled ? 1 : 0);
        }
        if (enabled) {
            int previousActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + OreMaskTexture.TEXTURE_UNIT);
            int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL13C.glActiveTexture(previousActiveTexture);
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
