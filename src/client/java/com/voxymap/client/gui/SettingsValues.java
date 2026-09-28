package com.voxymap.client.gui;

import com.voxymap.client.map.OreMaskController;
import com.voxymap.client.map.VoxyShaderPatcher;

final class SettingsValues {
    private SettingsValues() {
    }

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
