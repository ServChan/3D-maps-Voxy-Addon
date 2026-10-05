package com.voxymap.client.map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public final class MinimapOverlays {
    static final long SLIME_SALT = 987234911L;
    static final int SLIME_COLOR = 0xFF55FF55;
    static final int SPAWN_COLOR = 0xFFFF3030;

    private MinimapOverlays() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(command()));
    }

    public static Long slimeSeed(Level level) {
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            try {
                return server.overworld().getSeed();
            } catch (RuntimeException ignored) {

            }
        }
        return VoxyMapSettings.slimeSeed(MinimapRenderer.currentWorldId(minecraft));
    }

    public static boolean slimeSeedKnown(Minecraft minecraft) {
        return minecraft.level == null || !minecraft.level.dimension().equals(Level.OVERWORLD)
                || slimeSeed(minecraft.level) != null;
    }

    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, SLIME_SALT).nextInt(10) == 0;
    }

    static long parseSeed(String text) {
        String trimmed = text.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException exception) {
            return trimmed.hashCode();
        }
    }

    static int biomeColor(String biomeId) {
        int hash = biomeId.hashCode();
        float hue = (Math.floorMod(hash * 0x9E3779B1, 360)) / 360.0f;
        float saturation = 0.55f + (Math.floorMod(hash >>> 9, 30)) / 100.0f;
        return MapColorMath.hsv(hue, saturation, 0.9f);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("voxymap")
                .then(ClientCommands.literal("seed")
                        .then(ClientCommands.literal("clear").executes(MinimapOverlays::clearSeed))
                        .then(ClientCommands.argument("seed", StringArgumentType.greedyString())
                                .executes(MinimapOverlays::setSeed)));
    }

    private static int setSeed(CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (worldId == null || minecraft.level == null || !minecraft.level.dimension().equals(Level.OVERWORLD)) {
            context.getSource().sendError(Component.translatable("message.voxymap.seed.overworld_only"));
            return 0;
        }
        long seed = parseSeed(StringArgumentType.getString(context, "seed"));
        VoxyMapSettings.setSlimeSeed(worldId, seed);
        context.getSource().sendFeedback(Component.translatable("message.voxymap.seed.saved", Long.toString(seed)));
        return 1;
    }

    private static int clearSeed(CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        VoxyMapSettings.setSlimeSeed(MinimapRenderer.currentWorldId(minecraft), null);
        context.getSource().sendFeedback(Component.translatable("message.voxymap.seed.cleared"));
        return 1;
    }
}
