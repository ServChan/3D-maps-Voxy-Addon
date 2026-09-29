package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance", remap = false)
public abstract class ClientClockManagerMixin {
    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$overrideMapClockTicks(CallbackInfoReturnable<Long> cir) {
        VoxyMapSettings.TimeOfDay mode = VoxyMapSettings.activeMapLightingOverride();
        if (mode != null) {
            cir.setReturnValue(mode.clockTicks());
        }
    }
}
