package com.voxymap.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.voxymap.client.VoxyMapClient;
import org.lwjgl.opengl.GL;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class GraphicsBackend {
    public enum Kind { OPENGL, VULKAN, OTHER }

    private static volatile Kind detected;

    private GraphicsBackend() {
    }

    public static boolean openGl() {
        Kind kind = detected;
        if (kind == null) {
            kind = detect();
            if (kind == null) {
                return false;
            }
        }
        return kind == Kind.OPENGL;
    }

    static Kind classify(String backendName, BooleanSupplier glContextCurrent) {
        String name = backendName == null ? "" : backendName.toLowerCase(Locale.ROOT);
        if (name.contains("opengl")) {
            return Kind.OPENGL;
        }
        if (name.contains("vulkan")) {
            return Kind.VULKAN;
        }
        return glContextCurrent.getAsBoolean() ? Kind.OPENGL : Kind.OTHER;
    }

    private static synchronized Kind detect() {
        if (detected != null) {
            return detected;
        }
        String name;
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) {
                return null;
            }
            name = device.getDeviceInfo().backendName();
        } catch (RuntimeException | LinkageError exception) {
            name = null;
        }
        Kind kind = classify(name, GraphicsBackend::glContextCurrent);
        detected = kind;
        if (kind != Kind.OPENGL) {
            VoxyMapClient.LOGGER.info("Graphics backend is {}, not OpenGL; the ore mask, the Nether roof cut and the 3D window map are off",
                    name == null ? kind : name);
        }
        return kind;
    }

    private static boolean glContextCurrent() {
        if (!RenderSystem.isOnRenderThread()) {
            return false;
        }
        try {
            GL.getCapabilities();
            return true;
        } catch (IllegalStateException | LinkageError exception) {
            return false;
        }
    }
}
