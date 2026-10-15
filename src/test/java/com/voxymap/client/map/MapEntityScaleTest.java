package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapEntityScaleTest {
    @Test
    void closeModelsNeedNoGrowth() {
        assertTrue(MapEntityScale.requiredFactor(1.8, 0.0, 10.0, 0.0, 70.0, 480, 14.0) < 1.0);
    }

    @Test
    void requiredGrowthReachesTheMinimumScreenHeight() {
        double distance = 2000.0;
        double factor = MapEntityScale.requiredFactor(1.8, 0.0, distance, 0.0, 30.0, 480, 14.0);
        double visible = 2.0 * distance * Math.tan(Math.toRadians(30.0) * 0.5);
        assertEquals(14.0, 1.8 * factor * 480 / visible, 1.0e-6);
    }

    @Test
    void modelsThatWouldNeedHugeGrowthAreLeftOffTheMap() {
        double zoomedIn = MapEntityScale.requiredFactor(0.7, 0.0, 300.0, 0.0, 40.0, 480, 8.0);
        double zoomedOut = MapEntityScale.requiredFactor(0.7, 0.0, 8000.0, 0.0, 30.0, 480, 8.0);
        assertTrue(MapEntityScale.fitsOnMap(zoomedIn));
        assertFalse(MapEntityScale.fitsOnMap(zoomedOut));
    }
}
