package com.voxymap.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.voxymap.client.map.WindowMapRenderer;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.RenderDistanceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VoxyRenderSystem.class, remap = false)
public abstract class VoxyRenderSystemMixin {
    @WrapOperation(method = "renderOpaque", at = @At(value = "INVOKE",
            target = "Lme/cortex/voxy/client/core/rendering/RenderDistanceTracker;setCenterAndProcess(DD)Z"), require = 0)
    private boolean voxymap$keepLoadCentreOnPlayer(RenderDistanceTracker tracker, double x, double z, Operation<Boolean> original) {
        if (WindowMapRenderer.rendering()) {
            return false;
        }
        return original.call(tracker, x, z);
    }
}
