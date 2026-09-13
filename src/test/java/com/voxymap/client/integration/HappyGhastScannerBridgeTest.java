package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HappyGhastScannerBridgeTest {
    @Test
    void acceptsFiniteBoundedRoutes() {
        HappyGhastScannerBridge.Route route = HappyGhastScannerBridge.validatedRoute(10.0, -20.0, 64.0, 7);

        assertEquals(new HappyGhastScannerBridge.Route(10.0, -20.0, 64.0, 7), route);
        assertEquals(2, route.currentLap());
    }

    @Test
    void rejectsInvalidOrUnboundedRouteGeometry() {
        assertNull(HappyGhastScannerBridge.validatedRoute(Double.NaN, 0.0, 64.0, 0));
        assertNull(HappyGhastScannerBridge.validatedRoute(0.0, 0.0, 0.0, 0));
        assertNull(HappyGhastScannerBridge.validatedRoute(0.0, 0.0, 64.0, -1));
        assertNull(HappyGhastScannerBridge.validatedRoute(60_000_001.0, 0.0, 64.0, 0));
    }
}
