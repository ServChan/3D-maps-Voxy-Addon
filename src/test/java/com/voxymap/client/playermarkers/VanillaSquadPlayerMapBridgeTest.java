package com.voxymap.client.playermarkers;

import com.voxymap.client.integration.RemotePlayerMarkers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

        List<RemotePlayerMarkers.RemotePlayer> players = VanillaSquadPlayerMapBridge.parse(body);

        assertEquals(2, players.size());
        RemotePlayerMarkers.RemotePlayer thost = players.get(0);
        assertEquals("thost", thost.name());
        assertEquals("minecraft_overworld", thost.world());
        assertEquals(31.0, thost.x());
        assertEquals(69.0, thost.y());
        assertEquals(179.0, thost.z());
        assertEquals("2f4c3b65811535398569b527957f6721", thost.uuid());
    }

    @Test
    void selectsTheMapWhosePlayerSnapshotMatchesTheClientsCurrentPosition() {
        String uuid = "2f4c3b65811535398569b527957f6721";
        Map<String, List<RemotePlayerMarkers.RemotePlayer>> snapshots = Map.of(
                "build", List.of(new RemotePlayerMarkers.RemotePlayer(
                        "thost", "minecraft_overworld", 5000, 64, 5000, uuid)),
                "farm", List.of(new RemotePlayerMarkers.RemotePlayer(
                        "thost", "minecraft_overworld", 31, 69, 179, uuid)));

        assertEquals("farm", VanillaSquadPlayerMapBridge.selectMapId(
                snapshots, "minecraft:overworld", uuid, "thost", 30, 180));
    }

    @Test
    void refusesStaleOrAmbiguousCrossWorldPlayerSnapshots() {
        String uuid = "2f4c3b65811535398569b527957f6721";
        RemotePlayerMarkers.RemotePlayer stale = new RemotePlayerMarkers.RemotePlayer(
                "thost", "minecraft_overworld", 5000, 64, 5000, uuid);
        assertTrue(VanillaSquadPlayerMapBridge.selectMapId(
                Map.of("build", List.of(stale)), "minecraft:overworld", uuid, "thost", 0, 0) == null);

        Map<String, List<RemotePlayerMarkers.RemotePlayer>> ambiguous = Map.of(
                "build", List.of(new RemotePlayerMarkers.RemotePlayer(
                        "thost", "minecraft_overworld", 2, 64, 0, uuid)),
                "farm", List.of(new RemotePlayerMarkers.RemotePlayer(
                        "thost", "minecraft_overworld", 3, 64, 0, uuid)));
        assertTrue(VanillaSquadPlayerMapBridge.selectMapId(
                ambiguous, "minecraft:overworld", uuid, "thost", 0, 0) == null);
    }

    @Test
    void twoMatchingPlayersOnTheSameMapDoNotMakeItAmbiguous() {
        String localUuid = "2f4c3b65811535398569b527957f6721";
        String friendUuid = "770f80234155382b9bdee7e399cb92be";
        Map<String, List<RemotePlayerMarkers.RemotePlayer>> snapshots = Map.of(
                "build", List.of(
                        new RemotePlayerMarkers.RemotePlayer("thost", "minecraft_overworld", 101, 64, 100, localUuid),
                        new RemotePlayerMarkers.RemotePlayer("friend", "minecraft_overworld", 113, 64, 100, friendUuid)));

        String selected = VanillaSquadPlayerMapBridge.selectMapId(
                snapshots, "minecraft:overworld", localUuid, "thost", 100, 100,
                List.of(new RemotePlayerMarkers.Observer(friendUuid, "friend", 110, 100)));

        assertEquals("build", selected);
    }

    @Test
    void selectsRealmFromAnotherLoadedPlayerWhenTheLocalPlayerIsHiddenFromSquaremap() {
        String observerUuid = "770f80234155382b9bdee7e399cb92be";
        Map<String, List<RemotePlayerMarkers.RemotePlayer>> snapshots = Map.of(
                "build", List.of(new RemotePlayerMarkers.RemotePlayer(
                        "RonnyLearn", "minecraft_overworld", 44, 64, 239, observerUuid)),
                "farm", List.of(new RemotePlayerMarkers.RemotePlayer(
                        "Other", "minecraft_overworld", 44, 64, 239,
                        "27d05abbd6494df6b1513eaee244fd43")));

        String selected = VanillaSquadPlayerMapBridge.selectMapId(
                snapshots, "minecraft:overworld", "d087dd7791514f7b96213593fd584a89",
                "LTS_Server", 37, 184,
                List.of(new RemotePlayerMarkers.Observer(observerUuid, "RonnyLearn", 45, 240)));

        assertEquals("build", selected);
    }

    @Test
    void skipsEntriesMissingRequiredFieldsInsteadOfThrowing() {
        String body = "{\"players\":[{\"world\":\"minecraft_overworld\",\"name\":\"noPosition\"}]}";

        List<RemotePlayerMarkers.RemotePlayer> players = VanillaSquadPlayerMapBridge.parse(body);

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

        List<RemotePlayerMarkers.RemotePlayer> players = VanillaSquadPlayerMapBridge.parse(body);

        assertEquals(1, players.size());
        assertEquals("valid", players.get(0).name());
    }

    @Test
    void normalizeUuidStripsDashesAndLowercasesSoItMatchesMinecraftsUuidFormat() {
        String mojangStyle = "2F4C3B65-8115-3539-8569-B527957F6721";
        String squaremapStyle = "2f4c3b65811535398569b527957f6721";

        assertEquals(RemotePlayerMarkers.normalizeUuid(squaremapStyle),
                RemotePlayerMarkers.normalizeUuid(mojangStyle));
        assertEquals(null, RemotePlayerMarkers.normalizeUuid("not-a-uuid"));
    }
}
