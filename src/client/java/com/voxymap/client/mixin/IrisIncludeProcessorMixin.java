package com.voxymap.client.mixin;

import com.google.common.collect.ImmutableList;
import com.voxymap.client.map.ComplementaryMapFogPatcher;
import com.voxymap.client.map.SolasMapFogPatcher;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * Patches only the shader packs' small fog includes (Solas fog, Complementary Nether fog); no program
 * samplers or Voxy terrain shaders are touched.
 */
@Mixin(value = IncludeProcessor.class, remap = false)
public abstract class IrisIncludeProcessorMixin {
    private static final String SOLAS_FOG_PATH = "/lib/atmosphere/fog.glsl";

    @Inject(method = "getIncludedFile", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void voxymap$attenuateMapFog(
            AbsolutePackPath path,
            CallbackInfoReturnable<ImmutableList<String>> cir) {
        ImmutableList<String> lines = cir.getReturnValue();
        String pathString = path.getPathString();
        boolean solas = SOLAS_FOG_PATH.equals(pathString);
        if (lines == null || !(solas || ComplementaryMapFogPatcher.handles(pathString))) {
            return;
        }

        String source = String.join("\n", lines);
        String patched = solas
                ? SolasMapFogPatcher.patchSafely(source)
                : ComplementaryMapFogPatcher.patchSafely(pathString, source);
        if (!patched.equals(source)) {
            cir.setReturnValue(ImmutableList.copyOf(Arrays.asList(patched.split("\n", -1))));
        }
    }
}
