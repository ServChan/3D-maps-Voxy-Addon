package com.voxymap.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.rendering.hierachical.NodeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    private static final int NODE_TYPE_MASK = 0xC0000000;
    private static final int NODE_TYPE_REQUEST = 0x80000000;

    @Shadow
    @Final
    private Long2IntOpenHashMap activeSectionMap;

    @Inject(method = "processRequest", at = @At("HEAD"), cancellable = true)
    private void voxymap$ignoreAlreadyQueuedRequest(long position, CallbackInfo ci) {
        int node = activeSectionMap.get(position);
        if ((node & NODE_TYPE_MASK) == NODE_TYPE_REQUEST) {
            ci.cancel();
        }
    }
}
