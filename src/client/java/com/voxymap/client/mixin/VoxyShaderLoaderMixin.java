package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyShaderPatcher;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderLoader.class, remap = false)
public abstract class VoxyShaderLoaderMixin {
    @Inject(method = "parse", at = @At("RETURN"), cancellable = true, require = 0)
    private static void voxymap$patchVoxyShaders(String resource, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(VoxyShaderPatcher.patchSafely(resource, cir.getReturnValue()));
    }
}
