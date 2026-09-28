package com.voxymap.client.mixin;

import me.cortex.voxy.client.core.rendering.hierachical.NodeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NodeManager.class, remap = false)
public abstract class VoxyNodeManagerMixin {
    @Inject(method = "processRequest", at = @At(value = "INVOKE",
            target = "Lme/cortex/voxy/common/Logger;error([Ljava/lang/Object;)V", ordinal = 0), cancellable = true, require = 0)
    private void voxymap$ignoreAlreadyQueuedRequest(long position, CallbackInfo ci) {
        ci.cancel();
    }
}
