package com.voxymap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.VoxyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class MobHeadIcons {
    private static final int MAX_ICONS = 512;
    private static final int UPLOADS_PER_TICK = 6;

    private static final Map<Identifier, Entry> ENTRIES = new HashMap<>();
    private static final Map<Class<?>, Boolean> SINGLE_MODEL_RENDERER = new HashMap<>();
    private static int nextIconId;
    private static boolean failureLogged;
    private static BlockStateModelSet loadedModels;

    private MobHeadIcons() {
    }

    public record Face(Identifier icon, int width, int height) {
    }

    private static final class Entry {
        CompletableFuture<HeadIconComposer.Icon> pending;
        Face face;
    }

    public static boolean learning() {
        return VoxyMapSettings.minimapEnabled() && VoxyMapSettings.mobsEnabled();
    }

    public static void learnFromRender(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state) {
        try {
            Identifier texture = textureOf(renderer, state);
            if (texture != null && !ENTRIES.containsKey(texture)) {
                request(texture, renderer.getModel());
            }
        } catch (RuntimeException | LinkageError exception) {
            logOnce(exception);
        }
    }

    static Face faceOf(Minecraft minecraft, LivingEntity entity) {
        try {
            EntityRenderer<?, ?> renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) {
                return null;
            }
            Identifier texture = textureOf(livingRenderer, createState(livingRenderer, entity));
            if (texture == null) {
                return null;
            }
            Entry entry = ENTRIES.get(texture);
            if (entry == null && isSingleModelRenderer(livingRenderer.getClass())) {
                entry = request(texture, livingRenderer.getModel());
            }
            return entry == null ? null : entry.face;
        } catch (RuntimeException | LinkageError exception) {
            logOnce(exception);
            return null;
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        BlockStateModelSet models = MinimapBlockColors.loadedModels(minecraft);
        if (models == null) {
            return;
        }
        if (models != loadedModels) {
            loadedModels = models;
            clear(minecraft);
        }
        int uploads = 0;
        for (Map.Entry<Identifier, Entry> mapping : ENTRIES.entrySet()) {
            Entry entry = mapping.getValue();
            if (entry.pending == null || !entry.pending.isDone()) {
                continue;
            }
            CompletableFuture<HeadIconComposer.Icon> done = entry.pending;
            entry.pending = null;
            try {
                HeadIconComposer.Icon icon = done.join();
                if (icon != null) {
                    entry.face = upload(minecraft, icon);
                }
            } catch (RuntimeException | LinkageError exception) {
                logOnce(exception);
            }
            if (++uploads >= UPLOADS_PER_TICK) {
                break;
            }
        }
    }

    private static Entry request(Identifier texture, Object model) {
        if (ENTRIES.size() >= MAX_ICONS) {
            clear(Minecraft.getInstance());
        }
        Entry entry = new Entry();
        ENTRIES.put(texture, entry);
        if (!(model instanceof Model<?> typedModel)) {
            return entry;
        }
        List<HeadIconComposer.Quad> quads = HeadGeometry.capture(typedModel);
        if (quads.isEmpty()) {
            return entry;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            return entry;
        }
        Resource found = resource.get();
        entry.pending = CompletableFuture.supplyAsync(() -> compose(found, quads));
        return entry;
    }

    private static HeadIconComposer.Icon compose(Resource resource, List<HeadIconComposer.Quad> quads) {
        try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = image.getPixel(x, y);
                }
            }
            return HeadIconComposer.compose(quads, width, height, pixels);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static Face upload(Minecraft minecraft, HeadIconComposer.Icon icon) {
        NativeImage image = new NativeImage(icon.width() * 2, icon.height(), true);
        for (int y = 0; y < icon.height(); y++) {
            for (int x = 0; x < icon.width(); x++) {
                int color = icon.argb()[y * icon.width() + x];
                image.setPixel(x, y, color);
                image.setPixel(icon.width() + x, y, (color >>> 24) == 0 ? 0 : 0xFFFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(VoxyMapClient.MOD_ID, "mob_icon/" + nextIconId++);
        minecraft.getTextureManager().register(id, new DynamicTexture(() -> "VoxyMap mob icon", image));
        return new Face(id, icon.width(), icon.height());
    }

    private static void clear(Minecraft minecraft) {
        for (Iterator<Entry> iterator = ENTRIES.values().iterator(); iterator.hasNext(); ) {
            Entry entry = iterator.next();
            if (entry.face != null) {
                minecraft.getTextureManager().release(entry.face.icon());
            }
            iterator.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity, S extends LivingEntityRenderState> S createState(
            LivingEntityRenderer<?, ?, ?> renderer, LivingEntity entity) {
        return ((LivingEntityRenderer<T, S, ?>) renderer).createRenderState((T) entity, 1.0f);
    }

    @SuppressWarnings("unchecked")
    private static <S extends LivingEntityRenderState> Identifier textureOf(
            LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state) {
        return ((LivingEntityRenderer<?, S, ?>) renderer).getTextureLocation((S) state);
    }

    static boolean isSingleModelRenderer(Class<?> rendererClass) {
        return SINGLE_MODEL_RENDERER.computeIfAbsent(rendererClass, MobHeadIcons::declaresNoExtraModels);
    }

    private static boolean declaresNoExtraModels(Class<?> rendererClass) {
        for (Class<?> type = rendererClass; type != null && type != LivingEntityRenderer.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (Model.class.isAssignableFrom(fieldType) || AdultAndBabyModelPair.class.isAssignableFrom(fieldType)
                        || Map.class.isAssignableFrom(fieldType) || Collection.class.isAssignableFrom(fieldType)
                        || fieldType.isArray()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void logOnce(Throwable exception) {
        if (!failureLogged) {
            failureLogged = true;
            VoxyMapClient.LOGGER.warn("Could not resolve a minimap head icon; affected mobs keep a dot", exception);
        }
    }
}
