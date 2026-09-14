package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Refreshes a small, already-filtered snapshot of nearby mobs once per client tick (20/sec) instead of
 * every render frame - {@link MinimapRenderer}'s mob markers draw from this one snapshot instead of
 * iterating every tracked entity in the world itself, every frame.
 *
 * <p>Iterating the entity list, type-checking, and distance-filtering is cheap done once - what actually
 * cost real frame time with a lot of mobs nearby was doing that same work, plus several fill() draw calls
 * per mob, on the render thread at full frame rate (tens to hundreds of times a second, worse still while
 * dragging the fullscreen map's camera, since Voxy already re-renders its whole LOD scene every such
 * frame). Decoupling the expensive part from render frequency - and capping/sorting so a mob farm can't
 * blow the count up unbounded - fixes that without needing a background thread: entity position fields
 * aren't documented as safe to read from outside the client thread, so this stays on it, just off the
 * once-a-frame render path.
 */
public final class MobMarkerCache {
    private static final int MAX_MARKERS = 150;

    public record MobSnapshot(double x, double y, double z, boolean hostile) {
    }

    private static List<MobSnapshot> snapshot = List.of();

    private MobMarkerCache() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !VoxyMapSettings.mobsEnabled()
                // The only consumer is MinimapRenderer, which itself draws nothing while disabled in
                // Settings or hidden behind the fullscreen map - skip the whole entity scan whenever
                // neither will actually read the snapshot this tick produces.
                || !VoxyMapSettings.minimapEnabled() || VoxyMapCameraController.isActive()) {
            snapshot = List.of();
            return;
        }

        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();
        double maxDistSq = 128.0 * 128.0;

        List<MobSnapshot> found = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            double dx = mob.getX() - px;
            double dz = mob.getZ() - pz;
            if (dx * dx + dz * dz > maxDistSq) {
                continue;
            }
            found.add(new MobSnapshot(mob.getX(), mob.getY(), mob.getZ(), mob instanceof Enemy));
        }

        if (found.size() > MAX_MARKERS) {
            // Nearest mobs matter most on a small overlay - if a farm/grinder produced far more than can
            // usefully be drawn anyway, keep the closest MAX_MARKERS instead of an arbitrary iteration
            // order slice.
            found.sort(Comparator.comparingDouble(m -> horizontalDistanceSq(m, px, pz)));
            found = new ArrayList<>(found.subList(0, MAX_MARKERS));
        }
        snapshot = List.copyOf(found);
    }

    private static double horizontalDistanceSq(MobSnapshot mob, double px, double pz) {
        double dx = mob.x() - px;
        double dz = mob.z() - pz;
        return dx * dx + dz * dz;
    }

    /** The current snapshot - safe to iterate from the render thread without touching live entities. */
    public static List<MobSnapshot> snapshot() {
        return snapshot;
    }
}
