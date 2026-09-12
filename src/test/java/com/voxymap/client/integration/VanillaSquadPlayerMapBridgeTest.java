package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaSquadPlayerMapBridgeTest {
    @Test
    void parsesPlayersFromTheSquaremapResponseShape() {
        String body = """
                {"max":400,"players":[
                    {"world":"minecraft_overworld","name":"thost","x":31,"y":69,"z":179,"uuid":"2f4c3b65811535398569b527957f6721","yaw":113},
                    {"world":"minecraft_the_nether","name":"BOMGAID","x":27,"y":49,"z":-9,"uuid":"eb53e27c29c134b1811ae9d82a72c1d4","yaw":58}
                ]}
                """;

        List<VanillaSquadPlayerMapBridge.RemotePlayer> players = VanillaSquadPlayerMapBridge.parse(body);

        assertEquals(2, players.size());
        VanillaSquadPlayerMapBridge.RemotePlayer thost = players.get(0);
        assertEquals("thost", thost.name());
        assertEquals("minecraft_overworld", thost.world());
        assertEquals(31.0, thost.x());
        assertEquals(69.0, thost.y());
        assertEquals(179.0, thost.z());
        assertEquals("2f4c3b65811535398569b527957f6721", thost.uuid());
    }

    @Test
    void skipsEntriesMissingRequiredFieldsInsteadOfThrowing() {
        String body = "{\"players\":[{\"world\":\"minecraft_overworld\",\"name\":\"noPosition\"}]}";

        List<VanillaSquadPlayerMapBridge.RemotePlayer> players = VanillaSquadPlayerMapBridge.parse(body);

        assertTrue(players.isEmpty());
    }

    @Test
    void handlesAMissingPlayersArrayGracefully() {
        assertTrue(VanillaSquadPlayerMapBridge.parse("{\"max\":400}").isEmpty());
    }

    @Test
    void malformedEntryDoesNotDiscardValidPlayers() {
        String body = """
                {"players":[
                    {"world":"minecraft_overworld","name":null,"x":0,"y":64,"z":0},
                    {"world":"minecraft_overworld","name":"valid","x":1,"y":65,"z":2}
                ]}
                """;

        List<VanillaSquadPlayerMapBridge.RemotePlayer> players = VanillaSquadPlayerMapBridge.parse(body);

        assertEquals(1, players.size());
        assertEquals("valid", players.get(0).name());
    }

    @Test
    void normalizeUuidStripsDashesAndLowercasesSoItMatchesMinecraftsUuidFormat() {
        String mojangStyle = "2F4C3B65-8115-3539-8569-B527957F6721";
        String squaremapStyle = "2f4c3b65811535398569b527957f6721";

        assertEquals(VanillaSquadPlayerMapBridge.normalizeUuid(squaremapStyle),
                VanillaSquadPlayerMapBridge.normalizeUuid(mojangStyle));
        assertEquals(null, VanillaSquadPlayerMapBridge.normalizeUuid("not-a-uuid"));
    }
}
