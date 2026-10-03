package com.voxymap.client.mixin;

import com.voxymap.client.gui.MapScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.client.SpatialGUIClient", remap = false)
public abstract class SpatialGuiClientMixin {
    @Inject(method = "shouldHookScreen", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void voxymap$keepMapFlat(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (screen instanceof MapScreen) {
            cir.setReturnValue(false);
        }
    }
}
