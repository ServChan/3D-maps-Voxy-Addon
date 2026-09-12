package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpiralRouteGeometryTest {
    @Test
    void firstFourSegmentsTraceTheFirstLapAroundTheCenter() {
        SpiralRouteGeometry.Waypoint east = SpiralRouteGeometry.waypointAt(0, 0, 10, 0);
        SpiralRouteGeometry.Waypoint south = SpiralRouteGeometry.waypointAt(0, 0, 10, 1);
        SpiralRouteGeometry.Waypoint west = SpiralRouteGeometry.waypointAt(0, 0, 10, 2);
        SpiralRouteGeometry.Waypoint north = SpiralRouteGeometry.waypointAt(0, 0, 10, 3);

        assertEquals(10.0, east.x());
        assertEquals(0.0, east.z());
        assertEquals(10.0, south.x());
        assertEquals(10.0, south.z());
        assertEquals(-10.0, west.x());
        assertEquals(10.0, west.z());
        assertEquals(-10.0, north.x());
        assertEquals(-10.0, north.z());
        assertEquals(1, east.lap());
        assertEquals(1, north.lap());
    }

    @Test
    void lapIncrementsEveryFourSegments() {
        assertEquals(1, SpiralRouteGeometry.waypointAt(0, 0, 10, 0).lap());
        assertEquals(1, SpiralRouteGeometry.waypointAt(0, 0, 10, 3).lap());
        assertEquals(2, SpiralRouteGeometry.waypointAt(0, 0, 10, 4).lap());
        assertEquals(2, SpiralRouteGeometry.waypointAt(0, 0, 10, 7).lap());
        assertEquals(3, SpiralRouteGeometry.waypointAt(0, 0, 10, 8).lap());
    }

    @Test
    void secondLapConnectsDirectlyFromWhereTheFirstLapEnded() {
        SpiralRouteGeometry.Waypoint lastOfLapOne = SpiralRouteGeometry.waypointAt(0, 0, 10, 3);
        SpiralRouteGeometry.Waypoint firstOfLapTwo = SpiralRouteGeometry.waypointAt(0, 0, 10, 4);

        // The spiral is one continuous outward path: lap two's first leg starts on lap one's north
        // side (same Z) and extends the X range outward - it never jumps or leaves a gap.
        assertEquals(lastOfLapOne.z(), firstOfLapTwo.z());
    }

    @Test
    void pathSegmentStartsFromTheCenterWhenTheWindowBeginsAtSegmentZero() {
        List<SpiralRouteGeometry.Waypoint> path = SpiralRouteGeometry.pathSegment(5, -5, 10, 0, 3);

        assertEquals(5, path.size());
        assertEquals(5.0, path.get(0).x());
        assertEquals(-5.0, path.get(0).z());
    }

    @Test
    void pathSegmentPrefixesWithThePriorWaypointWhenTheWindowStartsMidSpiral() {
        List<SpiralRouteGeometry.Waypoint> path = SpiralRouteGeometry.pathSegment(0, 0, 10, 5, 6);

        SpiralRouteGeometry.Waypoint expectedPrefix = SpiralRouteGeometry.waypointAt(0, 0, 10, 4);
        assertEquals(3, path.size());
        assertEquals(expectedPrefix.x(), path.get(0).x());
        assertEquals(expectedPrefix.z(), path.get(0).z());
    }
}
