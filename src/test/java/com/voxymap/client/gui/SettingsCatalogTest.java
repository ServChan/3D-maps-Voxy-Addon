package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsCatalogTest {
    private static List<SettingsCatalog.Entry> flatten(List<SettingsCatalog.Section> sections) {
        List<SettingsCatalog.Entry> entries = new ArrayList<>();
        sections.forEach(section -> entries.addAll(section.entries()));
        return entries;
    }

    @Test
    void mapPanelShowsEverySettingExactlyOnce() {
        List<SettingsCatalog.Entry> entries = flatten(SettingsCatalog.sections(true));

        assertEquals(SettingsCatalog.Entry.values().length, entries.size());
        assertEquals(Set.copyOf(entries).size(), entries.size(), "a setting appears in two places");
    }

    @Test
    void groupsAreInDeclarationOrderAndEntriesStayInsideTheirGroup() {
        List<SettingsCatalog.Section> sections = SettingsCatalog.sections(true);

        List<SettingsCatalog.Group> groups = sections.stream().map(SettingsCatalog.Section::group).toList();
        assertEquals(List.of(SettingsCatalog.Group.values()), groups);
        for (SettingsCatalog.Section section : sections) {
            assertFalse(section.entries().isEmpty());
            section.entries().forEach(entry -> assertEquals(section.group(), entry.group()));
        }
    }

    @Test
    void outsideAWorldOnlyGlobalSettingsRemain() {
        List<SettingsCatalog.Section> sections = SettingsCatalog.sections(false);
        Set<SettingsCatalog.Entry> entries = EnumSet.copyOf(flatten(sections));

        for (SettingsCatalog.Entry entry : SettingsCatalog.Entry.values()) {
            assertEquals(!entry.needsWorld(), entries.contains(entry), entry.name());
        }
        assertFalse(sections.stream().anyMatch(section -> section.group() == SettingsCatalog.Group.SYNC),
                "an empty group must not be shown");
    }

    @Test
    void everySettingHasDistinctTranslationKeys() {
        Set<String> keys = new HashSet<>();
        for (SettingsCatalog.Group group : SettingsCatalog.Group.values()) {
            assertTrue(keys.add(group.titleKey()), group.name());
        }
        for (SettingsCatalog.Entry entry : SettingsCatalog.Entry.values()) {
            assertTrue(keys.add(entry.labelKey()), entry.name());
        }
    }

    @Test
    void removedFeaturesAreNotListed() {
        Set<String> names = new HashSet<>();
        for (SettingsCatalog.Entry entry : SettingsCatalog.Entry.values()) {
            names.add(entry.name());
        }
        assertFalse(names.contains("CUTAWAY"));
        assertFalse(names.contains("CUTAWAY_STATUS"));
        assertFalse(names.contains("GHAST_ROUTE"));
    }
}
