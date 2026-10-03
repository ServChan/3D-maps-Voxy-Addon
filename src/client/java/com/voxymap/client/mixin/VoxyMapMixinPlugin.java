package com.voxymap.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class VoxyMapMixinPlugin implements IMixinConfigPlugin {
    private static final String IRIS_MIXIN_PREFIX = "com.voxymap.client.mixin.Iris";
    private static final String SPATIAL_GUI_MIXIN_PREFIX = "com.voxymap.client.mixin.SpatialGui";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(IRIS_MIXIN_PREFIX)) {
            return FabricLoader.getInstance().isModLoaded("iris");
        }
        if (mixinClassName.startsWith(SPATIAL_GUI_MIXIN_PREFIX)) {
            return FabricLoader.getInstance().isModLoaded("spatial-gui");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
