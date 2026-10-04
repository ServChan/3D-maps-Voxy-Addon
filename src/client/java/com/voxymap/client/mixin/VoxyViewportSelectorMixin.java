package com.voxymap.client.mixin;

import com.voxymap.client.map.WindowMapRenderer;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.ViewportSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ViewportSelector.class, remap = false)
public abstract class VoxyViewportSelectorMixin {
    @Inject(method = "getViewport", at = @At("HEAD"), cancellable = true, require = 0)
    private void voxymap$useWindowViewport(CallbackInfoReturnable<Viewport<?>> cir) {
        if (WindowMapRenderer.rendering() && WindowMapRenderer.windowViewport(this) instanceof Viewport<?> viewport) {
            cir.setReturnValue(viewport);
        }
    }
}
