package com.voxymap.client.integration;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class LitematicaPlusBridge {
    private static final String MOD_ID = "litematicaplus";
    private static final int FIELD_COUNT = 11;
    private static final int MAX_PLACEMENTS = 256;
    private static final Accessor ACCESSOR = resolveAccessor();
    private static final long CACHE_NANOS = 100_000_000L;
    private static boolean failureLogged;
    private static long cachedAtNanos = Long.MIN_VALUE / 2;
    private static List<Placement> cached = List.of();

    public record Placement(String id, String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                            double progress, boolean selected, boolean shown) {
        public double centerX() {
            return (minX + maxX) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2.0;
        }
    }

    private record Accessor(Method placements, Method select, Method openEditor) {
    }

    private LitematicaPlusBridge() {
    }

    public static boolean isAvailable() {
        return ACCESSOR != null;
    }

    public static List<Placement> placements() {
        if (ACCESSOR == null) {
            return List.of();
        }
        long now = System.nanoTime();
        if (now - cachedAtNanos < CACHE_NANOS) {
            return cached;
        }
        cachedAtNanos = now;
        cached = query();
        return cached;
    }

    private static List<Placement> query() {
        try {
            Object value = ACCESSOR.placements.invoke(null);
            if (!(value instanceof List<?> rows)) {
                return List.of();
            }
            List<Placement> result = new ArrayList<>(Math.min(rows.size(), MAX_PLACEMENTS));
            for (Object row : rows) {
                if (result.size() >= MAX_PLACEMENTS) {
                    break;
                }
                Placement placement = parse(row);
                if (placement != null) {
                    result.add(placement);
                }
            }
            return result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailure(exception);
            return List.of();
        }
    }

    static Placement parse(Object row) {
        if (!(row instanceof Object[] fields) || fields.length < FIELD_COUNT) {
            return null;
        }
        if (!(fields[0] instanceof String id) || !(fields[1] instanceof String name)) {
            return null;
        }
        for (int i = 2; i <= 7; i++) {
            if (!(fields[i] instanceof Integer)) {
                return null;
            }
        }
        if (!(fields[8] instanceof Double progress) || !(fields[9] instanceof Boolean selected)
                || !(fields[10] instanceof Boolean shown)) {
            return null;
        }
        int minX = (Integer) fields[2];
        int minY = (Integer) fields[3];
        int minZ = (Integer) fields[4];
        int maxX = (Integer) fields[5];
        int maxY = (Integer) fields[6];
        int maxZ = (Integer) fields[7];
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new Placement(id, name.length() > 96 ? name.substring(0, 96) : name, minX, minY, minZ, maxX, maxY, maxZ,
                progress, selected, shown);
    }

    public static boolean select(String id) {
        cachedAtNanos = Long.MIN_VALUE / 2;
        return call(ACCESSOR == null ? null : ACCESSOR.select, id);
    }

    public static boolean openEditor(String id) {
        return call(ACCESSOR == null ? null : ACCESSOR.openEditor, id);
    }

    private static boolean call(Method method, String id) {
        if (method == null || id == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, id));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logFailure(exception);
            return false;
        }
    }

    private static void logFailure(Throwable exception) {
        if (!failureLogged) {
            failureLogged = true;
            VoxyMapClient.LOGGER.warn("Could not call the Litematica Plus map API", exception);
        }
    }

    private static Accessor resolveAccessor() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> api = Class.forName("org.lts.litematicaplus.api.MapApi");
            return new Accessor(api.getMethod("placements"), api.getMethod("select", String.class),
                    api.getMethod("openEditor", String.class));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("Could not find the Litematica Plus map API; the integration is off", exception);
            return null;
        }
    }
}
