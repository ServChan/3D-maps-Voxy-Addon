package com.voxymap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class MinimapBlockColors {
    private static final double WATER_BRIGHTNESS = 0.78;
    private static final long MODEL_SEED = 42L;

    private record Sample(int argb, int tintIndex) {
        static final Sample NONE = new Sample(0, -1);
    }

    private static final Map<BlockState, Sample> SAMPLES = new ConcurrentHashMap<>();
    private static volatile BlockStateModelSet sampledModels;
    private static volatile boolean disabled;

    private MinimapBlockColors() {
    }

    static int blockColor(Level level, BlockState state, BlockPos pos) {
        if (disabled) {
            return 0;
        }
        try {
            Sample sample = sample(state);
            if (sample.argb() == 0) {
                return 0;
            }
            if (sample.tintIndex() < 0 || !(level instanceof BlockAndTintGetter tintGetter)) {
                return sample.argb();
            }
            BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, sample.tintIndex());
            return source == null ? sample.argb()
                    : MapColorMath.multiply(sample.argb(), source.colorInWorld(state, tintGetter, pos));
        } catch (RuntimeException | LinkageError exception) {
            disable(exception);
            return 0;
        }
    }

    static int waterColor(Level level, BlockPos pos) {
        if (disabled || !(level instanceof BlockAndTintGetter tintGetter)) {
            return 0;
        }
        try {
            return MapColorMath.scale(0xFF000000 | BiomeColors.getAverageWaterColor(tintGetter, pos), WATER_BRIGHTNESS);
        } catch (RuntimeException | LinkageError exception) {
            disable(exception);
            return 0;
        }
    }

    static BlockStateModelSet loadedModels(Minecraft minecraft) {
        try {
            return minecraft.getModelManager().getBlockStateModelSet();
        } catch (NullPointerException notLoadedYet) {
            return null;
        }
    }

    private static Sample sample(BlockState state) {
        BlockStateModelSet models = loadedModels(Minecraft.getInstance());
        if (models == null) {
            return Sample.NONE;
        }
        if (models != sampledModels) {
            SAMPLES.clear();
            sampledModels = models;
        }
        Sample cached = SAMPLES.get(state);
        if (cached != null) {
            return cached;
        }
        Sample sampled = sampleModel(models.get(state));
        SAMPLES.put(state, sampled);
        return sampled;
    }

    private static Sample sampleModel(BlockStateModel model) {
        if (model == null) {
            return Sample.NONE;
        }
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(MODEL_SEED), parts);
        BakedQuad quad = topQuad(parts);
        if (quad != null) {
            int argb = averageOf(quad.materialInfo().sprite());
            if (argb != 0) {
                return new Sample(argb, quad.materialInfo().isTinted() ? quad.materialInfo().tintIndex() : -1);
            }
        }
        int particle = averageOf(model.particleMaterial().sprite());
        return particle == 0 ? Sample.NONE : new Sample(particle, -1);
    }

    private static BakedQuad topQuad(List<BlockStateModelPart> parts) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> up = part.getQuads(Direction.UP);
            if (!up.isEmpty()) {
                return up.getFirst();
            }
        }
        BakedQuad fallback = null;
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == Direction.UP) {
                    return quad;
                }
                if (fallback == null) {
                    fallback = quad;
                }
            }
        }
        return fallback;
    }

    private static int averageOf(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return 0;
        }
        SpriteContents contents = sprite.contents();
        NativeImage image = ((SpriteContentsAccessor) contents).getVoxymapOriginalImage();
        if (image == null) {
            return 0;
        }
        int width = Math.min(contents.width(), image.getWidth());
        int height = Math.min(contents.height(), image.getHeight());
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = image.getPixel(x, y);
            }
        }
        return MapColorMath.averageArgb(pixels);
    }

    private static void disable(Throwable cause) {
        if (!disabled) {
            disabled = true;
            VoxyMapClient.LOGGER.warn("[VoxyMap] Texture colours for the minimap are unavailable, using map colours", cause);
        }
    }
}
