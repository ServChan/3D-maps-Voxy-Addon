package com.voxymap.client.map;

import com.voxymap.client.gui.WaypointIcons;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WaypointLabels {
    private static final int ICON_SIZE = 11;
    private static final double AIM_RADIUS_PIXELS = 48.0;
    private static final double MIN_DISTANCE_BLOCKS = 2.0;
    private static final double ANCHOR_HEIGHT_BLOCKS = 1.0;
    private static final double PRECISION_DISTANCE = 1000.0;
    private static final float SCREEN_MARGIN_NDC = 1.02f;
    private static final int LABEL_BACKGROUND = 0xC0030914;
    private static final int LABEL_HEIGHT = 22;
    private static final int MAX_LABEL_LIFTS = 3;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int DISTANCE_COLOR = 0xFFB8C7D9;

    record Projected(WaypointManager.Waypoint waypoint, float x, float y, double distance, String dimension) {
        Projected(WaypointManager.Waypoint waypoint, float x, float y, double distance) {
            this(waypoint, x, y, distance, null);
        }
    }

    private record Candidate(WaypointManager.Waypoint waypoint, String dimension) {
    }

    private WaypointLabels() {
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft) {
        VoxyMapSettings.WaypointLabelMode mode = VoxyMapSettings.waypointLabelMode();
        if (mode == VoxyMapSettings.WaypointLabelMode.OFF || !VoxyMapSettings.waypointsEnabled()
                || minecraft.level == null || minecraft.player == null || minecraft.gui.hud.isHidden()
                || VoxyMapCameraController.isActive() || MinimapRenderer.hiddenByScreen(minecraft)) {
            return;
        }
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (worldId == null) {
            return;
        }
        List<Candidate> waypoints = new ArrayList<>();
        for (DimensionLinks.Foreign foreign : DimensionLinks.foreignWaypoints(minecraft)) {
            waypoints.add(new Candidate(foreign.local(), dimensionName(foreign.source())));
        }
        for (WaypointManager.Waypoint waypoint : WaypointManager.visibleForWorld(worldId)) {
            waypoints.add(new Candidate(waypoint, null));
        }
        if (waypoints.isEmpty()) {
            return;
        }

        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int width = g.guiWidth();
        int height = g.guiHeight();
        List<Projected> projected = new ArrayList<>();
        for (Candidate candidate : waypoints) {
            WaypointManager.Waypoint waypoint = candidate.waypoint();
            double dx = waypoint.x() - cameraPos.x;
            double dy = waypoint.y() + ANCHOR_HEIGHT_BLOCKS - cameraPos.y;
            double dz = waypoint.z() - cameraPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < MIN_DISTANCE_BLOCKS) {
                continue;
            }
            double shrink = distance > PRECISION_DISTANCE ? PRECISION_DISTANCE / distance : 1.0;
            Vector4f clip = new Vector4f((float) (dx * shrink), (float) (dy * shrink), (float) (dz * shrink), 1.0f)
                    .mul(viewProjection);
            if (clip.w <= 0.05f) {
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (Math.abs(ndcX) > SCREEN_MARGIN_NDC || Math.abs(ndcY) > SCREEN_MARGIN_NDC) {
                continue;
            }
            projected.add(new Projected(waypoint, (ndcX + 1.0f) * 0.5f * width, (1.0f - ndcY) * 0.5f * height, distance,
                    candidate.dimension()));
        }
        if (projected.isEmpty()) {
            return;
        }
        projected.sort(Comparator.comparingDouble(Projected::distance).reversed());

        Projected aimed = mode == VoxyMapSettings.WaypointLabelMode.AIMING
                ? aimedAt(projected, width / 2.0, height / 2.0, AIM_RADIUS_PIXELS) : null;
        Font font = minecraft.font;
        for (Projected point : projected) {
            WaypointIcons.drawCentered(g, font, Math.round(point.x()), Math.round(point.y()), ICON_SIZE,
                    point.waypoint().color(), point.waypoint().initial(),
                    point.dimension() == null ? 0xFF : WaypointIcons.OTHER_DIMENSION_ALPHA);
        }
        if (mode == VoxyMapSettings.WaypointLabelMode.ALWAYS) {
            List<Projected> byPriority = new ArrayList<>(projected);
            byPriority.sort(Comparator.comparingDouble(Projected::distance));
            List<Box> placed = new ArrayList<>();
            for (Projected point : byPriority) {
                Box wanted = labelBox(font, point, width);
                Integer lift = liftToFit(placed, wanted, LABEL_HEIGHT + 2, MAX_LABEL_LIFTS);
                if (lift == null) {
                    continue;
                }
                Box box = wanted.lifted(lift);
                placed.add(box);
                drawLabel(g, font, point, box);
            }
        }
        if (aimed != null) {
            drawLabel(g, font, aimed, labelBox(font, aimed, width));
        }
    }

    static Projected aimedAt(List<Projected> projected, double centerX, double centerY, double radius) {
        Projected best = null;
        double bestDistance = radius * radius;
        for (Projected point : projected) {
            double dx = point.x() - centerX;
            double dy = point.y() - centerY;
            double screenDistance = dx * dx + dy * dy;
            if (screenDistance <= bestDistance) {
                bestDistance = screenDistance;
                best = point;
            }
        }
        return best;
    }

    record Box(int left, int top, int right, int bottom) {
        boolean overlaps(Box other) {
            return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
        }

        Box lifted(int amount) {
            return new Box(left, top - amount, right, bottom - amount);
        }
    }

    static Integer liftToFit(List<Box> placed, Box wanted, int step, int maxLifts) {
        for (int attempt = 0; attempt <= maxLifts; attempt++) {
            Box candidate = wanted.lifted(attempt * step);
            boolean free = true;
            for (Box box : placed) {
                if (box.overlaps(candidate)) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return attempt * step;
            }
        }
        return null;
    }

    private static String distanceLine(Projected point) {
        return point.dimension() == null ? distanceText(point.distance())
                : distanceText(point.distance()) + " \u00b7 " + point.dimension();
    }

    private static Box labelBox(Font font, Projected point, int screenWidth) {
        int textWidth = Math.max(font.width(point.waypoint().name()), font.width(distanceLine(point)));
        int boxWidth = textWidth + 8;
        int centerX = Math.round(point.x());
        int left = Math.max(2, Math.min(screenWidth - boxWidth - 2, centerX - boxWidth / 2));
        int bottom = Math.round(point.y()) - ICON_SIZE / 2 - 3;
        return new Box(left, bottom - LABEL_HEIGHT, left + boxWidth, bottom);
    }

    private static void drawLabel(GuiGraphicsExtractor g, Font font, Projected point, Box box) {
        int centerX = (box.left() + box.right()) / 2;
        g.fill(box.left(), box.top(), box.right(), box.bottom(), LABEL_BACKGROUND);
        g.fill(box.left(), box.bottom() - 1, box.right(), box.bottom(), 0xFF000000 | point.waypoint().color());
        g.centeredText(font, point.waypoint().name(), centerX, box.top() + 3, NAME_COLOR);
        g.centeredText(font, distanceLine(point), centerX, box.top() + 12, DISTANCE_COLOR);
    }

    public static String dimensionName(WaypointText.Dimension dimension) {
        return Component.translatable(switch (dimension) {
            case NETHER -> "screen.voxymap.waypoints.tag.from_nether";
            case END -> "screen.voxymap.waypoints.tag.from_end";
            case OVERWORLD -> "screen.voxymap.waypoints.tag.from_overworld";
        }).getString();
    }

    public static String distanceText(double blocks) {
        return WaypointText.useKilometres(blocks)
                ? Component.translatable("overlay.voxymap.waypoint.distance_km", WaypointText.kilometres(blocks)).getString()
                : Component.translatable("screen.voxymap.waypoints.distance", Math.round(blocks)).getString();
    }
}
