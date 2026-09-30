package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitematicaPlusBridgeTest {
    private static Object[] row() {
        return new Object[]{"4a1c", "Castle", 10, 64, -30, 42, 96, 2, 0.25, true, true};
    }

    @Test
    void parsesAWellFormedRow() {
        LitematicaPlusBridge.Placement placement = LitematicaPlusBridge.parse(row());
        assertNotNull(placement);
        assertEquals("Castle", placement.name());
        assertEquals(26.0, placement.centerX());
        assertEquals(-14.0, placement.centerZ());
        assertEquals(0.25, placement.progress());
        assertTrue(placement.selected());
    }

    @Test
    void rejectsRowsOfTheWrongShape() {
        assertNull(LitematicaPlusBridge.parse(null));
        assertNull(LitematicaPlusBridge.parse("text"));
        assertNull(LitematicaPlusBridge.parse(new Object[]{"id", "name"}));
        Object[] wrongType = row();
        wrongType[4] = 1.5;
        assertNull(LitematicaPlusBridge.parse(wrongType));
        Object[] inverted = row();
        inverted[2] = 100;
        assertNull(LitematicaPlusBridge.parse(inverted));
    }

    @Test
    void shortensVeryLongNames() {
        Object[] longName = row();
        longName[1] = "x".repeat(500);
        assertEquals(96, LitematicaPlusBridge.parse(longName).name().length());
    }
}
