package com.voxymap.client.gui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiThemeTest {
    private static final int OPAQUE_BACKDROP = 0xFF05080F;

    private static int over(int argb) {
        return UiTheme.mix(OPAQUE_BACKDROP, argb | 0xFF000000, ((argb >>> 24) & 0xFF) / 255.0f) | 0xFF000000;
    }

    @Test
    void readableTextMeetsAaOnEverySurface() {
        int[] surfaces = {UiTheme.SURFACE, UiTheme.SURFACE_RAISED, UiTheme.SURFACE_SUNKEN, UiTheme.CONTROL,
                UiTheme.CONTROL_HOVER, UiTheme.CONTROL_SELECTED};
        for (int surface : surfaces) {
            int background = over(surface);
            assertTrue(UiTheme.contrast(UiTheme.TEXT, background) >= 4.5, Integer.toHexString(surface));
            assertTrue(UiTheme.contrast(UiTheme.TEXT_SECONDARY, background) >= 4.5, Integer.toHexString(surface));
        }
        for (int surface : new int[]{UiTheme.SURFACE, UiTheme.SURFACE_RAISED, UiTheme.SURFACE_SUNKEN, UiTheme.CONTROL}) {
            assertTrue(UiTheme.contrast(UiTheme.TEXT_MUTED, over(surface)) >= 4.5, Integer.toHexString(surface));
        }
    }

    @Test
    void controlsAndFocusRingMeetTheNonTextMinimum() {
        int background = over(UiTheme.SURFACE);
        assertTrue(UiTheme.contrast(UiTheme.BORDER_STRONG, background) >= 3.0);
        assertTrue(UiTheme.contrast(UiTheme.FOCUS, background) >= 3.0);
        assertTrue(UiTheme.contrast(UiTheme.ACCENT_BRIGHT, background) >= 3.0);
        assertTrue(UiTheme.contrast(UiTheme.WARNING, background) >= 3.0);
        assertTrue(UiTheme.contrast(UiTheme.DANGER, background) >= 3.0);
        assertTrue(UiTheme.contrast(UiTheme.SUCCESS, background) >= 3.0);
    }

    @Test
    void textOnFilledButtonsIsReadable() {
        assertTrue(UiTheme.contrast(UiTheme.TEXT, over(0xFF2B6FB8)) >= 4.5);
        assertTrue(UiTheme.contrast(UiTheme.TEXT, over(UiTheme.DANGER_FILL)) >= 4.5);
    }

    @Test
    void mixAndAlphaHelpersStayInRange() {
        assertEquals(0xFF000000, UiTheme.mix(0xFF000000, 0xFFFFFFFF, 0.0f));
        assertEquals(0xFFFFFFFF, UiTheme.mix(0xFF000000, 0xFFFFFFFF, 1.0f));
        assertEquals(0x80123456, UiTheme.withAlpha(0xFF123456, 0x80));
        assertEquals(0x00123456, UiTheme.scaleAlpha(0xFF123456, -1.0f));
    }
}
