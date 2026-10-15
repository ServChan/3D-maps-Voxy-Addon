package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsModelTest {
    private static final Set<SettingsCatalog.Entry> WITHOUT_DEFAULT = EnumSet.of(
            SettingsCatalog.Entry.VANILLASQUAD_MAP, SettingsCatalog.Entry.MINIMAP_MOVE,
            SettingsCatalog.Entry.SYNC_ADDRESS, SettingsCatalog.Entry.SYNC_PASSWORD, SettingsCatalog.Entry.SYNC_STATUS,
            SettingsCatalog.Entry.MAP_SHARE, SettingsCatalog.Entry.MAP_RECEIVE, SettingsCatalog.Entry.DIAGNOSTICS);

    @Test
    void everyPlainSettingIsResettableExactlyOnce() {
        Set<String> seen = new HashSet<>();
        for (SettingsCatalog.Entry entry : SettingsCatalog.Entry.values()) {
            boolean flag = entry.infoLine() != null || entry.radarCategory() != null || entry.mapOverlay() != null;
            String key = SettingsModel.propertyKey(entry);
            if (WITHOUT_DEFAULT.contains(entry) || flag) {
                assertNull(key, entry.name());
                continue;
            }
            assertNotNull(key, entry.name());
            assertTrue(seen.add(key), "duplicate property " + key);
        }
    }

    @Test
    void stepBackwardsCyclesForwardAllButOnce() {
        int[] calls = {0};
        SettingsModel.repeat(() -> calls[0]++, 4, 1);
        assertEquals(1, calls[0]);
        calls[0] = 0;
        SettingsModel.repeat(() -> calls[0]++, 4, -1);
        assertEquals(3, calls[0]);
    }
}
