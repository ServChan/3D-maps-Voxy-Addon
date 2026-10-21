package com.voxymap.client.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAreaTest {
    @Test
    void shortRoutesUseFullBlockCells() {
        NavArea area = NavArea.around(0, 0, 200, 50);
        assertNotNull(area);
        assertEquals(0, area.level());
        assertTrue(area.originX() <= -96 && area.originZ() <= -96);
        assertTrue(area.originX() + area.width() * area.cell() >= 296);
    }

    @Test
    void longRoutesUseCoarserCellsWithinTheBudget() {
        NavArea medium = NavArea.around(0, 0, 1000, 0);
        NavArea far = NavArea.around(0, 0, 6000, 6000);
        assertNotNull(medium);
        assertNotNull(far);
        assertEquals(1, medium.level());
        assertTrue(far.level() >= 2);
        assertTrue(far.cells() <= NavArea.MAX_CELLS);
        assertEquals(0, Math.floorMod(far.originX(), far.cell()));
    }

    @Test
    void routesBeyondTheCoarsestGridAreRefused() {
        assertNull(NavArea.around(0, 0, 60000, 60000));
    }
}
