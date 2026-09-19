package com.voxymap.client.mixin;

import com.voxymap.client.map.MapScreenshot;
import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Screenshot.class, remap = false)
public abstract class ScreenshotMixin {
    /** Redirects the screenshot key to the interface-free map capture while the map is open. */
    @Inject(method = "grab(Lnet/minecraft/client/Minecraft;Z)V", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private static void voxymap$captureMapWithoutInterface(Minecraft minecraft, boolean panorama, CallbackInfo ci) {
        boolean keepMarkers = VoxyMapSettings.screenshotContent() == VoxyMapSettings.ScreenshotContent.WITH_MARKERS;
        if (MapScreenshot.begin(MapScreenshot.isMapOpen(minecraft), keepMarkers)) {
            ci.cancel();
        }
    }
}
