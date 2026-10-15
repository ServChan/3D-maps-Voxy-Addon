package com.voxymap.client.map;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class MapRenderSettingsGuard {
    private static CloudStatus previousCloudStatus;
    private static boolean swappedForSave;

    private MapRenderSettingsGuard() {
    }

    public static void applyForMap(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        CloudStatus current = minecraft.options.cloudStatus().get();
        if (previousCloudStatus == null) {
            previousCloudStatus = current;
        }
        if (current != CloudStatus.OFF) {
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
        }
    }

    public static void beforeOptionsSave(Options options) {
        if (previousCloudStatus != null && options.cloudStatus().get() != previousCloudStatus) {
            options.cloudStatus().set(previousCloudStatus);
            swappedForSave = true;
        }
    }

    public static void afterOptionsSave(Options options) {
        if (swappedForSave) {
            swappedForSave = false;
            if (previousCloudStatus != null) {
                options.cloudStatus().set(CloudStatus.OFF);
            }
        }
    }

    public static void restoreAfterMap(Minecraft minecraft) {
        if (minecraft != null && minecraft.options != null && previousCloudStatus != null) {
            if (minecraft.options.cloudStatus().get() != previousCloudStatus) {
                minecraft.options.cloudStatus().set(previousCloudStatus);
            }
        }

        previousCloudStatus = null;
    }
}
