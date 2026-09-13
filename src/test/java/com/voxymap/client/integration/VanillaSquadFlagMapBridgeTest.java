package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaSquadFlagMapBridgeTest {
    @Test
    void parsesOnlyTheBannersMarkerSetIgnoringSpawnAndWorldBorder() {
        String body = """
                [
                  {"id":"squaremap-spawn_icon","name":"Спавн","markers":[
                    {"tooltip":"Спавн","icon":"squaremap-spawn_icon","type":"icon","point":{"x":43,"z":182}}
                  ]},
                  {"id":"squaremap-worldborder","name":"Граница мира","markers":[
                    {"type":"polyline","points":[{"x":-100,"z":-100},{"x":100,"z":100}]}
                  ]},
                  {"id":"banners","name":"Метки","markers":[
                    {"tooltip":"<center>Virelia</center>","icon":"BLACK","type":"icon","point":{"x":711,"z":1848}},
                    {"tooltip":"<center></center>","icon":"RED","type":"icon","point":{"x":-4215,"z":-5525}}
                  ]}
                ]
                """;

        List<VanillaSquadFlagMapBridge.Flag> flags = VanillaSquadFlagMapBridge.parse(body);

        assertEquals(2, flags.size());
        VanillaSquadFlagMapBridge.Flag virelia = flags.get(0);
        assertEquals(711.0, virelia.x());
        assertEquals(1848.0, virelia.z());
        assertEquals("BLACK", virelia.icon());
        assertEquals("Virelia", virelia.tooltip(), "HTML tags around the nation name must be stripped");
        assertEquals("", flags.get(1).tooltip());
    }

    @Test
    void returnsEmptyWhenNoBannerSetIsPresent() {
        assertTrue(VanillaSquadFlagMapBridge.parse("[{\"id\":\"squaremap-spawn_icon\",\"markers\":[]}]").isEmpty());
    }

    @Test
    void malformedMarkerDoesNotDiscardValidFlags() {
        String body = """
                [{"id":"banners","markers":[
                    {"tooltip":null,"point":{"x":0,"z":0}},
                    {"tooltip":"Valid","icon":"red","point":{"x":4,"z":8}}
                ]}]
                """;

        List<VanillaSquadFlagMapBridge.Flag> flags = VanillaSquadFlagMapBridge.parse(body);

        assertEquals(1, flags.size());
        assertEquals("RED", flags.get(0).icon());
    }

    @Test
    void malformedMarkerSetsDoNotDiscardTheValidBannerSet() {
        String body = """
                [null, 42, {"id":{}},
                 {"id":"banners","markers":[
                   {"tooltip":"Valid","icon":"blue","point":{"x":4,"z":8}}
                 ]}]
                """;

        List<VanillaSquadFlagMapBridge.Flag> flags = VanillaSquadFlagMapBridge.parse(body);

        assertEquals(1, flags.size());
        assertEquals("Valid", flags.get(0).tooltip());
    }

    @Test
    void colorForFallsBackToWhiteForUnknownOrMissingIcons() {
        assertEquals(VanillaSquadFlagMapBridge.Color.RED.argb, VanillaSquadFlagMapBridge.colorFor("RED"));
        assertEquals(VanillaSquadFlagMapBridge.Color.RED.argb, VanillaSquadFlagMapBridge.colorFor("red"));
        assertEquals(VanillaSquadFlagMapBridge.Color.WHITE.argb, VanillaSquadFlagMapBridge.colorFor("squaremap-spawn_icon"));
        assertEquals(VanillaSquadFlagMapBridge.Color.WHITE.argb, VanillaSquadFlagMapBridge.colorFor(null));
    }
}
