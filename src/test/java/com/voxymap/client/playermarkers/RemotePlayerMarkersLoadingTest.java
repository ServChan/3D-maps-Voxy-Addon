package com.voxymap.client.playermarkers;

import com.voxymap.client.integration.RemotePlayerMarkers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePlayerMarkersLoadingTest {
    @Test
    void theFacadeFindsTheBridgeWhenTheModuleIsPresent() {
        assertTrue(RemotePlayerMarkers.available());
        assertEquals(VanillaSquadPlayerMapBridge.class.getName(), "com.voxymap.client.playermarkers.VanillaSquadPlayerMapBridge");
    }
}
