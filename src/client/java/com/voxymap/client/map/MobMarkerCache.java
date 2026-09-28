package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MobMarkerCache {
    private static final int MAX_MARKERS = 150;
    private static final double MAX_RANGE_BLOCKS = 128.0;

    public record MobSnapshot(double x, double y, double z, boolean hostile, MobHeadIcons.Face face) {
        public MobSnapshot(double x, double y, double z, boolean hostile) {
            this(x, y, z, hostile, null);
        }
    }

    private static List<MobSnapshot> snapshot = List.of();

    private MobMarkerCache() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !VoxyMapSettings.mobsEnabled()

                || !VoxyMapSettings.minimapEnabled() || VoxyMapCameraController.isActive()) {
            snapshot = List.of();
            return;
        }

        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();

        List<MobSnapshot> candidates = new ArrayList<>();
        Map<MobSnapshot, Mob> sources = new IdentityHashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            MobSnapshot candidate = new MobSnapshot(mob.getX(), mob.getY(), mob.getZ(), mob instanceof Enemy);
            candidates.add(candidate);
            sources.put(candidate, mob);
        }
        List<MobSnapshot> kept = filterAndCap(candidates, px, pz, MAX_RANGE_BLOCKS, MAX_MARKERS);
        snapshot = MinimapHeads.shown(minecraft) ? withFaces(minecraft, kept, sources) : kept;
    }

    private static List<MobSnapshot> withFaces(Minecraft minecraft, List<MobSnapshot> mobs, Map<MobSnapshot, Mob> sources) {
        List<MobSnapshot> result = new ArrayList<>(mobs.size());
        for (MobSnapshot mob : mobs) {
            Mob source = sources.get(mob);
            MobHeadIcons.Face face = source == null ? null : MobHeadIcons.faceOf(minecraft, source);
            result.add(new MobSnapshot(mob.x(), mob.y(), mob.z(), mob.hostile(), face));
        }
        return List.copyOf(result);
    }

    static List<MobSnapshot> filterAndCap(List<MobSnapshot> candidates, double px, double pz,
                                           double maxRangeBlocks, int maxMarkers) {
        double maxDistSq = maxRangeBlocks * maxRangeBlocks;
        List<MobSnapshot> found = new ArrayList<>();
        for (MobSnapshot mob : candidates) {
            if (horizontalDistanceSq(mob, px, pz) <= maxDistSq) {
                found.add(mob);
            }
        }

        if (found.size() > maxMarkers) {

            found.sort(Comparator.comparingDouble(m -> horizontalDistanceSq(m, px, pz)));
            found = new ArrayList<>(found.subList(0, maxMarkers));
        }
        return List.copyOf(found);
    }

    private static double horizontalDistanceSq(MobSnapshot mob, double px, double pz) {
        double dx = mob.x() - px;
        double dz = mob.z() - pz;
        return dx * dx + dz * dz;
    }

    public static List<MobSnapshot> snapshot() {
        return snapshot;
    }
}
