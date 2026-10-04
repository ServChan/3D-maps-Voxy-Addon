package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class VoxyMapCameraController {
    private static boolean active;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static float cameraYaw;
    private static float cameraPitch;
    private static float fov;
    private static double centerX;
    private static double centerY;
    private static double centerZ;

    private VoxyMapCameraController() {
    }

    public static void update(Minecraft minecraft, double centerX, double centerY, double centerZ, double yawRadians, double pitchControl, double blocksPerPixel) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            active = false;
            NetherRoofController.deactivate();
            return;
        }

        boolean endDimension = "minecraft:the_end".equals(minecraft.level.dimension().identifier().toString());
        Pose pose = pose(endDimension, centerX, centerY, centerZ, yawRadians, pitchControl, blocksPerPixel);
        cameraX = pose.x();
        cameraY = pose.y();
        cameraZ = pose.z();
        fov = pose.fov();
        VoxyMapCameraController.centerX = centerX;
        VoxyMapCameraController.centerY = centerY;
        VoxyMapCameraController.centerZ = centerZ;
        cameraYaw = pose.yaw();
        cameraPitch = pose.pitch();
        active = true;
    }

    public record Pose(double x, double y, double z, float yaw, float pitch, float fov) {
    }

    public static Pose pose(boolean endDimension, double centerX, double centerY, double centerZ, double yawRadians,
                            double pitchControl, double blocksPerPixel) {
        float pitchDegrees = Mth.clamp((float) (18.0 + pitchControl * 42.0), 18.0f, 72.0f);
        float yawDegrees = (float) Math.toDegrees(yawRadians);
        double desiredRange = Mth.clamp(blocksPerPixel * 210.0, 64.0, 8192.0);
        double range;
        float poseFov;
        if (endDimension) {
            pitchDegrees = Mth.clamp((float) (30.0 + pitchControl * 32.0), 30.0f, 62.0f);
            range = Math.max(512.0, desiredRange);
            poseFov = Mth.clamp((float) (70.0 * desiredRange / range), 14.0f, 70.0f);
        } else {
            range = Math.max(1024.0, desiredRange);
            poseFov = Mth.clamp((float) (70.0 * desiredRange / range), 6.0f, 70.0f);
        }

        double pitchRadians = Math.toRadians(pitchDegrees);
        double horizontalRange = Math.cos(pitchRadians) * range;
        double verticalRange = Math.sin(pitchRadians) * range;
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        return new Pose(centerX - forwardX * horizontalRange, centerY + verticalRange, centerZ - forwardZ * horizontalRange,
                yawDegrees, pitchDegrees, poseFov);
    }

    public static boolean isActive() {
        return active;
    }

    public static double cameraX() {
        return cameraX;
    }

    public static double cameraY() {
        return cameraY;
    }

    public static double cameraZ() {
        return cameraZ;
    }

    public static double centerX() {
        return centerX;
    }

    public static double centerY() {
        return centerY;
    }

    public static double centerZ() {
        return centerZ;
    }

    public static float cameraYaw() {
        return cameraYaw;
    }

    public static float cameraPitch() {
        return cameraPitch;
    }

    public static float fov() {
        return fov;
    }

    public static void deactivate() {
        active = false;
        NetherRoofController.deactivate();
    }
}
