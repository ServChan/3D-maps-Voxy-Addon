package com.voxymap.client.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import com.voxymap.client.map.VoxyMapCameraController;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkSectionsToRender.class, priority = 2000)
public abstract class NetherChunkLayerMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
    private void voxymap$hideVanillaNetherTerrain(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (VoxyMapCameraController.isNetherSliceActive()) {
            ci.cancel();
        }
    }
}
