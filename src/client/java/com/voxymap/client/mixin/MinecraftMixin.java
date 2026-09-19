package com.voxymap.client.mixin;

import com.voxymap.client.map.MapScreenshot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin {
    /**
     * Runs before anything of the new frame is drawn, so the main render target still holds the
     * previous, fully rendered frame - the same moment at which vanilla takes its own screenshot.
     */
    @Inject(method = "runTick", at = @At("HEAD"), remap = false, require = 0)
    private void voxymap$saveInterfaceFreeMapFrame(boolean advanceGameTime, CallbackInfo ci) {
        MapScreenshot.captureIfDue((Minecraft) (Object) this);
    }
}
