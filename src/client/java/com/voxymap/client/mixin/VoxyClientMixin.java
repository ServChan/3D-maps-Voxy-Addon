package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapCameraController;
import me.cortex.voxy.client.VoxyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VoxyClient.class, remap = false, priority = 2000)
public abstract class VoxyClientMixin {
    @Inject(method = "disableSodiumChunkRender", at = @At("HEAD"), cancellable = true, require = 0)
    private static void voxymap$useCachedLodsForWholeMap(CallbackInfoReturnable<Boolean> cir) {
        if (VoxyMapCameraController.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
