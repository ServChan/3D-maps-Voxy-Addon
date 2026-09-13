package com.voxymap.client.integration;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Reads HappyGhastScanner's current spiral-scan route through reflection - the same soft-dependency
 * pattern as {@link XaeroWorldMapBridge}: no compile-time or Maven dependency on that mod, so VoxyMap
 * builds and runs identically whether or not it is installed.
 */
public final class HappyGhastScannerBridge {
    private static final String MOD_ID = "happyghastscanner";
    private static final String[] ACTIVE_STATES = {"ASCENDING", "SCANNING", "PAUSED"};
    private static final long SNAPSHOT_INTERVAL_NANOS = 100_000_000L;
    private static final double MAX_ABS_COORDINATE = 60_000_000.0;
    private static final double MAX_STEP = 1_000_000.0;
    private static final int MAX_SEGMENT = 1_000_000;

    private static final Accessor ACCESSOR = resolveAccessor();
    private static volatile Route cachedRoute;
    private static volatile long lastReadNanos = Long.MIN_VALUE;
    private static volatile boolean readFailureLogged;

    private HappyGhastScannerBridge() {
    }

    /** Whether HappyGhastScanner is installed and its scan-route API could be resolved. */
    public static boolean isAvailable() {
        return ACCESSOR != null;
    }

    /** The scanner's current spiral route, or {@code null} if unavailable, idle, or finished. */
    public static Route currentRoute() {
        if (ACCESSOR == null) {
            return null;
        }
        long now = System.nanoTime();
        if (lastReadNanos != Long.MIN_VALUE && now - lastReadNanos < SNAPSHOT_INTERVAL_NANOS) {
            return cachedRoute;
        }
        try {
            cachedRoute = ACCESSOR.read();
            readFailureLogged = false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            cachedRoute = null;
            if (!readFailureLogged) {
                readFailureLogged = true;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Could not read the HappyGhastScanner route; further failures will be suppressed.", exception);
            }
        } finally {
            lastReadNanos = now;
        }
        return cachedRoute;
    }

    private static Accessor resolveAccessor() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> controllerClass = Class.forName("org.lts.happyghastscanner.SpiralFlightController");
            Method getInstance = controllerClass.getMethod("getInstance");
            Method getState = controllerClass.getMethod("getState");
            Method getPlanner = controllerClass.getMethod("getPlanner");
            Class<?> plannerClass = Class.forName("org.lts.happyghastscanner.SquareSpiralPlanner");
            Method getCenterX = plannerClass.getMethod("getCenterX");
            Method getCenterZ = plannerClass.getMethod("getCenterZ");
            Method getStep = plannerClass.getMethod("getStep");
            Method getCurrentSegment = plannerClass.getMethod("getCurrentSegment");

            return () -> {
                // The scanner singleton may not exist yet while mods are still initializing. Do not
                // permanently capture that early null; resolve the current instance for each sampled
                // snapshot (at most ten times per second).
                Object controller = getInstance.invoke(null);
                if (controller == null) {
                    return null;
                }
                Object stateValue = getState.invoke(controller);
                if (!(stateValue instanceof Enum<?> stateEnum)) {
                    return null;
                }
                String state = stateEnum.name();
                if (!isActiveState(state)) {
                    return null;
                }
                Object planner = getPlanner.invoke(controller);
                if (planner == null) {
                    return null;
                }
                Object centerX = getCenterX.invoke(planner);
                Object centerZ = getCenterZ.invoke(planner);
                Object step = getStep.invoke(planner);
                Object currentSegment = getCurrentSegment.invoke(planner);
                if (!(centerX instanceof Number x) || !(centerZ instanceof Number z)
                        || !(step instanceof Number spacing) || !(currentSegment instanceof Number segment)) {
                    return null;
                }
                return validatedRoute(x.doubleValue(), z.doubleValue(), spacing.doubleValue(), segment.intValue());
            };
        } catch (ReflectiveOperationException | LinkageError exception) {
            VoxyMapClient.LOGGER.warn("[VoxyMap] HappyGhastScanner is installed but its API is incompatible.", exception);
            return null;
        }
    }

    private static boolean isActiveState(String state) {
        for (String active : ACTIVE_STATES) {
            if (active.equals(state)) {
                return true;
            }
        }
        return false;
    }

    static Route validatedRoute(double centerX, double centerZ, double step, int currentSegment) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(step)
                || Math.abs(centerX) > MAX_ABS_COORDINATE || Math.abs(centerZ) > MAX_ABS_COORDINATE
                || step <= 0.0 || step > MAX_STEP || currentSegment < 0 || currentSegment > MAX_SEGMENT) {
            return null;
        }
        return new Route(centerX, centerZ, step, currentSegment);
    }

    @FunctionalInterface
    private interface Accessor {
        Route read() throws ReflectiveOperationException;
    }

    /** @param currentSegment the spiral segment (4 per lap) the scanner is currently flying toward */
    public record Route(double centerX, double centerZ, double step, int currentSegment) {
        public int currentLap() {
            return (currentSegment / 4) + 1;
        }
    }
}
