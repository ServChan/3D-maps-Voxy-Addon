package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobMarkerCacheTest {
    @Test
    void mobsBeyondRangeAreExcluded() {
        List<MobMarkerCache.MobSnapshot> candidates = List.of(
                new MobMarkerCache.MobSnapshot(10, 64, 0, RadarCategory.PASSIVE),
                new MobMarkerCache.MobSnapshot(200, 64, 0, RadarCategory.PASSIVE));

        List<MobMarkerCache.MobSnapshot> result = MobMarkerCache.filterAndCap(candidates, 0, 0, 128.0, 150);

        assertEquals(1, result.size());
        assertEquals(10, result.get(0).x());
    }

    @Test
    void mobExactlyAtRangeIsIncluded() {
        List<MobMarkerCache.MobSnapshot> candidates = List.of(
                new MobMarkerCache.MobSnapshot(128, 64, 0, RadarCategory.PASSIVE));

        List<MobMarkerCache.MobSnapshot> result = MobMarkerCache.filterAndCap(candidates, 0, 0, 128.0, 150);

        assertEquals(1, result.size());
    }

    @Test
    void overflowKeepsOnlyTheNearestUpToTheCap() {
        List<MobMarkerCache.MobSnapshot> candidates = List.of(
                new MobMarkerCache.MobSnapshot(30, 64, 0, RadarCategory.PASSIVE),
                new MobMarkerCache.MobSnapshot(10, 64, 0, RadarCategory.PASSIVE),
                new MobMarkerCache.MobSnapshot(20, 64, 0, RadarCategory.PASSIVE));

        List<MobMarkerCache.MobSnapshot> result = MobMarkerCache.filterAndCap(candidates, 0, 0, 128.0, 2);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(m -> m.x() == 10));
        assertTrue(result.stream().anyMatch(m -> m.x() == 20));
    }

    @Test
    void atOrBelowTheCapKeepsEveryCandidateUnsorted() {
        List<MobMarkerCache.MobSnapshot> candidates = List.of(
                new MobMarkerCache.MobSnapshot(30, 64, 0, RadarCategory.HOSTILE),
                new MobMarkerCache.MobSnapshot(10, 64, 0, RadarCategory.PASSIVE));

        List<MobMarkerCache.MobSnapshot> result = MobMarkerCache.filterAndCap(candidates, 0, 0, 128.0, 150);

        assertEquals(2, result.size());
    }
}
