package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeleportCommandTest {
    @Test
    void defaultTemplateIsTppos() {
        assertEquals("tppos 10 64 -20", TeleportCommand.format(TeleportCommand.DEFAULT_SERVER_TEMPLATE, 10, 64, -20));
    }

    @Test
    void acceptsAngleBracketsAndLeadingSlash() {
        assertEquals("tppos 1 2 3", TeleportCommand.format("/tppos <x> <y> <z>", 1, 2, 3));
        assertEquals("tp @s 1 2 3", TeleportCommand.format("  //tp @s {x} {y} {z}  ", 1, 2, 3));
    }

    @Test
    void blankTemplateDisablesTeleport() {
        assertNull(TeleportCommand.format("", 1, 2, 3));
        assertNull(TeleportCommand.format(" / ", 1, 2, 3));
        assertNull(TeleportCommand.format(null, 1, 2, 3));
    }

    @Test
    void normalizeDropsControlCharactersAndCapsLength() {
        assertEquals("tppos {x} {y} {z}", TeleportCommand.normalize("tppos\n{x}\t{y} {z}").replaceAll(" +", " "));
        assertEquals(TeleportCommand.MAX_LENGTH, TeleportCommand.normalize("a".repeat(500)).length());
    }
}
