package com.voxymap.client.map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class WaypointChatShare {
    private static final long ECHO_WINDOW_MILLIS = 15_000L;
    private static final int MAX_PENDING = 9;

    private static final Queue<WaypointText.SharedWaypoint> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile String lastSent;
    private static volatile long lastSentAt;

    private WaypointChatShare() {
    }

    public static void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> onMessage(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onMessage(message);
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(command()));
    }

    public static boolean send(Minecraft minecraft, WaypointManager.Waypoint waypoint) {
        return send(minecraft, waypoint, currentDimension(minecraft));
    }

    public static boolean send(Minecraft minecraft, WaypointManager.Waypoint waypoint, WaypointText.Dimension dimension) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || waypoint == null) {
            return false;
        }
        String text = WaypointText.toXaero(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.color(),
                dimension == null ? WaypointText.Dimension.OVERWORLD : dimension);
        lastSent = text;
        lastSentAt = System.currentTimeMillis();
        connection.sendChat(text);
        return true;
    }

    public static void tick(Minecraft minecraft) {
        if (PENDING.isEmpty()) {
            return;
        }
        WaypointText.SharedWaypoint shared;
        while ((shared = PENDING.poll()) != null) {
            if (minecraft.player != null) {
                minecraft.gui.hud.getChat().addClientSystemMessage(prompt(minecraft, shared));
            }
        }
    }

    public static WaypointText.Dimension currentDimension(Minecraft minecraft) {
        return minecraft.level == null ? null
                : WaypointText.Dimension.fromLevelKey(minecraft.level.dimension().identifier().toString());
    }

    private static void onMessage(Component message) {
        String text = message.getString();
        List<WaypointText.SharedWaypoint> found = WaypointText.parseXaero(text);
        if (found.isEmpty()) {
            return;
        }
        String sent = lastSent;
        if (sent != null && text.contains(sent) && System.currentTimeMillis() - lastSentAt < ECHO_WINDOW_MILLIS) {
            lastSent = null;
            return;
        }
        for (WaypointText.SharedWaypoint shared : found) {
            if (PENDING.size() < MAX_PENDING) {
                PENDING.add(shared);
            }
        }
    }

    private static Component prompt(Minecraft minecraft, WaypointText.SharedWaypoint shared) {
        WaypointText.Dimension here = currentDimension(minecraft);
        MutableComponent line = Component.literal("[VoxyMap] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(shared.name())
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(WaypointText.chatColorArgb(shared.colorIndex()) & 0xFFFFFF))))
                .append(Component.literal(" " + shared.x() + ", " + (shared.y() == null ? "~" : shared.y()) + ", " + shared.z())
                        .withStyle(ChatFormatting.GRAY));
        WaypointText.Converted converted = WaypointText.convert(shared.x(), shared.z(), shared.dimension(), here);
        if (converted == null) {
            return line.append(Component.literal(" ")).append(Component.translatable("message.voxymap.waypoint.other_dimension")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (here != shared.dimension()) {
            line.append(Component.literal(" ")).append(Component.translatable("message.voxymap.waypoint.converted",
                    converted.x(), converted.z()).withStyle(ChatFormatting.YELLOW));
        }
        String command = "/voxymap waypoint add " + shared.dimension().id() + " " + shared.x() + " "
                + (shared.y() == null ? "~" : shared.y()) + " " + shared.z() + " " + shared.colorIndex() + " " + shared.name();
        Component button = Component.translatable("message.voxymap.waypoint.add_button")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.voxymap.waypoint.add_hover"))));
        return line.append(Component.literal(" ")).append(button);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("voxymap")
                .then(ClientCommands.literal("waypoint")
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("dimension", StringArgumentType.word())
                                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("y", StringArgumentType.word())
                                                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                                .then(ClientCommands.argument("color", IntegerArgumentType.integer(0, 15))
                                                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                                                .executes(WaypointChatShare::addFromCommand)))))))));
    }

    private static int addFromCommand(CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        WaypointText.Dimension from = WaypointText.Dimension.fromId(StringArgumentType.getString(context, "dimension"));
        WaypointText.Dimension here = currentDimension(minecraft);
        WaypointText.Converted converted = from == null ? null : WaypointText.convert(
                IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"), from, here);
        if (worldId == null || minecraft.player == null || converted == null) {
            context.getSource().sendError(Component.translatable("message.voxymap.waypoint.add_failed"));
            return 0;
        }
        String rawY = StringArgumentType.getString(context, "y");
        double y;
        try {
            y = "~".equals(rawY) ? minecraft.player.getY() : Integer.parseInt(rawY);
        } catch (NumberFormatException exception) {
            y = minecraft.player.getY();
        }
        String name = WaypointText.cleanName(StringArgumentType.getString(context, "name"));
        int color = WaypointText.chatColorArgb(IntegerArgumentType.getInteger(context, "color"));
        WaypointManager.Waypoint added = WaypointManager.add(worldId, name, converted.x() + 0.5, y, converted.z() + 0.5,
                color, false);
        if (added == null) {
            context.getSource().sendError(Component.translatable("message.voxymap.waypoint.add_failed"));
            return 0;
        }
        context.getSource().sendFeedback(Component.translatable("message.voxymap.waypoint.added", added.name()));
        return 1;
    }
}
