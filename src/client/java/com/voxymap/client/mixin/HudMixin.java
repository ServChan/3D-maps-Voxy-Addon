package com.voxymap.client.mixin;

import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapCameraController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.gui.Hud", remap = false)
public abstract class HudMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$hideCrosshair(CallbackInfo ci) {
        if (VoxyMapCameraController.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$hideHotbar(CallbackInfo ci) {
        if (VoxyMapCameraController.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), remap = false, require = 0)
    private void voxymap$extractMinimap(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        MinimapRenderer.extract(g, Minecraft.getInstance());
    }
}
