package com.voxymap.client.mixin;

import com.voxymap.client.map.MapRenderSettingsGuard;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Options.class, remap = false)
public abstract class OptionsMixin {
    @Inject(method = "save", at = @At("HEAD"), remap = false, require = 0)
    private void voxymap$saveTheRealCloudSetting(CallbackInfo ci) {
        MapRenderSettingsGuard.beforeOptionsSave((Options) (Object) this);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false, require = 0)
    private void voxymap$keepCloudsOffOnTheMap(CallbackInfo ci) {
        MapRenderSettingsGuard.afterOptionsSave((Options) (Object) this);
    }
}
