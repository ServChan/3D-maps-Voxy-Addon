package com.voxymap.client.mixin;

import com.voxymap.client.map.VoxyMapCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ParticleEngine.class, remap = false)
public abstract class ParticleEngineMixin {
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$skipParticlesOnMap(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick,
                                            CallbackInfo ci) {
        if (VoxyMapCameraController.isActive()) {
            state.reset();
            ci.cancel();
        }
    }
}
