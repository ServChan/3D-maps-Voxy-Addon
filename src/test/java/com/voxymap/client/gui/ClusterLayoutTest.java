package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterLayoutTest {
    @Test
    void keepsTheBoxInPlaceWhenItAlreadyFitsInsideTheViewport() {
        assertEquals(100, ClusterLayout.clampOrigin(100, 40, 800, 3));
    }

    @Test
    void pushesTheBoxAwayFromTheLeadingEdge() {
        assertEquals(3, ClusterLayout.clampOrigin(-20, 40, 800, 3));
    }

    @Test
    void pushesTheBoxAwayFromTheTrailingEdge() {
        assertEquals(757, ClusterLayout.clampOrigin(790, 40, 800, 3));
    }

    @Test
    void aBoxWiderThanTheViewportStaysPinnedToTheMargin() {
        // screenSize - size - margin < margin: the trailing bound would be before the leading one,
        // so the leading margin must win rather than producing a negative-width visible clamp.
        assertEquals(3, ClusterLayout.clampOrigin(50, 900, 800, 3));
    }
}
