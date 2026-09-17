package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPanelLayoutTest {
    @Test
    void tallPanelBecomesScrollableAndClampsItsOffset() {
        SettingsPanelLayout.Result layout = SettingsPanelLayout.calculate(520, 300, 8, 6, 999);
        assertEquals(286, layout.panelHeight());
        assertEquals(234, layout.maxScroll());
        assertEquals(234, layout.scroll());
    }

    @Test
    void rowsMustBeCompletelyInsideTheContentViewport() {
        assertFalse(SettingsPanelLayout.rowVisible(120, 100, 200, 24));
        assertTrue(SettingsPanelLayout.rowVisible(128, 100, 200, 24));
        assertFalse(SettingsPanelLayout.rowVisible(273, 100, 200, 24));
    }
}
