package com.voxymap.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/**
 * Whether the client is connected to the VanillaSquad server. The VanillaSquad settings and overlays only
 * make sense there: the data comes from that server's public live map, so on any other server or in a
 * singleplayer world they would show nothing or, worse, someone else's players.
 */
public final class VanillaSquadServer {
    private static final String DOMAIN = "vanillasquad.com";

    private VanillaSquadServer() {
    }

    /** True while the current multiplayer connection is to VanillaSquad. */
    public static boolean isConnected() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.isLocalServer()) {
            return false;
        }
        ServerData server = minecraft.getCurrentServer();
        return server != null && isVanillaSquadAddress(server.ip);
    }

    /** {@code vanillasquad.com} or any subdomain of it, with or without a port or trailing dot. */
    static boolean isVanillaSquadAddress(String address) {
        if (address == null) {
            return false;
        }
        String host = address.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            return false; // an IPv6 literal is never this domain
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
