package com.voxymap.client.mixin;

import com.voxymap.client.map.MapEntityOcclusion;
import com.voxymap.client.map.MapEntityScale;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelExtractor.class, remap = false)
public abstract class LevelExtractorMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void voxymap$showModelsOutsideCompiledSections(Entity entity, Frustum frustum, double cameraX, double cameraY,
                                                           double cameraZ, float partialTick, long fadeInMillis,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (MapEntityScale.hiddenOnMap(entity) || !MapEntityOcclusion.visible(entity)) {
            cir.setReturnValue(false);
        } else if (MapEntityScale.shown(entity)) {
            cir.setReturnValue(true);
        }
    }
}
