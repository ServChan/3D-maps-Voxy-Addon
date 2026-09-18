package com.voxymap.client.mixin;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.map.OreMaskTexture;
import com.voxymap.client.map.RoomCutawayController;
import com.voxymap.client.map.RoomCutawayShaderPatcher;
import com.voxymap.client.map.RoomCutawayTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICViewport;
import org.lwjgl.opengl.GL20C;
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

import java.util.HashMap;
import java.util.Map;

@Mixin(value = MDICSectionRenderer.class, remap = false)
public abstract class MDICSectionRendererMixin {
    @Shadow @Final private Shader terrainShader;
    @Shadow @Final private Shader translucentTerrainShader;
    private int voxymap$opaquePreviousTexture = -1;
    private int voxymap$translucentPreviousTexture = -1;
    private int voxymap$opaquePreviousOreTexture = -1;
    private int voxymap$translucentPreviousOreTexture = -1;

    // The layout(location = N) qualifiers in RoomCutawayShaderPatcher's injected declarations are only
    // a request - nothing here previously verified the driver's linker actually honored them, so a
    // location that silently ended up different (an uncommon but real possibility across GL
    // implementations, and impossible to tell apart from "the feature is just broken" without this)
    // would make every uniform write below land on the wrong slot, or none at all, with no error and
    // no visible symptom besides the cutaway/ore-mask never doing anything. Resolving the real location
    // per compiled program with glGetUniformLocation instead of trusting the hardcoded constants removes
    // that whole class of doubt.
    private static final Map<Integer, VoxymapUniformLocations> voxymap$uniformCache = new HashMap<>();

    private record VoxymapUniformLocations(int enabled, int originX, int originZ, int globalCutoff,
                                            int apertureCenterX, int apertureCenterZ, int apertureInnerCutoff,
                                            int apertureRadius, int oreMaskEnabled, int sphereCenterX,
                                            int sphereCenterY, int sphereCenterZ, int sphereRadius) {
    }

    private static VoxymapUniformLocations voxymap$locationsFor(int programId) {
        return voxymap$uniformCache.computeIfAbsent(programId, id -> {
            VoxymapUniformLocations locations = new VoxymapUniformLocations(
                    GL20C.glGetUniformLocation(id, "voxymapCutawayEnabled"),
                    GL20C.glGetUniformLocation(id, "voxymapCutawayOriginX"),
                    GL20C.glGetUniformLocation(id, "voxymapCutawayOriginZ"),
                    GL20C.glGetUniformLocation(id, "voxymapGlobalCutawayY"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureCenterX"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureCenterZ"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureInnerY"),
                    GL20C.glGetUniformLocation(id, "voxymapApertureRadius"),
                    GL20C.glGetUniformLocation(id, "voxymapOreMaskEnabled"),
                    GL20C.glGetUniformLocation(id, "voxymapSphereCenterX"),
                    GL20C.glGetUniformLocation(id, "voxymapSphereCenterY"),
                    GL20C.glGetUniformLocation(id, "voxymapSphereCenterZ"),
                    GL20C.glGetUniformLocation(id, "voxymapSphereRadius"));
            if (locations.enabled() < 0) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Room-cutaway uniform 'voxymapCutawayEnabled' was not found "
                        + "in the compiled Voxy shader (program {}, expected location {}) - the shader source "
                        + "patch applied, but the driver's linker did not expose the uniform where expected, so "
                        + "the cutaway will not affect rendering on this shader.",
                        id, RoomCutawayShaderPatcher.ENABLED_UNIFORM_LOCATION);
            }
            return locations;
        });
    }

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
        if (!RoomCutawayShaderPatcher.isSupported()) {
            return -1;
        }
        VoxymapUniformLocations locations = voxymap$locationsFor(shader.id());
        // Two independent reasons to have anything bound at all: the enclosed-room search found (and
        // uploaded) a real ceiling, or the camera-centered reveal sphere is active - either alone must
        // still turn the whole effect on, not just the one that happened to trigger it.
        boolean enabled = RoomCutawayController.isActive()
                && (RoomCutawayTexture.isEnabled() || RoomCutawayTexture.sphereRadius() > 0.0f);
        if (locations.enabled() >= 0) {
            GL41C.glProgramUniform1i(shader.id(), locations.enabled(), enabled ? 1 : 0);
        }
        if (enabled) {
            if (locations.originX() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.originX(), RoomCutawayTexture.originX());
            }
            if (locations.originZ() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.originZ(), RoomCutawayTexture.originZ());
            }
            if (locations.globalCutoff() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.globalCutoff(), RoomCutawayTexture.uniformCutoff());
            }
            if (locations.apertureCenterX() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureCenterX(), RoomCutawayTexture.apertureCenterX());
            }
            if (locations.apertureCenterZ() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureCenterZ(), RoomCutawayTexture.apertureCenterZ());
            }
            if (locations.apertureInnerCutoff() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureInnerCutoff(), RoomCutawayTexture.apertureInnerCutoff());
            }
            if (locations.apertureRadius() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.apertureRadius(), RoomCutawayTexture.apertureRadius());
            }
            if (locations.sphereCenterX() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.sphereCenterX(), (float) RoomCutawayTexture.sphereCenterX());
            }
            if (locations.sphereCenterY() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.sphereCenterY(), (float) RoomCutawayTexture.sphereCenterY());
            }
            if (locations.sphereCenterZ() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.sphereCenterZ(), (float) RoomCutawayTexture.sphereCenterZ());
            }
            if (locations.sphereRadius() >= 0) {
                GL41C.glProgramUniform1f(shader.id(), locations.sphereRadius(), RoomCutawayTexture.sphereRadius());
            }
            // Safe to bind unconditionally now even when only the sphere triggered `enabled` and the
            // room search itself found nothing this cycle - RoomCutawayTexture seeds newly created
            // storage to NO_CUT_VALUE (see its ensureCreated()), so an unbound-by-real-data texture
            // samples as "nothing confirmed here" instead of stale/undefined contents.
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
        if (!RoomCutawayShaderPatcher.isSupported()) {
            return -1;
        }
        VoxymapUniformLocations locations = voxymap$locationsFor(shader.id());
        boolean enabled = OreMaskTexture.hasAnyMarked();
        if (locations.oreMaskEnabled() >= 0) {
            GL41C.glProgramUniform1i(shader.id(), locations.oreMaskEnabled(), enabled ? 1 : 0);
        }
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
