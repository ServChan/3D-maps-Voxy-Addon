package com.voxymap.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GameRenderer.class, remap = false)
public interface GameRendererAccessor {
    @Accessor(value = "lightmapRenderStateExtractor", remap = false)
    LightmapRenderStateExtractor getVoxymapLightmapExtractor();
}
