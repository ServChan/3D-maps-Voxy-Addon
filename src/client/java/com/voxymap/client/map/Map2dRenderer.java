package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Map2dRenderer {
    static final int BACKGROUND = 0xFF05080F;
    private static final int MAX_VISIBLE_TILES = 96;
    private static final int UNKNOWN_HINT = 0x18FFFFFF;

    private Map2dRenderer() {
    }

    public record View(double centerX, double centerZ, double blocksPerPixel, int width, int height, int slice) {
        double screenX(double worldX) {
            return Map2dGeometry.screenX(worldX, centerX, blocksPerPixel, width);
        }

        double screenY(double worldZ) {
            return Map2dGeometry.screenY(worldZ, centerZ, blocksPerPixel, height);
        }
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft, View view) {
        g.fill(0, 0, view.width(), view.height(), BACKGROUND);
        if (!Map2dTiles.prepare(minecraft, view.slice())) {
            return;
        }
        Map2dTiles.tick(minecraft);
        int level = Map2dGeometry.levelFor(view.blocksPerPixel());
        while (level < Map2dGeometry.MAX_LEVEL && tileCount(view, level) > MAX_VISIBLE_TILES) {
            level++;
        }
        List<int[]> tiles = visibleTiles(view, level);
        for (int[] tile : tiles) {
            drawTile(g, view, level, tile[0], tile[1]);
        }
    }

    static long tileCount(View view, int level) {
        double tileBlocks = Map2dGeometry.tileBlocks(level);
        long across = (long) Math.ceil(view.width() * view.blocksPerPixel() / tileBlocks) + 1;
        long down = (long) Math.ceil(view.height() * view.blocksPerPixel() / tileBlocks) + 1;
        return across * down;
    }

    static List<int[]> visibleTiles(View view, int level) {
        int tileBlocks = Map2dGeometry.tileBlocks(level);
        double halfWidth = view.width() / 2.0 * view.blocksPerPixel();
        double halfHeight = view.height() / 2.0 * view.blocksPerPixel();
        int minX = Math.floorDiv((int) Math.floor(view.centerX() - halfWidth), tileBlocks);
        int maxX = Math.floorDiv((int) Math.floor(view.centerX() + halfWidth), tileBlocks);
        int minZ = Math.floorDiv((int) Math.floor(view.centerZ() - halfHeight), tileBlocks);
        int maxZ = Math.floorDiv((int) Math.floor(view.centerZ() + halfHeight), tileBlocks);
        double centerTileX = view.centerX() / tileBlocks - 0.5;
        double centerTileZ = view.centerZ() / tileBlocks - 0.5;
        List<int[]> tiles = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                tiles.add(new int[]{x, z});
            }
        }
        tiles.sort(Comparator.comparingDouble(tile -> {
            double dx = tile[0] - centerTileX;
            double dz = tile[1] - centerTileZ;
            return dx * dx + dz * dz;
        }));
        return tiles.size() > MAX_VISIBLE_TILES ? tiles.subList(0, MAX_VISIBLE_TILES) : tiles;
    }

    private static void drawTile(GuiGraphicsExtractor g, View view, int level, int tileX, int tileZ) {
        int tileBlocks = Map2dGeometry.tileBlocks(level);
        int x0 = (int) Math.round(view.screenX((double) tileX * tileBlocks));
        int x1 = (int) Math.round(view.screenX((double) (tileX + 1) * tileBlocks));
        int y0 = (int) Math.round(view.screenY((double) tileZ * tileBlocks));
        int y1 = (int) Math.round(view.screenY((double) (tileZ + 1) * tileBlocks));
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        Map2dTiles.TileKey key = new Map2dTiles.TileKey(level, tileX, tileZ);
        Identifier texture = Map2dTiles.texture(key);
        Map2dTiles.request(key);
        int size = Map2dGeometry.TILE_PIXELS;
        if (texture != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0.0f, 0.0f, x1 - x0, y1 - y0,
                    size, size, size, size, 0xFFFFFFFF);
            return;
        }
        if (Map2dTiles.knownEmpty(key)) {
            return;
        }
        for (int up = 1; level + up <= Map2dGeometry.MAX_LEVEL; up++) {
            int parentX = tileX >> up;
            int parentZ = tileZ >> up;
            Identifier parent = Map2dTiles.texture(new Map2dTiles.TileKey(level + up, parentX, parentZ));
            if (parent == null) {
                continue;
            }
            int part = size >> up;
            float u = (tileX - (parentX << up)) * part;
            float v = (tileZ - (parentZ << up)) * part;
            g.blit(RenderPipelines.GUI_TEXTURED, parent, x0, y0, u, v, x1 - x0, y1 - y0, part, part, size, size,
                    0xFFFFFFFF);
            return;
        }
        g.fill(x0, y0, x1, y0 + 1, UNKNOWN_HINT);
        g.fill(x0, y0, x0 + 1, y1, UNKNOWN_HINT);
    }
}
