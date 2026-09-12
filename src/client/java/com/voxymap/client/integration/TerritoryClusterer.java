package com.voxymap.client.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups {@link VanillaSquadFlagMapBridge.Flag} points sharing a name (and colour) into a single
 * territory outline, instead of rendering every individual banner marker. A named group with 1200+
 * members otherwise means 1200+ per-frame marker draws and text-width measurements — the actual cause
 * of the severe frame drop when the flags layer was first shown one marker at a time.
 */
final class TerritoryClusterer {
    private static final int MIN_POINTS_FOR_OUTLINE = 3;

    private TerritoryClusterer() {
    }

    static List<VanillaSquadFlagMapBridge.Territory> cluster(List<VanillaSquadFlagMapBridge.Flag> flags) {
        Map<GroupKey, List<VanillaSquadFlagMapBridge.Flag>> groups = new LinkedHashMap<>();
        for (VanillaSquadFlagMapBridge.Flag flag : flags) {
            if (flag.tooltip().isEmpty()) {
                // An unnamed banner isn't a "territory" and there can be hundreds of them; only
                // named clusters are worth an outline.
                continue;
            }
            groups.computeIfAbsent(new GroupKey(flag.tooltip(), flag.icon()), ignored -> new ArrayList<>()).add(flag);
        }

        List<VanillaSquadFlagMapBridge.Territory> territories = new ArrayList<>(groups.size());
        for (List<VanillaSquadFlagMapBridge.Flag> members : groups.values()) {
            VanillaSquadFlagMapBridge.Flag first = members.get(0);
            List<double[]> points = new ArrayList<>(members.size());
            double sumX = 0;
            double sumZ = 0;
            for (VanillaSquadFlagMapBridge.Flag member : members) {
                points.add(new double[]{member.x(), member.z()});
                sumX += member.x();
                sumZ += member.z();
            }
            List<double[]> outline = points.size() >= MIN_POINTS_FOR_OUTLINE ? ConvexHull.compute(points) : points;
            territories.add(new VanillaSquadFlagMapBridge.Territory(first.tooltip(), VanillaSquadFlagMapBridge.colorFor(first.icon()),
                    outline, sumX / members.size(), sumZ / members.size()));
        }
        return List.copyOf(territories);
    }

    private record GroupKey(String name, String icon) {
    }
}
