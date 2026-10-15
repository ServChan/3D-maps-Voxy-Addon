package com.voxymap.gametest;

import com.voxymap.client.gui.MapScreen;
import com.voxymap.client.map.VoxyMapSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

public final class VoxyMapGameTest implements FabricClientGameTest {
    private static final int VOXY_SETTLE_TICKS = 200;
    private static final int MAP_SETTLE_TICKS = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("tp @p 0 120 0 0 60");
            context.runOnClient(client -> {
                if (VoxyMapSettings.pauseSingleplayer()) {
                    VoxyMapSettings.togglePauseSingleplayer();
                }
                setTime(VoxyMapSettings.TimeOfDay.AUTO);
                setWeather(VoxyMapSettings.MapWeather.CLEAR);
                VoxyMapSettings.setMap2d(false);
            });
            context.waitTicks(VOXY_SETTLE_TICKS);
            context.takeScreenshot("00_world_noon");

            context.setScreen(MapScreen::new);
            context.waitTicks(MAP_SETTLE_TICKS);
            context.takeScreenshot("01_map_time_auto_noon");
            for (VoxyMapSettings.TimeOfDay mode : new VoxyMapSettings.TimeOfDay[]{
                    VoxyMapSettings.TimeOfDay.MORNING, VoxyMapSettings.TimeOfDay.DAY,
                    VoxyMapSettings.TimeOfDay.EVENING, VoxyMapSettings.TimeOfDay.NIGHT}) {
                context.runOnClient(client -> setTime(mode));
                context.waitTicks(20);
                context.takeScreenshot("02_map_time_" + mode.name().toLowerCase());
            }
            context.runOnClient(client -> setTime(VoxyMapSettings.TimeOfDay.AUTO));

            context.runOnClient(client -> setWeather(VoxyMapSettings.MapWeather.RAIN));
            context.waitTicks(20);
            context.takeScreenshot("03_map_weather_rain_plains");
            context.runOnClient(client -> setWeather(VoxyMapSettings.MapWeather.THUNDER));
            context.waitTicks(20);
            context.takeScreenshot("04_map_weather_thunder");

            context.setScreen(() -> null);
            server.runCommand("fillbiome -48 40 -48 48 140 48 minecraft:snowy_plains");
            context.waitTicks(40);
            context.setScreen(MapScreen::new);
            context.runOnClient(client -> setWeather(VoxyMapSettings.MapWeather.RAIN));
            context.waitTicks(MAP_SETTLE_TICKS);
            context.takeScreenshot("05_map_weather_snow_biome");

            context.runOnClient(client -> setWeather(VoxyMapSettings.MapWeather.AUTO));
            server.runCommand("weather rain");
            context.waitTicks(80);
            context.takeScreenshot("06_map_weather_auto_real_snow");

            context.runOnClient(client -> {
                setWeather(VoxyMapSettings.MapWeather.CLEAR);
                VoxyMapSettings.setMap2d(false);
            });
            context.setScreen(() -> null);
            context.waitTicks(10);
        }
    }

    private static void setTime(VoxyMapSettings.TimeOfDay mode) {
        for (int i = 0; i < VoxyMapSettings.TimeOfDay.values().length && VoxyMapSettings.timeOfDayMode() != mode; i++) {
            VoxyMapSettings.cycleTimeOfDayMode(1);
        }
    }

    private static void setWeather(VoxyMapSettings.MapWeather weather) {
        for (int i = 0; i < VoxyMapSettings.MapWeather.values().length && VoxyMapSettings.mapWeather() != weather; i++) {
            VoxyMapSettings.cycleMapWeather(1);
        }
    }
}
