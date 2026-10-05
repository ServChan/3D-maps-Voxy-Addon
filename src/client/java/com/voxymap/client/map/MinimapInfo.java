package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MinimapInfo {
    static final double NETHER_SCALE = 8.0;
    static final long TICKS_PER_DAY = 24000L;

    private static final int PRIMARY_COLOR = 0xFFDFF8FF;
    private static final int SECONDARY_COLOR = 0xFFB8C7D9;
    private static final int DARK_COLOR = 0xFFFF7A6B;

    public record Line(String text, int color) {
    }

    private static Level cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    private static BlockPos cachedPos;
    private static Direction cachedFacing;
    private static Set<MinimapInfoLine> cachedShown;
    private static String cachedLanguage;
    private static List<Line> cachedLines = List.of();

    private MinimapInfo() {
    }

    public static List<Line> lines(Minecraft minecraft) {
        Level level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            return List.of();
        }
        Set<MinimapInfoLine> shown = VoxyMapSettings.infoLines();
        BlockPos pos = player.blockPosition();
        Direction facing = Direction.fromYRot(player.getYRot());
        long tick = level.getGameTime();
        String language = minecraft.getLanguageManager().getSelected();
        if (level == cachedLevel && tick == cachedTick && pos.equals(cachedPos) && facing == cachedFacing
                && shown.equals(cachedShown) && language.equals(cachedLanguage)) {
            return cachedLines;
        }
        cachedLevel = level;
        cachedTick = tick;
        cachedPos = pos;
        cachedFacing = facing;
        cachedShown = shown;
        cachedLanguage = language;
        cachedLines = build(level, player, pos, facing, shown);
        return cachedLines;
    }

    private static List<Line> build(Level level, Player player, BlockPos pos, Direction facing,
                                    Set<MinimapInfoLine> shown) {
        List<Line> lines = new ArrayList<>();
        for (MinimapInfoLine line : shown) {
            Line built = switch (line) {
                case COORDINATES -> new Line(pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), PRIMARY_COLOR);
                case NETHER_COORDINATES -> counterpartLine(level, player);
                case BIOME -> biomeLine(level, pos);
                case LIGHT -> lightLine(level, pos);
                case TIME -> timeLine(level);
                case CHUNK -> new Line(Component.translatable("overlay.voxymap.info.chunk",
                        pos.getX() >> 4, pos.getZ() >> 4, pos.getX() & 15, pos.getZ() & 15).getString(), SECONDARY_COLOR);
                case FACING -> new Line(Component.translatable("overlay.voxymap.info.facing",
                        Component.translatable("overlay.voxymap.info.dir." + facing.getName()),
                        axisLabel(facing), bearing(player.getYRot())).getString(), SECONDARY_COLOR);
            };
            if (built != null) {
                lines.add(built);
            }
        }
        return List.copyOf(lines);
    }

    private static Line counterpartLine(Level level, Player player) {
        if (level.dimension().equals(Level.NETHER)) {
            return new Line(Component.translatable("overlay.voxymap.info.overworld",
                    scaled(player.getX(), NETHER_SCALE), scaled(player.getZ(), NETHER_SCALE)).getString(), SECONDARY_COLOR);
        }
        if (level.dimension().equals(Level.OVERWORLD)) {
            return new Line(Component.translatable("overlay.voxymap.info.nether",
                    scaled(player.getX(), 1.0 / NETHER_SCALE), scaled(player.getZ(), 1.0 / NETHER_SCALE)).getString(),
                    SECONDARY_COLOR);
        }
        return null;
    }

    private static Line biomeLine(Level level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .map(key -> new Line(Component.translatable(key.identifier().toLanguageKey("biome")).getString(),
                        SECONDARY_COLOR))
                .orElse(null);
    }

    private static Line lightLine(Level level, BlockPos pos) {
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return new Line(Component.translatable("overlay.voxymap.info.light", block, sky).getString(),
                block == 0 ? DARK_COLOR : SECONDARY_COLOR);
    }

    private static Line timeLine(Level level) {
        long ticks = level.getOverworldClockTime();
        return new Line(Component.translatable("overlay.voxymap.info.time", dayOf(ticks), clockOf(ticks)).getString(),
                SECONDARY_COLOR);
    }

    static int scaled(double coordinate, double factor) {
        return (int) Math.floor(coordinate * factor);
    }

    static long dayOf(long ticks) {
        return Math.floorDiv(ticks + 6000L, TICKS_PER_DAY) + 1;
    }

    static String clockOf(long ticks) {
        long dayTicks = Math.floorMod(ticks + 6000L, TICKS_PER_DAY);
        long minutes = dayTicks * 1440L / TICKS_PER_DAY;
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static int bearing(float yawDegrees) {
        return (int) Math.floorMod(Math.round(yawDegrees) + 180L, 360L);
    }

    static String axisLabel(Direction facing) {
        return switch (facing) {
            case NORTH -> "-Z";
            case SOUTH -> "+Z";
            case EAST -> "+X";
            case WEST -> "-X";
            case UP -> "+Y";
            case DOWN -> "-Y";
        };
    }
}
