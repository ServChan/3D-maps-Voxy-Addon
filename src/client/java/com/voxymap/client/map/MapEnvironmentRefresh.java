package com.voxymap.client.map;

import com.voxymap.client.mixin.GameRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;

public final class MapEnvironmentRefresh {
    private static ClientLevel lastLevel;
    private static VoxyMapSettings.TimeOfDay lastTime;
    private static VoxyMapSettings.MapWeather lastWeather;

    private MapEnvironmentRefresh() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        VoxyMapSettings.TimeOfDay time = VoxyMapSettings.activeMapLightingOverride();
        VoxyMapSettings.MapWeather weather = VoxyMapSettings.activeMapWeatherOverride();
        if (level == lastLevel && time == lastTime && weather == lastWeather) {
            return;
        }
        boolean sameLevel = level == lastLevel;
        lastLevel = level;
        lastTime = time;
        lastWeather = weather;
        if (level != null && sameLevel) {
            refresh(client, level);
        }
    }

    private static void refresh(Minecraft client, ClientLevel level) {
        level.environmentAttributes().invalidateTickCache();
        ((GameRendererAccessor) client.gameRenderer).getVoxymapLightmapExtractor().tick();
        Camera camera = client.gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        EnvironmentAttributeProbe probe = camera.attributeProbe();
        probe.reset();
        probe.tick(level, camera.position());
    }
}
