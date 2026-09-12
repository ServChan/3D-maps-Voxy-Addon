package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapCameraController;
import me.cortex.voxy.client.VoxyClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Uses Voxy's own LOD-only rendering mode while the full-screen map is open.
 *
 * <p>Normally Voxy leaves the area inside the vanilla render distance to Sodium. That hand-off is
 * correct for gameplay, but Sodium's visible-section list still follows the real player camera while
 * the map temporarily substitutes a remote camera. The result is the angle-dependent hole around
 * the player. Voxy's built-in switch both renders cached LOD geometry down to its close near plane
 * and suppresses Sodium's overlapping chunk pass, without loading or rebuilding any chunks.</p>
 */
@Mixin(value = VoxyClient.class, remap = false, priority = 2000)
public abstract class VoxyClientMixin {
    @Inject(method = "disableSodiumChunkRender", at = @At("HEAD"), cancellable = true)
    private static void voxymap$useCachedLodsForWholeMap(CallbackInfoReturnable<Boolean> cir) {
        if (VoxyMapCameraController.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
