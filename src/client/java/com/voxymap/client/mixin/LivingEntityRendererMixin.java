package com.voxymap.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxymap.client.map.MapEntityScale;
import com.voxymap.client.map.MobHeadIcons;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void voxymap$learnMinimapHeadIcon(LivingEntityRenderState state, PoseStack poseStack,
                                              SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (MobHeadIcons.learning()) {
            MobHeadIcons.learnFromRender((LivingEntityRenderer<?, ?, ?>) (Object) this, state);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void voxymap$enlargeModelsOnMap(LivingEntity entity, LivingEntityRenderState state, float partialTick,
                                            CallbackInfo ci) {
        if (MapEntityScale.shown(entity)) {
            state.scale *= MapEntityScale.factor(entity, state.x, state.y, state.z);
        }
    }
}
