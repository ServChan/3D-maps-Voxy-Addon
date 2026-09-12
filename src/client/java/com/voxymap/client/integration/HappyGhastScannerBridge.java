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

    private static final Accessor ACCESSOR = resolveAccessor();

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
        try {
            return ACCESSOR.read();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not read the HappyGhastScanner route.", exception);
            return null;
        }
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
            Object controller = getInstance.invoke(null);

            Class<?> plannerClass = Class.forName("org.lts.happyghastscanner.SquareSpiralPlanner");
            Method getCenterX = plannerClass.getMethod("getCenterX");
            Method getCenterZ = plannerClass.getMethod("getCenterZ");
            Method getStep = plannerClass.getMethod("getStep");
            Method getCurrentSegment = plannerClass.getMethod("getCurrentSegment");

            return () -> {
                String state = ((Enum<?>) getState.invoke(controller)).name();
                if (!isActiveState(state)) {
                    return null;
                }
                Object planner = getPlanner.invoke(controller);
                if (planner == null) {
                    return null;
                }
                double centerX = (double) getCenterX.invoke(planner);
                double centerZ = (double) getCenterZ.invoke(planner);
                double step = (double) getStep.invoke(planner);
                int currentSegment = (int) getCurrentSegment.invoke(planner);
                return new Route(centerX, centerZ, step, currentSegment);
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
