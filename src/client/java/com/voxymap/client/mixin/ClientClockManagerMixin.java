package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives the celestial angle used to pick the sky/sun/moon rendering - separate from (and just as
 * necessary as) the {@code getOverworldClockTime}/{@code getDefaultClockTime} overrides in
 * {@code LevelMixin}, which only affect {@code Level}'s own notion of the world clock, not what the sky
 * renderer reads here.
 */
@Mixin(targets = "net.minecraft.client.ClientClockManager", remap = false)
public abstract class ClientClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$overrideMapClockTicks(Holder<?> clock, CallbackInfoReturnable<Long> cir) {
        VoxyMapSettings.TimeOfDay mode = VoxyMapSettings.activeMapLightingOverride();
        if (mode != null) {
            cir.setReturnValue(mode == VoxyMapSettings.TimeOfDay.NIGHT ? 18000L : 6000L);
        }
    }
}
