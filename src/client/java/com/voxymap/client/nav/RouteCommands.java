package com.voxymap.client.nav;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.WaypointManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RouteCommands {
    private RouteCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(command()));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("voxymap")
                .then(ClientCommands.literal("route")
                        .then(ClientCommands.literal("stop").executes(RouteCommands::stop))
                        .then(ClientCommands.literal("waypoint")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(RouteCommands::toWaypoint)))
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                        .executes(RouteCommands::toCoordinates))));
    }

    private static int stop(CommandContext<FabricClientCommandSource> context) {
        Navigator.stop(Minecraft.getInstance());
        return 1;
    }

    private static int toCoordinates(CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (worldId == null) {
            context.getSource().sendError(Component.translatable("message.voxymap.route.no_world"));
            return 0;
        }
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        Navigator.start(minecraft, new Navigator.Target(x + " / " + z, x + 0.5, Double.NaN, z + 0.5, worldId));
        return 1;
    }

    private static int toWaypoint(CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        String name = StringArgumentType.getString(context, "name").trim();
        if (worldId == null) {
            context.getSource().sendError(Component.translatable("message.voxymap.route.no_world"));
            return 0;
        }
        for (WaypointManager.Waypoint waypoint : WaypointManager.forWorld(worldId)) {
            if (waypoint.name().equalsIgnoreCase(name)) {
                Navigator.start(minecraft, toWaypoint(waypoint, worldId));
                return 1;
            }
        }
        context.getSource().sendError(Component.translatable("message.voxymap.route.no_waypoint", name));
        return 0;
    }

    public static Navigator.Target toWaypoint(WaypointManager.Waypoint waypoint, String worldId) {
        return new Navigator.Target(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), worldId);
    }
}
