package com.voxymap.client.map;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.lang.reflect.Method;

public final class WindowMapRenderer {
    public static final Object VIEWPORT_KEY = new Object();
    private static final int MAX_TARGET_SIZE = 2048;
    private static final float NEAR_PLANE = 0.5f;
    private static final float FAR_PLANE = 16384.0f;
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.043f, 0.071f, 0.125f, 1.0f);
    private static final float NO_FOG_DISTANCE = 1.0E7f;
    private static final FogParameters NO_FOG = new FogParameters(CLEAR_COLOR.x, CLEAR_COLOR.y, CLEAR_COLOR.z, 0.0f,
            NO_FOG_DISTANCE, NO_FOG_DISTANCE, NO_FOG_DISTANCE, NO_FOG_DISTANCE);
    private static final Method GET_OR_CREATE = resolveGetOrCreate();

    public enum Status { READY, NO_VOXY, SHADER_PACK, FAILED }

    private static boolean rendering;
    private static boolean failed;
    private static RenderTarget target;
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static boolean hasFrame;

    private WindowMapRenderer() {
    }

    public static boolean rendering() {
        return rendering;
    }

    public static Object windowViewport(Object selector) {
        if (GET_OR_CREATE == null) {
            return null;
        }
        try {
            return GET_OR_CREATE.invoke(selector, VIEWPORT_KEY);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            return null;
        }
    }

    public static Status status() {
        if (failed || GET_OR_CREATE == null || !GraphicsBackend.openGl()) {
            return Status.FAILED;
        }
        if (shaderPackInUse()) {
            return Status.SHADER_PACK;
        }
        try {
            return IVoxyRenderSystemHolder.getNullable() == null ? Status.NO_VOXY : Status.READY;
        } catch (RuntimeException | LinkageError exception) {
            fail(exception);
            return Status.FAILED;
        }
    }

    public static boolean render(VoxyMapCameraController.Pose pose, int width, int height) {
        hasFrame = false;
        if (status() != Status.READY || width <= 0 || height <= 0) {
            return false;
        }
        int targetWidth = Math.min(width, MAX_TARGET_SIZE);
        int targetHeight = Math.min(height, MAX_TARGET_SIZE);
        try {
            VoxyRenderSystem system = IVoxyRenderSystemHolder.getNullable();
            if (target == null) {
                target = new TextureTarget("VoxyMap window map", targetWidth, targetHeight, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            } else if (target.width != targetWidth || target.height != targetHeight) {
                target.resize(targetWidth, targetHeight);
            }
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), CLEAR_COLOR,
                    target.getDepthTexture(), RenderProperties.getRenderProperties().clearDepth());

            Projection projection = new Projection();
            projection.setupPerspective(NEAR_PLANE, FAR_PLANE, pose.fov(), targetWidth, targetHeight);
            Matrix4f projectionMatrix = projection.getMatrix(new Matrix4f());
            Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(pose.yaw()),
                    -(float) Math.toRadians(pose.pitch()), 0.0f);
            Matrix4f modelView = new Matrix4f().rotation(rotation.conjugate(new Quaternionf()));

            rendering = true;
            Viewport<?> viewport = system.setupViewport(projectionMatrix, modelView, NO_FOG,
                    targetWidth, targetHeight, pose.x(), pose.y(), pose.z());
            if (viewport == null) {
                return false;
            }
            system.renderOpaque(viewport, ((GlTextureView) target.getDepthTextureView()).glId(),
                    ((GlTextureView) target.getColorTextureView()).glId());
            VIEW_PROJECTION.set(projectionMatrix).mul(modelView);
            cameraX = pose.x();
            cameraY = pose.y();
            cameraZ = pose.z();
            hasFrame = true;
            return true;
        } catch (RuntimeException | LinkageError exception) {
            fail(exception);
            return false;
        } finally {
            rendering = false;
        }
    }

    public static GpuTextureView colorView() {
        return hasFrame && target != null ? target.getColorTextureView() : null;
    }

    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static double[] project(double worldX, double worldY, double worldZ) {
        if (!hasFrame) {
            return null;
        }
        Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float) (worldX - cameraX), (float) (worldY - cameraY),
                (float) (worldZ - cameraZ), 1.0f));
        if (clip.w <= 0.05f) {
            return null;
        }
        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) {
            return null;
        }
        return new double[]{Math.clamp(ndcX, -1.0E6, 1.0E6), Math.clamp(ndcY, -1.0E6, 1.0E6)};
    }

    public static void release() {
        hasFrame = false;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    private static boolean shaderPackInUse() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }
        try {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        } catch (RuntimeException | LinkageError exception) {
            return true;
        }
    }

    private static void fail(Throwable exception) {
        if (!failed) {
            failed = true;
            VoxyMapClient.LOGGER.warn("Could not render a second Voxy view; the 3D map in the Spatial GUI window is off", exception);
        }
    }

    private static Method resolveGetOrCreate() {
        try {
            Method method = Class.forName("me.cortex.voxy.client.core.rendering.ViewportSelector")
                    .getDeclaredMethod("getOrCreate", Object.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            VoxyMapClient.LOGGER.warn("Could not find Voxy's extra viewports; the window map stays flat", exception);
            return null;
        }
    }
}
