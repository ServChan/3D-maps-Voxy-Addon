package com.voxymap.syncserver;

import com.voxymap.shared.WaypointDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointStoreTest {
    private static final String WORLD_A = "0123456789abcdef0123456789abcdef";
    private static final String WORLD_B = "abcdefabcdefabcdefabcdefabcdefab";

    @Test
    void putThenListReturnsTheSavedWaypoint(@TempDir Path dir) throws IOException {
        WaypointStore store = new WaypointStore(dir);
        WaypointDto waypoint = new WaypointDto("id-1", "Base", 1.0, 2.0, 3.0, 0xFF62E7FF);

        store.put(WORLD_A, waypoint);

        assertEquals(List.of(waypoint), store.list(WORLD_A));
        assertTrue(store.list(WORLD_B).isEmpty());
    }

    @Test
    void puttingTheSameIdTwiceReplacesRatherThanDuplicates(@TempDir Path dir) throws IOException {
        WaypointStore store = new WaypointStore(dir);
        store.put(WORLD_A, new WaypointDto("id-1", "Base", 1.0, 2.0, 3.0, 0xFF62E7FF));
        store.put(WORLD_A, new WaypointDto("id-1", "Renamed base", 1.0, 2.0, 3.0, 0xFF62E7FF));

        List<WaypointDto> list = store.list(WORLD_A);
        assertEquals(1, list.size());
        assertEquals("Renamed base", list.get(0).name());
    }

    @Test
    void deleteRemovesOnlyTheMatchingWaypoint(@TempDir Path dir) throws IOException {
        WaypointStore store = new WaypointStore(dir);
        store.put(WORLD_A, new WaypointDto("id-1", "Keep", 0, 0, 0, 0xFF62E7FF));
        store.put(WORLD_A, new WaypointDto("id-2", "Remove", 0, 0, 0, 0xFF62E7FF));

        assertTrue(store.delete(WORLD_A, "id-2"));
        assertFalse(store.delete(WORLD_A, "id-2"));
        assertEquals(List.of("Keep"), store.list(WORLD_A).stream().map(WaypointDto::name).toList());
    }

    @Test
    void dataSurvivesReopeningTheStoreFromTheSameDirectory(@TempDir Path dir) throws IOException {
        WaypointDto waypoint = new WaypointDto("id-1", "Дом", 100.5, 64.0, -200.25, 0xFFFFC94D);
        new WaypointStore(dir).put(WORLD_A, waypoint);

        WaypointStore reopened = new WaypointStore(dir);
        assertEquals(List.of(waypoint), reopened.list(WORLD_A));
    }

    @Test
    void invalidWorldIdIsRejected() {
        assertFalse(WaypointStore.isValidWorldId("not-a-world-id"));
        assertFalse(WaypointStore.isValidWorldId(null));
        assertTrue(WaypointStore.isValidWorldId(WORLD_A));
    }
}
