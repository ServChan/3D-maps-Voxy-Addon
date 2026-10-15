package com.voxymap.client.mixin;

import com.voxymap.client.map.Map2dTiles;
import com.voxymap.client.map.MinimapRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class, remap = false)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), remap = false, require = 0)
    private void voxymap$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (oldState != newState) {
            MinimapRenderer.blockChanged(pos.getX(), pos.getZ());
            Map2dTiles.chunkChanged(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"), remap = false, require = 0)
    private void voxymap$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        MinimapRenderer.markDirty(pos.x(), pos.z());
        MinimapRenderer.markDirty(pos.x() + 1, pos.z());
        MinimapRenderer.markDirty(pos.x(), pos.z() + 1);
        Map2dTiles.chunkChanged(pos.x(), pos.z());
    }
}
