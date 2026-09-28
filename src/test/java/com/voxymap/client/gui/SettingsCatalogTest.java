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
    private static Set<SettingsCatalog.Entry> entries(boolean inWorld, boolean inMap, boolean onVanillaSquad) {
        List<SettingsCatalog.Entry> flat = new ArrayList<>();
        SettingsCatalog.sections(inWorld, inMap, onVanillaSquad).forEach(section -> flat.addAll(section.entries()));
        assertEquals(Set.copyOf(flat).size(), flat.size(), "a setting appears in two places");
        return flat.isEmpty() ? EnumSet.noneOf(SettingsCatalog.Entry.class) : EnumSet.copyOf(flat);
    }

    @Test
    void mapPanelShowsEverySettingExactlyOnceOnVanillaSquad() {
        assertEquals(EnumSet.allOf(SettingsCatalog.Entry.class), entries(true, true, true));
    }

    @Test
    void groupsAreInDeclarationOrderAndEntriesStayInsideTheirGroup() {
        List<SettingsCatalog.Section> sections = SettingsCatalog.sections(true, true, true);

        List<SettingsCatalog.Group> groups = sections.stream().map(SettingsCatalog.Section::group).toList();
        assertEquals(List.of(SettingsCatalog.Group.values()), groups);
        for (SettingsCatalog.Section section : sections) {
            assertFalse(section.entries().isEmpty());
            section.entries().forEach(entry -> assertEquals(section.group(), entry.group()));
        }
    }

    @Test
    void modMenuOutsideAWorldListsOnlyGlobalSettings() {
        Set<SettingsCatalog.Entry> shown = entries(false, false, false);

        for (SettingsCatalog.Entry entry : SettingsCatalog.Entry.values()) {
            assertEquals(entry.availability() == SettingsCatalog.Availability.EVERYWHERE && !entry.vanillaSquadOnly(),
                    shown.contains(entry), entry.name());
        }
        assertFalse(SettingsCatalog.sections(false, false, false).stream()
                        .anyMatch(section -> section.group() == SettingsCatalog.Group.SYNC),
                "an empty group must not be shown");
    }

    @Test
    void modMenuOpenedFromAWorldAddsTheSyncSettingsButNotTheMapOnlyActions() {
        Set<SettingsCatalog.Entry> shown = entries(true, false, false);

        assertTrue(shown.containsAll(EnumSet.of(SettingsCatalog.Entry.SYNC_ADDRESS,
                SettingsCatalog.Entry.SYNC_PASSWORD, SettingsCatalog.Entry.SYNC_STATUS)));
        assertFalse(shown.contains(SettingsCatalog.Entry.MAP_SHARE), "sharing needs the map's area selection");
        assertFalse(shown.contains(SettingsCatalog.Entry.MAP_RECEIVE), "receiving needs the map's code prompt");
    }

    @Test
    void vanillaSquadSettingsAreOnlyListedOnThatServer() {
        Set<SettingsCatalog.Entry> squad = EnumSet.of(SettingsCatalog.Entry.VANILLASQUAD_PLAYERS,
                SettingsCatalog.Entry.VANILLASQUAD_FLAGS, SettingsCatalog.Entry.VANILLASQUAD_MAP);
        for (SettingsCatalog.Entry entry : SettingsCatalog.Entry.values()) {
            assertEquals(squad.contains(entry), entry.vanillaSquadOnly(), entry.name());
        }

        for (boolean inMap : new boolean[] {true, false}) {
            Set<SettingsCatalog.Entry> elsewhere = entries(true, inMap, false);
            for (SettingsCatalog.Entry entry : squad) {
                assertFalse(elsewhere.contains(entry), entry + " must be hidden off VanillaSquad");
            }
            assertTrue(elsewhere.contains(SettingsCatalog.Entry.WAYPOINTS), "other layers stay");
        }

        assertTrue(entries(true, true, true).containsAll(squad));
        Set<SettingsCatalog.Entry> menuOutsideWorld = entries(false, false, true);
        assertTrue(menuOutsideWorld.contains(SettingsCatalog.Entry.VANILLASQUAD_PLAYERS));
        assertFalse(menuOutsideWorld.contains(SettingsCatalog.Entry.VANILLASQUAD_MAP),
                "the per-world map choice still needs a world");
    }

    @Test
    void theBuildWithoutPlayerMarkersHidesOnlyThePlayerMarkerRow() {
        for (boolean inWorld : new boolean[] {true, false}) {
            for (boolean inMap : new boolean[] {true, false}) {
                Set<SettingsCatalog.Entry> withMarkers = EnumSet.noneOf(SettingsCatalog.Entry.class);
                Set<SettingsCatalog.Entry> withoutMarkers = EnumSet.noneOf(SettingsCatalog.Entry.class);
                SettingsCatalog.sections(inWorld, inMap, true, true).forEach(section -> withMarkers.addAll(section.entries()));
                SettingsCatalog.sections(inWorld, inMap, true, false).forEach(section -> withoutMarkers.addAll(section.entries()));

                assertFalse(withoutMarkers.contains(SettingsCatalog.Entry.VANILLASQUAD_PLAYERS));
                withMarkers.remove(SettingsCatalog.Entry.VANILLASQUAD_PLAYERS);
                assertEquals(withMarkers, withoutMarkers);
            }
        }
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
