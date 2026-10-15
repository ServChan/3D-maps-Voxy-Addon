package com.voxymap.client.mixin;

import com.mojang.blaze3d.audio.ListenerTransform;
import com.voxymap.client.map.VoxyMapCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = SoundEngine.class, remap = false)
public abstract class SoundEngineMixin {
    @ModifyVariable(method = "updateSource", at = @At("STORE"), ordinal = 0, remap = false, require = 0)
    private ListenerTransform voxymap$listenFromThePlayerOnTheMap(ListenerTransform transform) {
        if (!VoxyMapCameraController.isActive()) {
            return transform;
        }
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (entity == null) {
            return transform;
        }
        return new ListenerTransform(entity.getEyePosition(), entity.getViewVector(1.0f), entity.getUpVector(1.0f));
    }
}
