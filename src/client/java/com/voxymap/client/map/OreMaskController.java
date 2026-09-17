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

/**
 * Classifies newly-discovered blocks as ore or not (by vanilla registry name - "diamond_ore",
 * "deepslate_iron_ore", "ancient_debris", etc.) and marks their {@link OreMaskTexture} entry so the
 * patched Voxy fragment shader renders them as plain stone instead of their real texture.
 *
 * <p>This exists because Voxy meshes the boundary of its cached voxel data as an ordinary opaque wall
 * whenever a neighbouring section hasn't been explored/loaded yet (its mesher has no way to tell "real
 * cave wall" from "edge of what's been explored so far"), so any ore sitting on that boundary renders
 * with its true appearance - an unintended x-ray. Voxy gives the shader no signal to tell those two
 * cases apart, so masking applies to every rendered instance of an ore block, not only ones sitting on
 * such a boundary.
 *
 * <p>Reaches into Voxy's live render system via reflection (see {@link #MODEL_SERVICE_FIELD}) because
 * {@code VoxyRenderSystem} has no public accessor for its model bakery - mirrors {@link VoxyBridge}'s
 * established, defensive, cache-once, disable-forever-on-failure pattern for touching Voxy internals
 * VoxyMap doesn't own, so a future Voxy update that renames or removes the field just turns this feature
 * off instead of breaking anything else.
 */
public final class OreMaskController {
    private static final Field MODEL_SERVICE_FIELD = resolveModelServiceField();
    private static boolean accessFailureLogged = false;
    private static WorldIdentifier lastWorldId;
    /** Model ids are local to this factory and may be reassigned after an Iris shader/resource reload. */
    private static ModelFactory lastModelFactory;
    /** classified[id] once that block id has been checked and (if it was baked yet) resolved - see
     * {@link #tick}'s comment on why this can't just be "highest id checked so far". */
    private static boolean[] classified = new boolean[0];
    /** How many consecutive ticks {@code classified[id]} has stayed false purely because its model
     * still isn't baked - see {@link #GIVE_UP_AFTER_TICKS}. */
    private static short[] unbakedTicks = new short[0];
    /** The lowest id not yet in {@code classified} - advances past a fully-classified prefix so a long
     * session doesn't re-scan ids settled long ago, but a gap (an id whose model isn't baked yet) never
     * blocks anything past it from being checked. */
    private static int firstUnclassified = 0;
    /** An id stuck unbaked for this many ticks (~60s at 20 tps) gives up rather than keeps forcing every
     * tick to rescan the ever-growing range after it - see the "give up" branch in {@link #tick}. An id
     * whose model never bakes is, by the boundary-wall reasoning in this class's own doc, never actually
     * rendered with its true texture either, so there is nothing left for it to leak - skipping it for
     * good costs nothing but the CPU that would otherwise be spent re-checking it forever. */
    private static final int GIVE_UP_AFTER_TICKS = 1200;

    private OreMaskController() {
    }

    /** Whether this Voxy build exposes the model service required for the anti-xray ore mask. */
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

        // Toggling Iris shaders rebuilds Voxy's render pipeline/model bakery. Its numeric model ids
        // are not stable across that rebuild: keeping the old R8 mask can therefore turn ordinary
        // grass, leaves and stone into the mask's flat grey/white replacement. Reset both the GPU
        // lookup and the CPU classification so ores are re-marked against the new factory.
        if (!worldId.equals(lastWorldId) || factory != lastModelFactory) {
            lastWorldId = worldId;
            lastModelFactory = factory;
            classified = new boolean[0];
            unbakedTicks = new short[0];
            firstUnclassified = 0;
            OreMaskTexture.reset();
        }

        // Capacity must be established after the lifecycle reset above. Doing this before the reset
        // left both arrays empty and crashed the very first tick of every newly-created Voxy factory.
        int knownCount = mapper.getBlockStateCount();
        if (firstUnclassified >= knownCount) {
            return;
        }
        if (knownCount > classified.length) {
            classified = java.util.Arrays.copyOf(classified, knownCount);
            unbakedTicks = java.util.Arrays.copyOf(unbakedTicks, knownCount);
        }

        Registry<Block> blockRegistry = minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
        // Voxy bakes models as they're first rendered, not in id-assignment order - a rarely-seen id
        // sitting unbaked between two baked ones must never stall classification of everything after it,
        // so every not-yet-classified id in range is retried each tick rather than stopping at the first
        // gap.
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
                    // See GIVE_UP_AFTER_TICKS - give up rather than let this one id force every future
                    // tick to keep rescanning the whole, ever-growing [firstUnclassified, knownCount)
                    // range for the rest of the session.
                    classified[id] = true;
                    if (id == firstUnclassified) {
                        firstUnclassified++;
                    }
                }
                continue; // Not baked yet - retry this specific id next tick.
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
        // toString(), not a namespace()/path() accessor pair, to match this codebase's own established
        // Identifier-handling convention (see RoomCutawayController/VoxyMapCameraController) rather than
        // guess at accessor names in a mapping that has already renamed ResourceLocation once.
        String id = key.toString();
        if (!id.startsWith("minecraft:")) {
            return false; // Modded ores aren't classified - no reliable version-proof signal for them.
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
