package com.voxymap.client.mixin;

import com.voxymap.client.map.NetherSliceShaderPatcher;
import com.voxymap.client.map.VoxyMapCameraController;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICViewport;
import org.lwjgl.opengl.GL41C;
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

    @Inject(method = "renderOpaque(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("HEAD"))
    private void voxymap$setOpaqueSlice(MDICViewport viewport, CallbackInfo ci) {
        voxymap$setSliceUniforms(terrainShader);
    }

    @Inject(method = "renderTranslucent(Lme/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport;)V", at = @At("HEAD"))
    private void voxymap$setTranslucentSlice(MDICViewport viewport, CallbackInfo ci) {
        voxymap$setSliceUniforms(translucentTerrainShader);
    }

    private static void voxymap$setSliceUniforms(Shader shader) {
        boolean enabled = VoxyMapCameraController.isNetherSliceActive();
        GL41C.glProgramUniform1i(shader.id(), NetherSliceShaderPatcher.ENABLED_UNIFORM_LOCATION, enabled ? 1 : 0);
        GL41C.glProgramUniform1f(shader.id(), NetherSliceShaderPatcher.HEIGHT_UNIFORM_LOCATION,
                enabled ? VoxyMapCameraController.netherSliceY() : 0.0f);
    }
}
