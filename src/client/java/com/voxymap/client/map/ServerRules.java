package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ServerRules {
    static final String XAERO_RESET = "§r§e§s§e§t§x§a§e§r§o";
    static final String XAERO_NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
    static final String XAERO_FAIR_PLAY = "§f§a§i§r§x§a§e§r§o";
    static final String XAERO_MINIMAP_NETHER_FAIR = "§x§a§e§r§o§m§m§n§e§t§h§e§r§i§s§f§a§i§r";
    static final String XAERO_WORLD_MAP_NETHER_FAIR = "§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r";
    static final String VOXYMAP_RESET = "§v§o§x§y§m§a§p§r§e§s§e§t";
    static final String VOXYMAP_NO_RADAR = "§v§o§x§y§m§a§p§n§o§r§a§d§a§r";
    static final String VOXYMAP_NO_CAVES = "§v§o§x§y§m§a§p§n§o§c§a§v§e§s";
    static final String VOXYMAP_NO_MINIMAP = "§v§o§x§y§m§a§p§n§o§m§i§n§i§m§a§p";

    public record Rules(boolean fairPlay, boolean netherFair, boolean noMinimap, boolean noRadar, boolean noCaves) {
        public static final Rules NONE = new Rules(false, false, false, false, false);

        public boolean radarAllowed() {
            return !fairPlay && !noRadar;
        }

        public boolean caveMapsAllowed(boolean nether) {
            if (noCaves) return false;
            return !fairPlay || nether && netherFair;
        }

        public boolean minimapAllowed() {
            return !noMinimap;
        }

        public boolean restricted() {
            return !radarAllowed() || !caveMapsAllowed(false) || !caveMapsAllowed(true) || !minimapAllowed();
        }
    }

    public enum Restriction {
        RADAR("screen.voxymap.rules.radar"),
        CAVES("screen.voxymap.rules.caves"),
        NETHER("screen.voxymap.rules.nether"),
        MINIMAP("screen.voxymap.rules.minimap");

        private final String translationKey;

        Restriction(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private static volatile Rules rules = Rules.NONE;
    private static ClientPacketListener lastConnection;
    private static final List<Runnable> CONNECTION_LISTENERS = new ArrayList<>();

    private ServerRules() {
    }

    public static Rules current() {
        return rules;
    }

    public static boolean radarAllowed() {
        return rules.radarAllowed();
    }

    public static boolean caveMapsAllowed(boolean nether) {
        return rules.caveMapsAllowed(nether);
    }

    public static boolean minimapAllowed() {
        return rules.minimapAllowed();
    }

    public static List<Restriction> restrictions() {
        Rules snapshot = rules;
        List<Restriction> result = new ArrayList<>();
        if (!snapshot.radarAllowed()) result.add(Restriction.RADAR);
        if (!snapshot.caveMapsAllowed(false)) result.add(Restriction.CAVES);
        if (!snapshot.caveMapsAllowed(true)) result.add(Restriction.NETHER);
        if (!snapshot.minimapAllowed()) result.add(Restriction.MINIMAP);
        return result;
    }

    public static void onConnectionChange(Runnable listener) {
        CONNECTION_LISTENERS.add(listener);
    }

    public static void onSystemMessage(Component message) {
        if (message == null) return;
        syncConnection(Minecraft.getInstance());
        rules = apply(rules, message.getString());
    }

    public static void tick(Minecraft minecraft) {
        syncConnection(minecraft);
    }

    private static void syncConnection(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == lastConnection) return;
        lastConnection = connection;
        rules = Rules.NONE;
        if (connection != null) {
            ServerData server = minecraft.getCurrentServer();
            if (server != null && server.motd != null) {
                rules = apply(rules, server.motd.getString());
            }
        }
        for (Runnable listener : CONNECTION_LISTENERS) {
            listener.run();
        }
    }

    static Rules apply(Rules current, String text) {
        if (text == null || text.indexOf('§') < 0) return current;
        Rules result = current;
        if (text.contains(XAERO_RESET) || text.contains(VOXYMAP_RESET)) {
            result = Rules.NONE;
        }
        boolean fairPlay = result.fairPlay() || text.contains(XAERO_FAIR_PLAY);
        boolean netherFair = result.netherFair() || text.contains(XAERO_MINIMAP_NETHER_FAIR)
                || text.contains(XAERO_WORLD_MAP_NETHER_FAIR);
        boolean noMinimap = result.noMinimap() || text.contains(XAERO_NO_MINIMAP) || text.contains(VOXYMAP_NO_MINIMAP);
        boolean noRadar = result.noRadar() || text.contains(VOXYMAP_NO_RADAR);
        boolean noCaves = result.noCaves() || text.contains(VOXYMAP_NO_CAVES);
        return new Rules(fairPlay, netherFair, noMinimap, noRadar, noCaves);
    }
}
