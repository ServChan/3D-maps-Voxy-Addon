package com.voxymap.client.mixin;

import com.voxymap.client.map.MapScreenshot;
import com.voxymap.client.map.VoxyMapCameraController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, remap = false)
public abstract class GuiMixin {
    @Shadow(remap = false)
    private GuiRenderState guiRenderState;

    /**
     * Interface-free map screenshot: once the whole GUI (map panels, HUD, chat, toasts, tooltips) has
     * been extracted, throw it away so this frame renders with the world view alone.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("TAIL"),
            remap = false, require = 0)
    private void voxymap$dropInterfaceForMapScreenshot(DeltaTracker deltaTracker, boolean renderGui,
                                                       boolean renderScreen, CallbackInfo ci) {
        if (MapScreenshot.afterGuiExtracted(MapScreenshot.isMapOpen(Minecraft.getInstance()))) {
            guiRenderState.reset();
        }
    }

    @Inject(method = {"renderCrosshair", "extractCrosshair"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$hideCrosshair(CallbackInfo ci) {
        if (VoxyMapCameraController.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = {"renderItemHotbar", "extractItemHotbar"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$hideHotbar(CallbackInfo ci) {
        if (VoxyMapCameraController.isActive()) {
            ci.cancel();
        }
    }
}
