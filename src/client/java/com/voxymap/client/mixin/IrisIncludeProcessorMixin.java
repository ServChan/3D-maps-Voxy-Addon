package com.voxymap.client.mixin;

import com.google.common.collect.ImmutableList;
import com.voxymap.client.map.SolasMapFogPatcher;
import com.voxymap.client.map.SolasColoredLightCachePatcher;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/** Patches narrowly-recognized Solas includes; unknown shader layouts remain byte-for-byte untouched. */
@Mixin(value = IncludeProcessor.class, remap = false)
public abstract class IrisIncludeProcessorMixin {
    private static final String SOLAS_FOG_PATH = "/lib/atmosphere/fog.glsl";
    private static final String SOLAS_LIGHTING_PATH = "/lib/lighting/gbuffersLighting.glsl";

    @Inject(method = "getIncludedFile", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void voxymap$attenuateSolasMapRain(
            AbsolutePackPath path,
            CallbackInfoReturnable<ImmutableList<String>> cir) {
        ImmutableList<String> lines = cir.getReturnValue();
        if (lines == null) {
            return;
        }

        String source = String.join("\n", lines);
        String patched;
        if (SOLAS_FOG_PATH.equals(path.getPathString())) {
            patched = SolasMapFogPatcher.patchSafely(source);
        } else if (SOLAS_LIGHTING_PATH.equals(path.getPathString())) {
            patched = SolasColoredLightCachePatcher.patchSafely(source);
        } else {
            return;
        }
        if (!patched.equals(source)) {
            cir.setReturnValue(ImmutableList.copyOf(Arrays.asList(patched.split("\n", -1))));
        }
    }
}
