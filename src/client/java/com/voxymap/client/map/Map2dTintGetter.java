package com.voxymap.client.map;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

final class Map2dTintGetter implements BlockAndTintGetter {
    private final int minY;
    private final int height;
    private Biome biome;

    Map2dTintGetter(int minY, int height) {
        this.minY = minY;
        this.height = height;
    }

    void setBiome(Biome biome) {
        this.biome = biome;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return biome == null ? 0xFFFFFF : resolver.getColor(biome, pos.getX(), pos.getZ());
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return null;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinY() {
        return minY;
    }
}
