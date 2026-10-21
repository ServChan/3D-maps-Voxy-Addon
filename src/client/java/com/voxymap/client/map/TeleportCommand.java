package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.server.permissions.Permissions;

import java.util.regex.Pattern;

public final class TeleportCommand {
    public static final String DEFAULT_SERVER_TEMPLATE = "tppos {x} {y} {z}";
    public static final int MAX_LENGTH = 128;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private TeleportCommand() {
    }

    public static String normalize(String template) {
        if (template == null) {
            return "";
        }
        String value = CONTROL_CHARACTERS.matcher(template).replaceAll(" ").strip();
        while (value.startsWith("/")) {
            value = value.substring(1).stripLeading();
        }
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH).strip() : value;
    }

    public static String format(String template, int x, int y, int z) {
        String value = normalize(template);
        if (value.isEmpty()) {
            return null;
        }
        return value
                .replace("{x}", Integer.toString(x)).replace("<x>", Integer.toString(x))
                .replace("{y}", Integer.toString(y)).replace("<y>", Integer.toString(y))
                .replace("{z}", Integer.toString(z)).replace("<z>", Integer.toString(z));
    }

    public static boolean available(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        if (minecraft.hasSingleplayerServer()) {
            return minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        }
        return !normalize(VoxyMapSettings.serverTeleportCommand()).isEmpty();
    }

    public static boolean send(Minecraft minecraft, int x, int y, int z) {
        if (!available(minecraft)) {
            return false;
        }
        String command = minecraft.hasSingleplayerServer()
                ? "tp @s " + x + " " + y + " " + z
                : format(VoxyMapSettings.serverTeleportCommand(), x, y, z);
        if (command == null) {
            return false;
        }
        minecraft.getConnection().sendCommand(command);
        return true;
    }
}
