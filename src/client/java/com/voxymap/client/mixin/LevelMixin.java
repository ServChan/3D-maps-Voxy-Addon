package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Level.class, remap = false)
public abstract class LevelMixin {
    private VoxyMapSettings.MapWeather voxymap$mapWeather() {
        return (Object) this instanceof ClientLevel ? VoxyMapSettings.activeMapWeatherOverride() : null;
    }

    private VoxyMapSettings.TimeOfDay voxymap$activeTimeOfDayOverride() {
        return (Object) this instanceof ClientLevel ? VoxyMapSettings.activeMapLightingOverride() : null;
    }

    @Inject(method = "isDarkOutside", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$overrideMapDarkness(CallbackInfoReturnable<Boolean> cir) {
        VoxyMapSettings.TimeOfDay mode = voxymap$activeTimeOfDayOverride();
        if (mode != null) {
            cir.setReturnValue(mode.isDark());
        }
    }

    @Inject(method = "getSkyDarken", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$overrideMapSkyDarken(CallbackInfoReturnable<Integer> cir) {
        VoxyMapSettings.TimeOfDay mode = voxymap$activeTimeOfDayOverride();
        if (mode != null) {
            cir.setReturnValue(mode.skyDarken());
        }
    }

    @Inject(method = {"getOverworldClockTime", "getDefaultClockTime"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$overrideMapClockTime(CallbackInfoReturnable<Long> cir) {
        VoxyMapSettings.TimeOfDay mode = voxymap$activeTimeOfDayOverride();
        if (mode != null) {
            cir.setReturnValue(mode.clockTicks());
        }
    }

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$mapRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        VoxyMapSettings.MapWeather weather = voxymap$mapWeather();
        if (weather != null) {
            cir.setReturnValue(weather.rain());
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$mapThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        VoxyMapSettings.MapWeather weather = voxymap$mapWeather();
        if (weather != null) {
            cir.setReturnValue(weather.thunder());
        }
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$mapRaining(CallbackInfoReturnable<Boolean> cir) {
        VoxyMapSettings.MapWeather weather = voxymap$mapWeather();
        if (weather != null) {
            cir.setReturnValue(weather.rain() > 0.2f);
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$mapThundering(CallbackInfoReturnable<Boolean> cir) {
        VoxyMapSettings.MapWeather weather = voxymap$mapWeather();
        if (weather != null) {
            cir.setReturnValue(weather.thunder() > 0.9f);
        }
    }
}
