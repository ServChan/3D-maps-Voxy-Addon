package com.voxymap.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxymap.client.VoxyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class MobHeadIcons {
    private static final String HEAD_PART = "head";
    private static final int MAX_LEARNED_TEXTURES = 4096;
    private static final FaceRect NO_FACE = new FaceRect(0.0f, 0.0f, 0.0f, 0.0f);
    private static final Map<Object, FaceRect> FACE_BY_MODEL = new WeakHashMap<>();
    private static final Map<Identifier, FaceRect> FACE_BY_TEXTURE = new HashMap<>();
    private static final Map<Class<?>, Boolean> SINGLE_MODEL_RENDERER = new HashMap<>();
    private static boolean failureLogged;

    private MobHeadIcons() {
    }

    public record FaceRect(float u0, float v0, float u1, float v1) {
        boolean isEmpty() {
            return !(u1 > u0) || !(v1 > v0);
        }
    }

    public record Face(Identifier texture, FaceRect rect) {
    }

    public static boolean learning() {
        return VoxyMapSettings.minimapEnabled() && VoxyMapSettings.mobsEnabled();
    }

    public static void learnFromRender(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state) {
        try {
            Identifier texture = textureOf(renderer, state);
            if (texture == null || FACE_BY_TEXTURE.containsKey(texture)) {
                return;
            }
            if (FACE_BY_TEXTURE.size() >= MAX_LEARNED_TEXTURES) {
                FACE_BY_TEXTURE.clear();
            }
            FACE_BY_TEXTURE.put(texture, faceRectOf(renderer.getModel()));
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
            LivingEntityRenderState state = createState(livingRenderer, entity);
            Identifier texture = textureOf(livingRenderer, state);
            if (texture == null) {
                return null;
            }
            FaceRect rect = FACE_BY_TEXTURE.get(texture);
            if (rect == null && isSingleModelRenderer(livingRenderer.getClass())) {
                rect = faceRectOf(livingRenderer.getModel());
            }
            return rect == null || rect == NO_FACE ? null : new Face(texture, rect);
        } catch (RuntimeException | LinkageError exception) {
            logOnce(exception);
            return null;
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

    private static FaceRect faceRectOf(Object model) {
        if (!(model instanceof Model<?> typedModel)) {
            return NO_FACE;
        }
        FaceRect cached = FACE_BY_MODEL.get(model);
        if (cached == null) {
            cached = computeFaceRect(typedModel.root());
            FACE_BY_MODEL.put(model, cached);
        }
        return cached;
    }

    private static FaceRect computeFaceRect(ModelPart root) {
        ModelPart head = root.createPartLookup().apply(HEAD_PART);
        if (head == null) {
            return NO_FACE;
        }
        ModelPart.Cube[] firstCube = new ModelPart.Cube[1];
        head.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (firstCube[0] == null && path.isEmpty()) {
                firstCube[0] = cube;
            }
        });
        if (firstCube[0] == null) {
            return NO_FACE;
        }
        for (ModelPart.Polygon polygon : firstCube[0].polygons) {
            if (polygon.normal().z() > -0.5f) {
                continue;
            }
            ModelPart.Vertex[] vertices = polygon.vertices();
            float[] us = new float[vertices.length];
            float[] vs = new float[vertices.length];
            for (int i = 0; i < vertices.length; i++) {
                us[i] = vertices[i].u();
                vs[i] = vertices[i].v();
            }
            FaceRect rect = boundingRect(us, vs);
            return rect.isEmpty() ? NO_FACE : rect;
        }
        return NO_FACE;
    }

    static FaceRect boundingRect(float[] us, float[] vs) {
        float u0 = Float.POSITIVE_INFINITY;
        float v0 = Float.POSITIVE_INFINITY;
        float u1 = Float.NEGATIVE_INFINITY;
        float v1 = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < us.length; i++) {
            u0 = Math.min(u0, us[i]);
            u1 = Math.max(u1, us[i]);
            v0 = Math.min(v0, vs[i]);
            v1 = Math.max(v1, vs[i]);
        }
        return new FaceRect(u0, v0, u1, v1);
    }

    private static void logOnce(Throwable exception) {
        if (!failureLogged) {
            failureLogged = true;
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not resolve a minimap head icon; affected mobs keep a dot.", exception);
        }
    }
}
