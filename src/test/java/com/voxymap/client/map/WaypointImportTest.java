package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointImportTest {
    @Test
    void xaeroLinesBecomeCandidatesAndDeathpointsAreSkipped() {
        List<WaypointImport.Candidate> parsed = WaypointImport.parseXaero(List.of(
                "#waypoint:name:initials:x:y:z:color:disabled:type:set:rotate_on_tp:tp_yaw:visibility_type:destination",
                "waypoint:Base^col^ north:BN:120:64:-340:12:false:0:gui.xaero_default:false:0:0:false",
                "waypoint:Hidden:H:1:~:2:3:true:0:gui.xaero_default:false:0:0",
                "waypoint:gui.xaero_deathpoint:D:5:6:7:0:false:1:gui.xaero_default:false:0:0",
                "waypoint:Broken:B:x:6:7:0:false:0"));
        assertEquals(2, parsed.size());
        WaypointImport.Candidate base = parsed.get(0);
        assertEquals("Base: north", base.name());
        assertEquals("BN", base.symbol());
        assertEquals(-340, base.z());
        assertEquals(0xFFFF5555, base.color());
        WaypointImport.Candidate hidden = parsed.get(1);
        assertNull(hidden.y());
        assertTrue(hidden.disabled());
    }

    @Test
    void xaeroFoldersMapToDimensionsAndSets() {
        assertEquals(WaypointText.Dimension.OVERWORLD, WaypointImport.xaeroDimension("dim%0"));
        assertEquals(WaypointText.Dimension.NETHER, WaypointImport.xaeroDimension("dim%-1"));
        assertEquals(WaypointText.Dimension.END, WaypointImport.xaeroDimension("dim%1"));
        assertEquals(WaypointText.Dimension.NETHER, WaypointImport.xaeroDimension("dim%minecraft$the_nether"));
        assertEquals("", WaypointImport.xaeroSetName(Path.of("mw$default_1.txt")));
        assertEquals("farms", WaypointImport.xaeroSetName(Path.of("mw$farms_1.txt")));
    }

    @Test
    void journeyMapFilesOfBothShapesAreRead() {
        Map<WaypointText.Dimension, List<WaypointImport.Candidate>> found = new EnumMap<>(WaypointText.Dimension.class);
        WaypointImport.parseJourneyMap("""
                {"name":"Village","x":100,"y":70,"z":-20,"r":255,"g":0,"b":0,"enable":true,
                 "dimensions":["minecraft:overworld","minecraft:the_nether"]}""", found);
        WaypointImport.parseJourneyMap("""
                {"waypoints":[{"name":"Portal","x":10.7,"y":40,"z":3,"color":65280,"dimension":"-1"},
                              {"name":"Death","type":"Death","x":1,"z":1}]}""", found);
        assertEquals(1, found.get(WaypointText.Dimension.OVERWORLD).size());
        assertEquals(0xFFFF0000, found.get(WaypointText.Dimension.OVERWORLD).getFirst().color());
        assertEquals(List.of("Village", "Portal"),
                found.get(WaypointText.Dimension.NETHER).stream().map(WaypointImport.Candidate::name).toList());
        assertEquals(10, found.get(WaypointText.Dimension.NETHER).get(1).x());
    }

    @Test
    void sourcesForThisDimensionAndServerComeFirst() {
        WaypointImport.Found nether = new WaypointImport.Found(WaypointImport.Source.XAERO, "Multiplayer_play.example.net",
                "", WaypointText.Dimension.NETHER, List.of());
        WaypointImport.Found other = new WaypointImport.Found(WaypointImport.Source.XAERO, "Multiplayer_other.org",
                "", WaypointText.Dimension.OVERWORLD, List.of());
        WaypointImport.Found exact = new WaypointImport.Found(WaypointImport.Source.XAERO, "Multiplayer_play.example.net",
                "", WaypointText.Dimension.OVERWORLD, List.of());
        assertEquals(List.of(exact, other, nether), WaypointImport.rank(List.of(nether, other, exact),
                "play.example.net", WaypointText.Dimension.OVERWORLD));
    }
}
