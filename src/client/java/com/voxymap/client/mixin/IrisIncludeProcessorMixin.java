package com.voxymap.client.mixin;

import com.google.common.collect.ImmutableList;
import com.voxymap.client.map.SolasMapFogPatcher;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/** Patches only Solas' small fog include; no program samplers or Voxy terrain shaders are touched. */
@Mixin(value = IncludeProcessor.class, remap = false)
public abstract class IrisIncludeProcessorMixin {
    private static final String SOLAS_FOG_PATH = "/lib/atmosphere/fog.glsl";

    @Inject(method = "getIncludedFile", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void voxymap$attenuateSolasMapRain(
            AbsolutePackPath path,
            CallbackInfoReturnable<ImmutableList<String>> cir) {
        ImmutableList<String> lines = cir.getReturnValue();
        if (lines == null || !SOLAS_FOG_PATH.equals(path.getPathString())) {
            return;
        }

        String source = String.join("\n", lines);
        String patched = SolasMapFogPatcher.patchSafely(source);
        if (!patched.equals(source)) {
            cir.setReturnValue(ImmutableList.copyOf(Arrays.asList(patched.split("\n", -1))));
        }
    }
}
