package com.voxymap.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SpriteContents.class, remap = false)
public interface SpriteContentsAccessor {
    @Accessor(value = "originalImage", remap = false)
    NativeImage getVoxymapOriginalImage();
}
