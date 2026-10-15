package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class MapEntityScale {
    static final double PLAYER_MIN_PIXELS = 14.0;
    static final double MOB_MIN_PIXELS = 8.0;
    static final float MAX_FACTOR = 8.0f;

    private MapEntityScale() {
    }

    public static boolean shown(Entity entity) {
        if (!VoxyMapCameraController.isActive() || MapViewMode.flat() || entity.isInvisible()) {
            return false;
        }
        VoxyMapSettings.MapEntityModels models = VoxyMapSettings.mapEntityModels();
        Minecraft minecraft = Minecraft.getInstance();
        boolean allowed;
        if (entity instanceof Player) {
            allowed = models.players() && !hiddenOnMap(entity) && (entity == minecraft.player
                    || ServerRules.radarAllowed() && VoxyMapSettings.radarShows(RadarCategory.PLAYERS));
        } else {
            RadarCategory category = MobMarkerCache.categoryOf(entity);
            allowed = models.mobs() && category != null && category != RadarCategory.ITEMS
                    && ServerRules.radarAllowed() && VoxyMapSettings.radarShows(category);
        }
        return allowed && requiredFactor(entity, entity.getX(), entity.getY(), entity.getZ()) <= MAX_FACTOR
                && MapEntityOcclusion.visible(entity);
    }

    public static boolean hiddenOnMap(Entity entity) {
        return entity instanceof Player && entity != Minecraft.getInstance().player
                && VoxyMapCameraController.isActive() && VoxyMapSettings.playerHidden(entity.getUUID());
    }

    public static float factor(Entity entity, double x, double y, double z) {
        return (float) Math.max(1.0, Math.min(MAX_FACTOR, requiredFactor(entity, x, y, z)));
    }

    private static double requiredFactor(Entity entity, double x, double y, double z) {
        double minPixels = entity instanceof Player ? PLAYER_MIN_PIXELS : MOB_MIN_PIXELS;
        int screenHeight = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        return requiredFactor(entity.getDimensions(Pose.STANDING).height(), x - VoxyMapCameraController.cameraX(),
                y - VoxyMapCameraController.cameraY(), z - VoxyMapCameraController.cameraZ(),
                VoxyMapCameraController.fov(), screenHeight, minPixels);
    }

    static double requiredFactor(double height, double dx, double dy, double dz, double fovDegrees, int screenHeight,
                                 double minPixels) {
        double distance = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double visible = 2.0 * distance * Math.tan(Math.toRadians(fovDegrees) * 0.5);
        double pixels = Math.max(0.05, height) * screenHeight / visible;
        return minPixels / pixels;
    }

    static boolean fitsOnMap(double requiredFactor) {
        return requiredFactor <= MAX_FACTOR;
    }
}
