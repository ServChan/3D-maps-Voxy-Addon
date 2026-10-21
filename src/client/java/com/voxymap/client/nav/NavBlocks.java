package com.voxymap.client.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

final class NavBlocks {
    static final byte PASS = 0;
    static final byte CANOPY = 1;
    static final byte TRUNK = 2;
    static final byte WATER = 3;
    static final byte ICE = 4;
    static final byte ROAD = 5;
    static final byte HAZARD = 6;
    static final byte LAVA = 7;
    static final byte SOLID = 8;

    private NavBlocks() {
    }

    static byte classify(BlockState state) {
        if (state == null || state.isAir()) {
            return PASS;
        }
        Fluid fluid = state.getFluidState().getType();
        if (fluid.isSame(Fluids.LAVA)) {
            return LAVA;
        }
        if (fluid.isSame(Fluids.WATER) && !hasCollision(state)) {
            return WATER;
        }
        if (state.is(BlockTags.ICE)) {
            return ICE;
        }
        if (state.is(BlockTags.LEAVES)) {
            return CANOPY;
        }
        if (state.is(BlockTags.LOGS)) {
            return TRUNK;
        }
        if (state.is(BlockTags.RAILS) || state.is(Blocks.DIRT_PATH)) {
            return ROAD;
        }
        if (state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return HAZARD;
        }
        return hasCollision(state) ? SOLID : PASS;
    }

    private static boolean hasCollision(BlockState state) {
        return !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    static byte surfaceKind(byte code, boolean canopy) {
        return switch (code) {
            case WATER -> NavKind.WATER;
            case ICE -> NavKind.ICE;
            case ROAD -> NavKind.ROAD;
            case HAZARD -> NavKind.HAZARD;
            case LAVA -> NavKind.BLOCKED;
            default -> canopy ? NavKind.FOREST : NavKind.LAND;
        };
    }
}
