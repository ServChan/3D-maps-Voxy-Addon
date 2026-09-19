package com.voxymap.client.mixin;

import com.google.common.collect.ImmutableList;
import com.voxymap.client.map.ShaderPackFogPatcher;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * Patches only the shader packs' small fog includes (see {@link ShaderPackFogPatcher}); no program
 * samplers or Voxy terrain shaders are touched.
 */
@Mixin(value = IncludeProcessor.class, remap = false)
public abstract class IrisIncludeProcessorMixin {
    @Inject(method = "getIncludedFile", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void voxymap$removeMapFog(
            AbsolutePackPath path,
            CallbackInfoReturnable<ImmutableList<String>> cir) {
        ImmutableList<String> lines = cir.getReturnValue();
        String pathString = path.getPathString();
        if (lines == null || !ShaderPackFogPatcher.handles(pathString)) {
            return;
        }

        String source = String.join("\n", lines);
        String patched = ShaderPackFogPatcher.patchSafely(pathString, source);
        if (!patched.equals(source)) {
            cir.setReturnValue(ImmutableList.copyOf(Arrays.asList(patched.split("\n", -1))));
        }
    }
}
