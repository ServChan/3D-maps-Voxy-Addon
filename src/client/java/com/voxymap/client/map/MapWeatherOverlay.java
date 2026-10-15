package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;

public final class MapWeatherOverlay {
    private static final int DROPS = 90;
    private static final int FLAKES = 260;
    private static final int RAIN_COLOR = 0x5598B8E0;
    private static final int SNOW_COLOR = 0xCCF4F8FF;
    private static final long FLASH_PERIOD_NANOS = 7_000_000_000L;
    private static final long FLASH_NANOS = 180_000_000L;

    private MapWeatherOverlay() {
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft, int width, int height,
                               double viewX, double viewY, double viewZ) {
        if (minecraft.level == null || width <= 0 || height <= 0) {
            return;
        }
        float rain = minecraft.level.getRainLevel(1.0f);
        float thunder = minecraft.level.getThunderLevel(1.0f);
        if (rain <= 0.05f) {
            return;
        }
        Biome.Precipitation precipitation = precipitationAt(minecraft, viewX, viewY, viewZ);
        long now = System.nanoTime();
        double seconds = now / 1_000_000_000.0;
        if (precipitation == Biome.Precipitation.SNOW) {
            drawSnow(g, width, height, rain, seconds);
        } else if (precipitation == Biome.Precipitation.RAIN) {
            drawRain(g, width, height, rain, seconds);
        }
        if (thunder > 0.5f && precipitation != Biome.Precipitation.NONE
                && Math.floorMod(now, FLASH_PERIOD_NANOS) < FLASH_NANOS) {
            g.fill(0, 0, width, height, 0x30E8F0FF);
        }
    }

    static Biome.Precipitation precipitationAt(Minecraft minecraft, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!minecraft.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && minecraft.player != null) {
            pos = minecraft.player.blockPosition();
        }
        BlockPos surface = pos.atY(Math.max(pos.getY(), minecraft.level.getSeaLevel()));
        return minecraft.level.getBiome(surface).value().getPrecipitationAt(surface, minecraft.level.getSeaLevel());
    }

    private static void drawRain(GuiGraphicsExtractor g, int width, int height, float strength, double seconds) {
        int drops = Math.round(DROPS * strength);
        int color = scaledAlpha(RAIN_COLOR, strength);
        for (int i = 0; i < drops; i++) {
            double speed = 0.55 + fraction(i * 0.3819660112501051) * 0.45;
            int x = (int) (fraction(fraction(i * 0.7548776662466927) + seconds * 0.06 * speed) * (width + 40)) - 20;
            int y = (int) (fraction(fraction(i * 0.5698402909980532) + seconds * 0.9 * speed) * (height + 40)) - 20;
            int length = 6 + (i % 5);
            for (int step = 0; step < length; step++) {
                g.fill(x - step / 3, y + step, x - step / 3 + 1, y + step + 1, color);
            }
        }
    }

    private static void drawSnow(GuiGraphicsExtractor g, int width, int height, float strength, double seconds) {
        int flakes = Math.round(FLAKES * strength);
        int color = scaledAlpha(SNOW_COLOR, strength);
        for (int i = 0; i < flakes; i++) {
            double speed = 0.35 + fraction(i * 0.3819660112501051) * 0.3;
            double sway = Math.sin(seconds * (0.8 + speed) + i) * 6.0;
            int x = (int) (fraction(fraction(i * 0.7548776662466927) + seconds * 0.01) * (width + 20)) - 10
                    + (int) Math.round(sway);
            int y = (int) (fraction(fraction(i * 0.5698402909980532) + seconds * 0.12 * speed) * (height + 20)) - 10;
            if (i % 4 == 0) {
                g.fill(x - 1, y, x + 2, y + 1, color);
                g.fill(x, y - 1, x + 1, y + 2, color);
            } else {
                int size = i % 4 == 1 ? 2 : 1;
                g.fill(x, y, x + size, y + size, color);
            }
        }
    }

    private static int scaledAlpha(int argb, float strength) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, strength)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }
}
