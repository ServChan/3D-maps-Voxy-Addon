package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRulesTest {
    @Test
    void plainTextChangesNothing() {
        assertSame(ServerRules.Rules.NONE, ServerRules.apply(ServerRules.Rules.NONE, "fairxaero nominimap"));
        assertFalse(ServerRules.Rules.NONE.restricted());
    }

    @Test
    void xaeroFairPlayDisablesRadarAndCaves() {
        ServerRules.Rules rules = ServerRules.apply(ServerRules.Rules.NONE, "Welcome! " + ServerRules.XAERO_FAIR_PLAY);
        assertFalse(rules.radarAllowed());
        assertFalse(rules.caveMapsAllowed(false));
        assertFalse(rules.caveMapsAllowed(true));
        assertTrue(rules.minimapAllowed());
    }

    @Test
    void netherFairKeepsTheNetherOpenUnderFairPlay() {
        ServerRules.Rules rules = ServerRules.apply(ServerRules.Rules.NONE,
                ServerRules.XAERO_FAIR_PLAY + ServerRules.XAERO_WORLD_MAP_NETHER_FAIR);
        assertTrue(rules.caveMapsAllowed(true));
        assertFalse(rules.caveMapsAllowed(false));
        ServerRules.Rules minimapCode = ServerRules.apply(ServerRules.Rules.NONE,
                ServerRules.XAERO_MINIMAP_NETHER_FAIR + ServerRules.XAERO_FAIR_PLAY);
        assertTrue(minimapCode.caveMapsAllowed(true));
    }

    @Test
    void netherFairAloneRestrictsNothing() {
        ServerRules.Rules rules = ServerRules.apply(ServerRules.Rules.NONE, ServerRules.XAERO_WORLD_MAP_NETHER_FAIR);
        assertFalse(rules.restricted());
    }

    @Test
    void ownCodesRestrictSingleFeatures() {
        ServerRules.Rules radar = ServerRules.apply(ServerRules.Rules.NONE, ServerRules.VOXYMAP_NO_RADAR);
        assertFalse(radar.radarAllowed());
        assertTrue(radar.caveMapsAllowed(false));
        ServerRules.Rules caves = ServerRules.apply(ServerRules.Rules.NONE, ServerRules.VOXYMAP_NO_CAVES);
        assertTrue(caves.radarAllowed());
        assertFalse(caves.caveMapsAllowed(true));
        ServerRules.Rules minimap = ServerRules.apply(ServerRules.Rules.NONE, ServerRules.VOXYMAP_NO_MINIMAP);
        assertFalse(minimap.minimapAllowed());
        assertFalse(ServerRules.apply(ServerRules.Rules.NONE, ServerRules.XAERO_NO_MINIMAP).minimapAllowed());
    }

    @Test
    void rulesAccumulateAndResetClearsThem() {
        ServerRules.Rules rules = ServerRules.apply(ServerRules.Rules.NONE, ServerRules.VOXYMAP_NO_RADAR);
        rules = ServerRules.apply(rules, ServerRules.VOXYMAP_NO_CAVES);
        assertFalse(rules.radarAllowed());
        assertFalse(rules.caveMapsAllowed(false));
        assertEquals(ServerRules.Rules.NONE, ServerRules.apply(rules, ServerRules.XAERO_RESET));
        assertEquals(ServerRules.Rules.NONE, ServerRules.apply(rules, ServerRules.VOXYMAP_RESET));
    }

    @Test
    void resetAndNewCodeInOneMessageKeepsTheNewCode() {
        ServerRules.Rules rules = ServerRules.apply(ServerRules.Rules.NONE, ServerRules.VOXYMAP_NO_RADAR);
        rules = ServerRules.apply(rules, ServerRules.VOXYMAP_RESET + ServerRules.VOXYMAP_NO_CAVES);
        assertTrue(rules.radarAllowed());
        assertFalse(rules.caveMapsAllowed(false));
    }
}
