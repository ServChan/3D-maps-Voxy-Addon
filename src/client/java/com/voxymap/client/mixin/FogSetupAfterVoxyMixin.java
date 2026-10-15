package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyRenderGate;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FogRenderer.class, priority = 2000, remap = false)
public abstract class FogSetupAfterVoxyMixin {
    @Inject(method = "setupFog", at = @At("RETURN"), remap = false, require = 0)
    private void voxymap$restoreVanillaFogWithoutVoxy(CallbackInfoReturnable<FogData> cir) {
        VoxyRenderGate.restoreFog(cir.getReturnValue());
    }
}
