package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapGotoTest {
    @Test
    void twoOrThreeNumbersWithAnySeparator() {
        assertEquals(new MapGoto.Target(120, null, -340), MapGoto.parse(" 120 -340 "));
        assertEquals(new MapGoto.Target(120, 64, -340), MapGoto.parse("120, 64; -340"));
    }

    @Test
    void anythingElseIsRejected() {
        assertNull(MapGoto.parse("120"));
        assertNull(MapGoto.parse("1 2 3 4"));
        assertNull(MapGoto.parse("north 5"));
        assertTrue(MapGoto.allowedCharacter('-'));
        assertFalse(MapGoto.allowedCharacter('x'));
    }
}
