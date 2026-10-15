package com.voxymap.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.voxymap.client.map.SpriteReadLock;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SpriteContents.class, remap = false)
public abstract class SpriteContentsMixin {
    @WrapMethod(method = "close", remap = false, require = 0)
    private void voxymap$waitForColourReaders(Operation<Void> original) {
        SpriteReadLock.LOCK.writeLock().lock();
        try {
            original.call();
        } finally {
            SpriteReadLock.LOCK.writeLock().unlock();
        }
    }
}
