package com.voxymap.client.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import com.voxymap.client.map.RoomCutawayController;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the overlapping vanilla/Sodium terrain only while the shader cutaway is actually active.
 * Keeping this pass disabled for every map frame exposes Voxy's intentionally coarse near-camera LOD
 * and produces the giant stretched chunk surfaces seen when cutaway is switched off.
 */
@Mixin(value = ChunkSectionsToRender.class, priority = 2000)
public abstract class MapVanillaTerrainMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"), cancellable = true)
    private void voxymap$hideVanillaTerrainInMap(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (RoomCutawayController.isActive()) {
            ci.cancel();
        }
    }
}
