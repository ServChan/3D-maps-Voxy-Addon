package com.voxymap.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.voxymap.client.gui.VoxyMapConfigScreen;

public final class VoxyMapModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return VoxyMapConfigScreen::new;
    }
}
