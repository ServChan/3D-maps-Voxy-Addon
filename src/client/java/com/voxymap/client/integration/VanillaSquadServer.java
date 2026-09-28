package com.voxymap.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

public final class VanillaSquadServer {
    private static final String DOMAIN = "vanillasquad.com";

    private VanillaSquadServer() {
    }

    public static boolean isConnected() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.isLocalServer()) {
            return false;
        }
        ServerData server = minecraft.getCurrentServer();
        return server != null && isVanillaSquadAddress(server.ip);
    }

    static boolean isVanillaSquadAddress(String address) {
        if (address == null) {
            return false;
        }
        String host = address.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            return false;
        }
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.equals(DOMAIN) || host.endsWith("." + DOMAIN);
    }
}
