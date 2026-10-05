package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaypointLabelsTest {
    private static WaypointLabels.Projected at(String name, float x, float y) {
        return new WaypointLabels.Projected(
                new WaypointManager.Waypoint(name, name, 0, 0, 0, 0xFFFFFFFF, false), x, y, 10.0);
    }

    @Test
    void theWaypointClosestToTheCrosshairGetsTheLabel() {
        List<WaypointLabels.Projected> points = List.of(at("far", 140, 100), at("near", 104, 98), at("mid", 120, 100));
        assertEquals("near", WaypointLabels.aimedAt(points, 100, 100, 48).waypoint().name());
    }

    @Test
    void nothingIsAimedAtOutsideTheRadius() {
        assertNull(WaypointLabels.aimedAt(List.of(at("away", 300, 300)), 100, 100, 48));
    }

    @Test
    void waypointsFadeOutOverTheLastPartOfTheRange() {
        assertEquals(1.0f, WaypointLabels.fade(100_000.0, 0));
        assertEquals(1.0f, WaypointLabels.fade(800.0, 1000));
        assertEquals(0.5f, WaypointLabels.fade(900.0, 1000), 1e-6f);
        assertEquals(0.0f, WaypointLabels.fade(1000.0, 1000));
        assertEquals(0.0f, WaypointLabels.fade(5000.0, 1000));
    }

    @Test
    void overlappingLabelsAreLiftedOrDropped() {
        WaypointLabels.Box first = new WaypointLabels.Box(0, 100, 50, 122);
        assertEquals(0, WaypointLabels.liftToFit(List.of(), first, 24, 3));
        assertEquals(24, WaypointLabels.liftToFit(List.of(first), new WaypointLabels.Box(10, 100, 60, 122), 24, 3));
        List<WaypointLabels.Box> stack = List.of(first, first.lifted(24), first.lifted(48), first.lifted(72));
        assertNull(WaypointLabels.liftToFit(stack, first, 24, 3));
        assertEquals(0, WaypointLabels.liftToFit(List.of(first), new WaypointLabels.Box(60, 100, 90, 122), 24, 3));
    }
}
