package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MobMarkerCache {
    private static final int MAX_MARKERS = 150;
    private static final double MAX_RANGE_BLOCKS = 128.0;

    public record MobSnapshot(double x, double y, double z, RadarCategory category, MobHeadIcons.Face face,
                              Entity source) {
        public MobSnapshot(double x, double y, double z, RadarCategory category) {
            this(x, y, z, category, null, null);
        }

        public MobSnapshot(double x, double y, double z, RadarCategory category, MobHeadIcons.Face face) {
            this(x, y, z, category, face, null);
        }

        public double xAt(float partialTick) {
            return source == null ? x : Mth.lerp(partialTick, source.xo, source.getX());
        }

        public double zAt(float partialTick) {
            return source == null ? z : Mth.lerp(partialTick, source.zo, source.getZ());
        }
    }

    private static List<MobSnapshot> snapshot = List.of();

    private MobMarkerCache() {
    }

    public static void tick(Minecraft minecraft) {
        MobHeadIcons.tick(minecraft);
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !VoxyMapSettings.minimapEnabled() || VoxyMapCameraController.isActive()) {
            snapshot = List.of();
            return;
        }

        Set<RadarCategory> shown = VoxyMapSettings.radarCategories();
        if (!shown.contains(RadarCategory.ITEMS) && !VoxyMapSettings.mobsEnabled()) {
            snapshot = List.of();
            return;
        }

        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();

        List<MobSnapshot> candidates = new ArrayList<>();
        Map<MobSnapshot, Mob> sources = new IdentityHashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            RadarCategory category = categoryOf(entity);
            if (category == null || !shown.contains(category) || entity.isInvisibleTo(minecraft.player)) {
                continue;
            }
            MobSnapshot candidate = new MobSnapshot(entity.getX(), entity.getY(), entity.getZ(), category, null, entity);
            candidates.add(candidate);
            if (entity instanceof Mob mob) {
                sources.put(candidate, mob);
            }
        }
        List<MobSnapshot> kept = filterAndCap(candidates, px, pz, MAX_RANGE_BLOCKS, MAX_MARKERS);
        snapshot = MinimapHeads.shown(minecraft) ? withFaces(minecraft, kept, sources) : kept;
    }

    private static List<MobSnapshot> withFaces(Minecraft minecraft, List<MobSnapshot> mobs, Map<MobSnapshot, Mob> sources) {
        List<MobSnapshot> result = new ArrayList<>(mobs.size());
        for (MobSnapshot mob : mobs) {
            Mob source = sources.get(mob);
            MobHeadIcons.Face face = source == null ? null : MobHeadIcons.faceOf(minecraft, source);
            result.add(new MobSnapshot(mob.x(), mob.y(), mob.z(), mob.category(), face, mob.source()));
        }
        return List.copyOf(result);
    }

    static RadarCategory categoryOf(Entity entity) {
        if (entity instanceof ItemEntity) {
            return RadarCategory.ITEMS;
        }
        if (!(entity instanceof Mob)) {
            return null;
        }
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            return RadarCategory.PASSIVE;
        }
        if (entity instanceof NeutralMob) {
            return RadarCategory.NEUTRAL;
        }
        if (entity instanceof Enemy) {
            return RadarCategory.HOSTILE;
        }
        return RadarCategory.PASSIVE;
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
