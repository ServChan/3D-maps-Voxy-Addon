package com.voxymap.client.map;

import com.voxymap.client.gui.WaypointIcons;
import com.voxymap.client.integration.VanillaSquadFlagMapBridge;
import com.voxymap.client.integration.VanillaSquadWebMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WebMapRenderer {
    static final int MAX_VISIBLE_TILES = 160;
    private static final int MAX_TILE_SPAN = 24;
    private static final int LABEL_BACKGROUND = 0x99030914;
    private static final int SPAWN_COLOR = 0xFFFFD84D;
    private static final int OUTLINE = 0xEE0A0A12;

    private WebMapRenderer() {
    }

    public record Overlays(boolean currentWorld, String linkedWorldId) {
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view,
                               VanillaSquadWebMap.World world, Overlays overlays) {
        WebMapTiles.tick(minecraft);
        g.enableScissor(view.left(), view.top(), view.left() + view.width(), view.top() + view.height());
        drawTiles(g, minecraft, view, world);
        FlatMapRenderer.drawChunkGridIfEnabled(g, view);
        drawTerritories(g, minecraft, view, world);
        drawSpawn(g, minecraft, view, world);
        drawWaypoints(g, minecraft, view, overlays.linkedWorldId());
        if (overlays.currentWorld()) {
            drawPlayers(g, minecraft, view);
        }
        g.disableScissor();
    }

    private static void drawTiles(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view,
                                  VanillaSquadWebMap.World world) {
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        int maxZoom = world.maxZoom();
        int zoom = WebMapGeometry.zoomFor(1.0 / (view.pixelsPerBlock() * guiScale), maxZoom);
        if (zoom > 0) {
            for (int[] tile : visibleTiles(view, 0, maxZoom)) {
                requestTile(world, 0, tile[0], tile[1]);
            }
        }
        int tileBlocks = WebMapGeometry.tileBlocks(zoom, maxZoom);
        for (int[] tile : visibleTiles(view, zoom, maxZoom)) {
            int tileX = tile[0];
            int tileZ = tile[1];
            int x0 = WebMapGeometry.screenCoordinate(view.screenX((double) tileX * tileBlocks));
            int x1 = WebMapGeometry.screenCoordinate(view.screenX((double) (tileX + 1) * tileBlocks));
            int y0 = WebMapGeometry.screenCoordinate(view.screenY((double) tileZ * tileBlocks));
            int y1 = WebMapGeometry.screenCoordinate(view.screenY((double) (tileZ + 1) * tileBlocks));
            if (x1 <= x0 || y1 <= y0) {
                continue;
            }
            WebMapTiles.TileKey key = new WebMapTiles.TileKey(world.mapId(), world.name(), zoom, tileX, tileZ);
            Identifier texture = WebMapTiles.texture(key);
            if (texture != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0.0f, 0.0f, x1 - x0, y1 - y0,
                        WebMapGeometry.TILE_PIXELS, WebMapGeometry.TILE_PIXELS,
                        WebMapGeometry.TILE_PIXELS, WebMapGeometry.TILE_PIXELS, 0xFFFFFFFF);
                continue;
            }
            requestTile(world, zoom, tileX, tileZ);
            for (int up = 1; up <= zoom; up++) {
                int parentX = tileX >> up;
                int parentZ = tileZ >> up;
                Identifier parent = WebMapTiles.texture(new WebMapTiles.TileKey(world.mapId(), world.name(),
                        zoom - up, parentX, parentZ));
                if (parent == null) {
                    continue;
                }
                int part = WebMapGeometry.TILE_PIXELS >> up;
                float u = (tileX - (parentX << up)) * (float) part;
                float v = (tileZ - (parentZ << up)) * (float) part;
                g.blit(RenderPipelines.GUI_TEXTURED, parent, x0, y0, u, v, x1 - x0, y1 - y0, part, part,
                        WebMapGeometry.TILE_PIXELS, WebMapGeometry.TILE_PIXELS, 0xFFFFFFFF);
                break;
            }
        }
    }

    private static void requestTile(VanillaSquadWebMap.World world, int zoom, int tileX, int tileZ) {
        WebMapTiles.request(new WebMapTiles.TileKey(world.mapId(), world.name(), zoom, tileX, tileZ),
                world.tileUrl(zoom, tileX, tileZ));
    }

    private static List<int[]> visibleTiles(FlatMapRenderer.View view, int zoom, int maxZoom) {
        int tileBlocks = WebMapGeometry.tileBlocks(zoom, maxZoom);
        int minX = WebMapGeometry.tileIndex(view.worldX(view.left()), tileBlocks);
        int maxX = WebMapGeometry.tileIndex(view.worldX(view.left() + view.width()), tileBlocks);
        int minZ = WebMapGeometry.tileIndex(view.worldZ(view.top()), tileBlocks);
        int maxZ = WebMapGeometry.tileIndex(view.worldZ(view.top() + view.height()), tileBlocks);
        double centerX = view.centerX() / tileBlocks;
        double centerZ = view.centerZ() / tileBlocks;
        int centerTileX = (int) Math.floor(centerX);
        int centerTileZ = (int) Math.floor(centerZ);
        minX = Math.max(minX, centerTileX - MAX_TILE_SPAN);
        maxX = Math.min(maxX, centerTileX + MAX_TILE_SPAN);
        minZ = Math.max(minZ, centerTileZ - MAX_TILE_SPAN);
        maxZ = Math.min(maxZ, centerTileZ + MAX_TILE_SPAN);
        List<int[]> tiles = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                tiles.add(new int[]{x, z});
            }
        }
        tiles.sort(Comparator.comparingDouble(tile -> square(tile[0] + 0.5 - centerX) + square(tile[1] + 0.5 - centerZ)));
        return tiles.size() > MAX_VISIBLE_TILES ? tiles.subList(0, MAX_VISIBLE_TILES) : tiles;
    }

    private static void drawTerritories(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view,
                                        VanillaSquadWebMap.World world) {
        if (!VoxyMapSettings.vanillaSquadFlagsActive()) {
            return;
        }
        VanillaSquadFlagMapBridge.tick(world.mapId(), world.dimensionId());
        for (VanillaSquadFlagMapBridge.Territory territory
                : VanillaSquadFlagMapBridge.territoriesInDimension(world.mapId(), world.dimensionId())) {
            List<double[]> outline = territory.outline();
            if (outline.size() == 1) {
                double[] point = outline.getFirst();
                drawDiamond(g, WebMapGeometry.screenCoordinate(view.screenX(point[0])),
                        WebMapGeometry.screenCoordinate(view.screenY(point[1])), 4, territory.color());
            } else {
                for (int i = 0; i < outline.size(); i++) {
                    double[] a = outline.get(i);
                    double[] b = outline.get((i + 1) % outline.size());
                    drawDashedLine(g, view, view.screenX(a[0]), view.screenY(a[1]), view.screenX(b[0]),
                            view.screenY(b[1]), territory.color());
                }
            }
            drawLabel(g, minecraft, view, view.screenX(territory.centroidX()), view.screenY(territory.centroidZ()),
                    territory.name(), territory.color());
        }
    }

    private static void drawSpawn(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view,
                                  VanillaSquadWebMap.World world) {
        double sx = view.screenX(world.spawnX() + 0.5);
        double sy = view.screenY(world.spawnZ() + 0.5);
        if (!view.contains(sx, sy)) {
            return;
        }
        int px = WebMapGeometry.screenCoordinate(sx);
        int py = WebMapGeometry.screenCoordinate(sy);
        drawDiamond(g, px, py, 6, OUTLINE);
        drawDiamond(g, px, py, 4, SPAWN_COLOR);
        drawLabel(g, minecraft, view, sx, sy + 14, Component.translatable("screen.voxymap.webmap.spawn").getString(),
                SPAWN_COLOR);
    }

    private static void drawWaypoints(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view,
                                      String worldId) {
        if (worldId == null || !VoxyMapSettings.waypointsEnabled()) {
            return;
        }
        for (WaypointManager.Waypoint waypoint : WaypointManager.visibleForWorld(worldId, WaypointDisplay.Surface.MAP)) {
            double sx = view.screenX(waypoint.x());
            double sy = view.screenY(waypoint.z());
            if (!view.contains(sx, sy)) {
                continue;
            }
            int px = WebMapGeometry.screenCoordinate(sx);
            int py = WebMapGeometry.screenCoordinate(sy);
            WaypointIcons.drawCentered(g, minecraft.font, px, py, 9, waypoint.color(), waypoint.initial());
            int nameWidth = minecraft.font.width(waypoint.name());
            int nameX = px - nameWidth / 2;
            int nameY = py + 7;
            g.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 8, LABEL_BACKGROUND);
            g.text(minecraft.font, waypoint.name(), nameX, nameY, 0xFFFFFFFF);
        }
    }

    private static void drawPlayers(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view) {
        Player self = minecraft.player;
        if (self == null || minecraft.level == null) {
            return;
        }
        boolean heads = MinimapHeads.shown(minecraft);
        for (Player other : minecraft.level.players()) {
            if (other == self || !ServerRules.radarAllowed()) {
                continue;
            }
            double sx = view.screenX(other.getX());
            double sy = view.screenY(other.getZ());
            if (view.contains(sx, sy)) {
                MinimapRenderer.drawPlayerMarker(g, minecraft, other, WebMapGeometry.screenCoordinate(sx),
                        WebMapGeometry.screenCoordinate(sy), MinimapRenderer.verticalAlpha(other.getY(), self.getY()), heads);
            }
        }
        double sx = view.screenX(self.getX());
        double sy = view.screenY(self.getZ());
        if (view.contains(sx, sy)) {
            int px = WebMapGeometry.screenCoordinate(sx);
            int py = WebMapGeometry.screenCoordinate(sy);
            MinimapRenderer.drawFacingIndicator(g, px, py, self.getYRot());
            MinimapRenderer.drawDot(g, px, py, 3, 0xFF0A0A12);
            MinimapRenderer.drawDot(g, px, py, 2, 0xFF55E7FF);
        }
    }

    private static void drawLabel(GuiGraphicsExtractor g, Minecraft minecraft, FlatMapRenderer.View view,
                                  double centerX, double centerY, String text, int accent) {
        if (text == null || text.isEmpty() || !view.contains(centerX, centerY)) {
            return;
        }
        int width = minecraft.font.width(text) + 8;
        int x = WebMapGeometry.screenCoordinate(centerX) - width / 2;
        int y = WebMapGeometry.screenCoordinate(centerY) - 5;
        g.fill(x, y, x + width, y + 11, LABEL_BACKGROUND);
        g.fill(x, y + 10, x + width, y + 11, accent);
        g.text(minecraft.font, text, x + 4, y + 2, 0xFFE9F2FF);
    }

    private static void drawDashedLine(GuiGraphicsExtractor g, FlatMapRenderer.View view, double x1, double y1,
                                       double x2, double y2, int color) {
        double[] clipped = WebMapGeometry.clipLine(x1, y1, x2, y2, view.left() - 2.0, view.top() - 2.0,
                view.left() + view.width() + 2.0, view.top() + view.height() + 2.0);
        if (clipped == null) {
            return;
        }
        double dx = clipped[2] - clipped[0];
        double dy = clipped[3] - clipped[1];
        int steps = Math.max(1, (int) (Math.hypot(dx, dy) / 3.0));
        for (int i = 0; i <= steps; i += 2) {
            double t = i / (double) steps;
            int px = WebMapGeometry.screenCoordinate(clipped[0] + dx * t);
            int py = WebMapGeometry.screenCoordinate(clipped[1] + dy * t);
            g.fill(px, py, px + 2, py + 2, color);
        }
    }

    private static void drawDiamond(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = radius - Math.abs(row);
            g.fill(centerX - halfWidth, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private static double square(double value) {
        return value * value;
    }
}
