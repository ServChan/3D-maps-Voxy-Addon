package com.voxymap.client.mixin;

import com.voxymap.client.map.MapScreenshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin {

    @Inject(method = "runTick", at = @At("HEAD"), remap = false, require = 0)
    private void voxymap$saveInterfaceFreeMapFrame(boolean advanceGameTime, CallbackInfo ci) {
        MapScreenshot.captureIfDue((Minecraft) (Object) this);
    }
}
