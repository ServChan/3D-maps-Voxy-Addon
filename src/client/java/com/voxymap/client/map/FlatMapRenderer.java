package com.voxymap.client.map;

import com.voxymap.client.integration.LitematicaPlusBridge;
import com.voxymap.client.gui.WaypointIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public final class FlatMapRenderer {
    private static final int GRID_COLOR = 0x22FFFFFF;
    private static final int MIN_GRID_PIXELS = 6;

    private FlatMapRenderer() {
    }

    public record View(int left, int top, int width, int height, double centerX, double centerZ, double pixelsPerBlock) {
        public double screenX(double worldX) {
            return left + width / 2.0 + (worldX - centerX) * pixelsPerBlock;
        }

        public double screenY(double worldZ) {
            return top + height / 2.0 + (worldZ - centerZ) * pixelsPerBlock;
        }

        public double worldX(double screenX) {
            return centerX + (screenX - left - width / 2.0) / pixelsPerBlock;
        }

        public double worldZ(double screenY) {
            return centerZ + (screenY - top - height / 2.0) / pixelsPerBlock;
        }

        public boolean contains(double x, double y) {
            return x >= left && x < left + width && y >= top && y < top + height;
        }
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft, View view) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        FlatMapTerrain.update(minecraft);
        g.enableScissor(view.left(), view.top(), view.left() + view.width(), view.top() + view.height());
        drawTerrain(g, view);
        drawChunkGridIfEnabled(g, view);
        drawSchematics(g, view);
        drawWaypoints(g, minecraft, view);
        drawPlayers(g, minecraft, view, player);
        int selfX = (int) Math.round(view.screenX(player.getX()));
        int selfY = (int) Math.round(view.screenY(player.getZ()));
        MinimapRenderer.drawFacingIndicator(g, selfX, selfY, player.getYRot());
        MinimapRenderer.drawDot(g, selfX, selfY, 3, 0xFF0A0A12);
        MinimapRenderer.drawDot(g, selfX, selfY, 2, 0xFF55E7FF);
        g.disableScissor();
    }

    private static void drawTerrain(GuiGraphicsExtractor g, View view) {
        int size = FlatMapTerrain.REGION_BLOCKS;
        int minRegionX = Math.floorDiv((int) Math.floor(view.worldX(view.left())), size);
        int maxRegionX = Math.floorDiv((int) Math.floor(view.worldX(view.left() + view.width())), size);
        int minRegionZ = Math.floorDiv((int) Math.floor(view.worldZ(view.top())), size);
        int maxRegionZ = Math.floorDiv((int) Math.floor(view.worldZ(view.top() + view.height())), size);
        for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                Identifier texture = FlatMapTerrain.regionTexture(regionX, regionZ);
                if (texture == null) {
                    continue;
                }
                int x0 = (int) Math.round(view.screenX((double) regionX * size));
                int x1 = (int) Math.round(view.screenX((double) (regionX + 1) * size));
                int y0 = (int) Math.round(view.screenY((double) regionZ * size));
                int y1 = (int) Math.round(view.screenY((double) (regionZ + 1) * size));
                if (x1 <= x0 || y1 <= y0) {
                    continue;
                }
                g.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0.0f, 0.0f, x1 - x0, y1 - y0,
                        size, size, size, size, 0xFFFFFFFF);
            }
        }
    }

    static void drawChunkGridIfEnabled(GuiGraphicsExtractor g, View view) {
        if (VoxyMapSettings.chunkGridEnabled() && 16 * view.pixelsPerBlock() >= MIN_GRID_PIXELS) {
            drawChunkGrid(g, view);
        }
    }

    private static void drawChunkGrid(GuiGraphicsExtractor g, View view) {
        int right = view.left() + view.width();
        int bottom = view.top() + view.height();
        for (int x = (int) Math.floor(view.worldX(view.left()) / 16.0) * 16; view.screenX(x) < right; x += 16) {
            int sx = (int) Math.round(view.screenX(x));
            g.fill(sx, view.top(), sx + 1, bottom, GRID_COLOR);
        }
        for (int z = (int) Math.floor(view.worldZ(view.top()) / 16.0) * 16; view.screenY(z) < bottom; z += 16) {
            int sy = (int) Math.round(view.screenY(z));
            g.fill(view.left(), sy, right, sy + 1, GRID_COLOR);
        }
    }

    private static void drawSchematics(GuiGraphicsExtractor g, View view) {
        if (!VoxyMapSettings.schematicsEnabled()) {
            return;
        }
        for (LitematicaPlusBridge.Placement placement : LitematicaPlusBridge.placements()) {
            if (!placement.shown()) {
                continue;
            }
            int x0 = (int) Math.round(Math.max(view.screenX(placement.minX()), view.left() - 2.0));
            int x1 = (int) Math.round(Math.min(view.screenX(placement.maxX() + 1.0), view.left() + view.width() + 2.0));
            int y0 = (int) Math.round(Math.max(view.screenY(placement.minZ()), view.top() - 2.0));
            int y1 = (int) Math.round(Math.min(view.screenY(placement.maxZ() + 1.0), view.top() + view.height() + 2.0));
            if (x1 <= x0 || y1 <= y0) {
                continue;
            }
            g.outline(x0, y0, x1 - x0, y1 - y0, placement.selected() ? 0xFF00E5FF : 0xCC2FC7FF);
        }
    }

    private static void drawWaypoints(GuiGraphicsExtractor g, Minecraft minecraft, View view) {
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (worldId == null || !VoxyMapSettings.waypointsEnabled()) {
            return;
        }
        for (WaypointManager.Waypoint waypoint : WaypointManager.visibleForWorld(worldId, WaypointDisplay.Surface.MAP)) {
            double sx = view.screenX(waypoint.x());
            double sy = view.screenY(waypoint.z());
            if (!view.contains(sx, sy)) {
                continue;
            }
            int px = (int) Math.round(sx);
            int py = (int) Math.round(sy);
            WaypointIcons.drawCentered(g, minecraft.font, px, py, 9, waypoint.color(),
                    waypoint.initial());
            int nameWidth = minecraft.font.width(waypoint.name());
            int nameX = px - nameWidth / 2;
            int nameY = py + 7;
            g.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 8, 0x99030914);
            g.text(minecraft.font, waypoint.name(), nameX, nameY, 0xFFFFFFFF);
        }
    }

    private static void drawPlayers(GuiGraphicsExtractor g, Minecraft minecraft, View view, Player self) {
        boolean heads = MinimapHeads.shown(minecraft);
        for (Player other : minecraft.level.players()) {
            if (other == self) {
                continue;
            }
            double sx = view.screenX(other.getX());
            double sy = view.screenY(other.getZ());
            if (!view.contains(sx, sy)) {
                continue;
            }
            MinimapRenderer.drawPlayerMarker(g, minecraft, other, (int) Math.round(sx), (int) Math.round(sy),
                    MinimapRenderer.verticalAlpha(other.getY(), self.getY()), heads);
        }
    }
}
