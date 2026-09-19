package com.voxymap.client.gui;

import com.voxymap.client.map.OreMaskController;
import com.voxymap.client.map.VoxyShaderPatcher;

/** Setting readouts shared by the in-map panel and the Mod Menu screen. */
final class SettingsValues {
    private SettingsValues() {
    }

    /** Translation key describing whether the Voxy shader patches loaded. */
    static String diagnosticsKey() {
        if (!VoxyShaderPatcher.isSupported()) {
            return "screen.voxymap.diagnostics.shader_unsupported";
        }
        if (!OreMaskController.isSupported()) {
            return "screen.voxymap.diagnostics.ore_mask_unsupported";
        }
        return "screen.voxymap.diagnostics.ok";
    }
}
