package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapCameraController;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies an immediate, unsmoothed map-view signal to compatible shader-pack source patches. */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class IrisCommonUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("RETURN"), remap = false, require = 0)
    private static void voxymap$addMapViewUniform(
            UniformHolder uniforms,
            FrameUpdateNotifier updateNotifier,
            PackDirectives directives,
            CallbackInfo ci) {
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "voxymapMapView",
                () -> VoxyMapCameraController.isActive() ? 1.0f : 0.0f);
    }
}
