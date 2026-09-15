package com.voxymap.client.map;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Approximate Solas 3.7 emitter chroma for light sources stored in Voxy's terrain cache. */
final class SolasLightColor {
    private SolasLightColor() {
    }

    static Rgb classify(Identifier id, BlockState state) {
        return id == null ? null : classify(id.toString(), state == null ? 0 : state.getLightEmission());
    }

    static Rgb classify(String id, int emission) {
        if (id == null || emission <= 0) return null;
        String path = id.substring(id.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);

        if (containsAny(path, "soul_", "sculk", "menril", "bioluminescent", "magnetic_light")) {
            return new Rgb(0.12f, 0.62f, 1.0f);
        }
        if (containsAny(path, "verdant", "uranium", "acid", "toxic", "lime", "green_", "copper_torch",
                "copper_lantern", "copper_fire", "enriching_")) {
            return new Rgb(0.12f, 1.0f, 0.28f);
        }
        if (containsAny(path, "nether_portal", "respawn_anchor", "crying_obsidian", "purple_", "amethyst")) {
            return new Rgb(0.72f, 0.16f, 1.0f);
        }
        if (containsAny(path, "pearlescent", "magenta_", "pink_")) {
            return new Rgb(1.0f, 0.24f, 0.68f);
        }
        if (path.contains("redstone_lamp")) {
            return new Rgb(1.0f, 0.43f, 0.10f);
        }
        if (containsAny(path, "redstone", "red_")) {
            return new Rgb(1.0f, 0.06f, 0.025f);
        }
        if (containsAny(path, "sea_lantern", "end_rod", "beacon", "light_blue_", "cyan_", "tesla")) {
            return new Rgb(0.48f, 0.92f, 1.0f);
        }
        if (containsAny(path, "ochre", "yellow_", "orange_", "lava", "magma", "fire", "torch", "lantern",
                "campfire", "glowstone", "shroomlight", "cave_vines", "jack_o_lantern", "candle")) {
            return new Rgb(1.0f, 0.43f, 0.10f);
        }
        // Solas treats otherwise-unclassified emitters as ordinary warm block light. Keeping the same
        // fallback means modded lamps still contribute without maintaining a hard dependency on them.
        return new Rgb(1.0f, 0.58f, 0.22f);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    record Rgb(float r, float g, float b) {
    }
}
