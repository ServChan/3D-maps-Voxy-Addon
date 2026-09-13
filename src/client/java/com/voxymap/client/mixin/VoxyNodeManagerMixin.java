package com.voxymap.client.mixin;

import me.cortex.voxy.client.core.rendering.hierachical.NodeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silences Voxy 0.2.18-beta's harmless duplicate-request error before logging frameworks mirror it
 * into the player's chat. Voxy's own {@code processRequest} performs this exact state check, logs an
 * error, and immediately returns; cancelling here preserves that behavior without the false alarm.
 */
@Mixin(value = NodeManager.class, remap = false)
public abstract class VoxyNodeManagerMixin {
    @Inject(method = "processRequest", at = @At(value = "INVOKE",
            target = "Lme/cortex/voxy/common/Logger;error([Ljava/lang/Object;)V", ordinal = 0), cancellable = true)
    private void voxymap$ignoreAlreadyQueuedRequest(long position, CallbackInfo ci) {
        ci.cancel();
    }
}
