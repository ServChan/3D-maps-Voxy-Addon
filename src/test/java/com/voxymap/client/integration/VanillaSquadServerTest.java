package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaSquadServerTest {
    @Test
    void recognisesTheServerAddressInItsCommonForms() {
        assertTrue(VanillaSquadServer.isVanillaSquadAddress("mc.vanillasquad.com"));
        assertTrue(VanillaSquadServer.isVanillaSquadAddress("vanillasquad.com"));
        assertTrue(VanillaSquadServer.isVanillaSquadAddress("MC.VanillaSquad.com:25565"));
        assertTrue(VanillaSquadServer.isVanillaSquadAddress("  mc.vanillasquad.com.  "));
    }

    @Test
    void rejectsOtherServersAndLookalikes() {
        assertFalse(VanillaSquadServer.isVanillaSquadAddress(null));
        assertFalse(VanillaSquadServer.isVanillaSquadAddress(""));
        assertFalse(VanillaSquadServer.isVanillaSquadAddress("127.0.0.1:25565"));
        assertFalse(VanillaSquadServer.isVanillaSquadAddress("mc.twenture.ru"));
        assertFalse(VanillaSquadServer.isVanillaSquadAddress("vanillasquad.com.evil.example"));
        assertFalse(VanillaSquadServer.isVanillaSquadAddress("notvanillasquad.com"));
        assertFalse(VanillaSquadServer.isVanillaSquadAddress("[::1]:25565"));
    }
}
