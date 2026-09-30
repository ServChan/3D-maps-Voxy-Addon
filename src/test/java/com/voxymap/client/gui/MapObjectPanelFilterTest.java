package com.voxymap.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapObjectPanelFilterTest {
    @Test
    void schematicsFilterIsSkippedWithoutLitematicaPlus() {
        assertEquals(MapObjectPanel.Filter.ALL, MapObjectPanel.Filter.TERRITORIES.next(false));
        assertEquals(MapObjectPanel.Filter.SCHEMATICS, MapObjectPanel.Filter.TERRITORIES.next(true));
        assertEquals(MapObjectPanel.Filter.ALL, MapObjectPanel.Filter.SCHEMATICS.next(true));
    }
}
