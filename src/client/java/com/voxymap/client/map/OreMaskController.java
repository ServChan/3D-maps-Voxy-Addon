package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;

public final class OreMaskController {
    private static final Field MODEL_SERVICE_FIELD = resolveModelServiceField();
    private static boolean accessFailureLogged = false;
    private static WorldIdentifier lastWorldId;

    private static ModelFactory lastModelFactory;

    private static boolean[] classified = new boolean[0];

    private static short[] unbakedTicks = new short[0];

    private static int firstUnclassified = 0;

    private static final int GIVE_UP_AFTER_TICKS = 1200;

    private OreMaskController() {
    }

    public static boolean isSupported() {
        return MODEL_SERVICE_FIELD != null && !accessFailureLogged;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || MODEL_SERVICE_FIELD == null) {
            return;
        }

        WorldIdentifier worldId = WorldIdentifier.of(minecraft.level);
        if (worldId == null) {
            return;
        }
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) {
            return;
        }
        Mapper mapper = engine.getMapper();
        VoxyRenderSystem renderSystem = IVoxyRenderSystemHolder.getNullable();
        if (renderSystem == null) {
            return;
        }
        ModelBakerySubsystem modelService = modelService(renderSystem);
        if (modelService == null) {
            return;
        }
        ModelFactory factory = modelService.factory;

        if (!worldId.equals(lastWorldId) || factory != lastModelFactory) {
            lastWorldId = worldId;
            lastModelFactory = factory;
            classified = new boolean[0];
            unbakedTicks = new short[0];
            firstUnclassified = 0;
            OreMaskTexture.reset();
        }

        int knownCount = mapper.getBlockStateCount();
        if (firstUnclassified >= knownCount) {
            return;
        }
        if (knownCount > classified.length) {
            classified = java.util.Arrays.copyOf(classified, knownCount);
            unbakedTicks = java.util.Arrays.copyOf(unbakedTicks, knownCount);
        }

        Registry<Block> blockRegistry = minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);

        while (firstUnclassified < knownCount && classified[firstUnclassified]) {
            firstUnclassified++;
        }
        for (int id = firstUnclassified; id < knownCount; id++) {
            if (classified[id]) {
                if (id == firstUnclassified) {
                    firstUnclassified++;
                }
                continue;
            }
            if (!factory.hasModelForBlockId(id)) {
                if (++unbakedTicks[id] > GIVE_UP_AFTER_TICKS) {

                    classified[id] = true;
                    if (id == firstUnclassified) {
                        firstUnclassified++;
                    }
                }
                continue;
            }
            BlockState state = mapper.getBlockStateFromBlockId(id);
            if (state != null && isOre(blockRegistry, state)) {
                OreMaskTexture.markOre(factory.getModelId(id));
            }
            classified[id] = true;
            if (id == firstUnclassified) {
                firstUnclassified++;
            }
        }
        while (firstUnclassified < knownCount && classified[firstUnclassified]) {
            firstUnclassified++;
        }
    }

    private static boolean isOre(Registry<Block> blockRegistry, BlockState state) {
        Identifier key = blockRegistry.getKey(state.getBlock());
        if (key == null) {
            return false;
        }

        String id = key.toString();
        if (!id.startsWith("minecraft:")) {
            return false;
        }
        return id.endsWith("_ore") || id.equals("minecraft:ancient_debris");
    }

    private static WorldEngine liveEngine(WorldIdentifier worldId) {
        VoxyInstance instance = VoxyCommon.getInstance();
        return instance == null ? null : instance.getNullable(worldId);
    }

    private static ModelBakerySubsystem modelService(VoxyRenderSystem renderSystem) {
        try {
            return (ModelBakerySubsystem) MODEL_SERVICE_FIELD.get(renderSystem);
        } catch (IllegalAccessException | RuntimeException exception) {
            if (!accessFailureLogged) {
                accessFailureLogged = true;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Could not read Voxy's model service; ore masking is disabled.", exception);
            }
            return null;
        }
    }

    private static Field resolveModelServiceField() {
        try {
            Field field = VoxyRenderSystem.class.getDeclaredField("modelService");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException exception) {
            VoxyMapClient.LOGGER.info("[VoxyMap] Voxy's render system layout changed; ore masking is disabled.", exception);
            return null;
        }
    }
}
