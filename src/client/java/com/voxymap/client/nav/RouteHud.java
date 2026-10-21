package com.voxymap.client.nav;

import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiTheme;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class RouteHud {
    static final double LOOKAHEAD_GROUND = 24.0;
    static final double LOOKAHEAD_FLIGHT = 80.0;
    static final double MARKER_LIFT = 1.2;
    static final double PRECISION_DISTANCE = 1000.0;
    private static final int TOP = 6;
    private static final int PAD = 6;
    private static final int HEIGHT = 30;
    private static final int ARROW_BOX = 16;
    private static final int MARKER_RADIUS = 4;

    private RouteHud() {
    }

    public static int color(Route.Point point) {
        if (point.unknown()) {
            return UiTheme.ROUTE_UNKNOWN;
        }
        return switch (point.mode()) {
            case WALK -> UiTheme.ROUTE_WALK;
            case SWIM -> UiTheme.ROUTE_SWIM;
            case BOAT -> UiTheme.ROUTE_BOAT;
            case ELYTRA -> UiTheme.ROUTE_ELYTRA;
        };
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft) {
        Navigator.Target target = Navigator.target();
        LocalPlayer player = minecraft.player;
        if (target == null || player == null || minecraft.gui.hud.isHidden() || VoxyMapCameraController.isActive()
                || MinimapRenderer.hiddenByScreen(minecraft)) {
            return;
        }
        Route route = Navigator.route();
        RouteProgress.State state = Navigator.progress();
        Font font = minecraft.font;
        String title;
        String detail;
        int accent;
        double[] ahead = null;
        if (route == null || state == null) {
            title = Component.translatable("hud.voxymap.route.target", target.name()).getString();
            detail = Component.translatable("hud.voxymap.route.planning").getString();
            accent = UiTheme.TEXT_MUTED;
        } else {
            Route.Point current = route.points().get(state.segment());
            accent = color(current);
            title = Component.translatable("hud.voxymap.route.title", RouteText.mode(current.mode()), target.name(),
                    RouteText.distance(state.remaining()), RouteText.duration(state.remainingSeconds())).getString();
            detail = detail(route, state).getString();
            ahead = RouteProgress.pointAhead(route, state.segment(), state.along(),
                    route.flight() ? LOOKAHEAD_FLIGHT : LOOKAHEAD_GROUND);
        }
        int textWidth = Math.max(font.width(title), font.width(detail));
        int width = Math.min(g.guiWidth() - 16, textWidth + PAD * 2 + ARROW_BOX + PAD);
        int x = (g.guiWidth() - width) / 2;
        UiDraw.card(g, x, TOP, width, HEIGHT);
        int textX = x + PAD + ARROW_BOX + PAD;
        int textMax = width - (textX - x) - PAD;
        UiDraw.text(g, font, title, textX, TOP + 5, textMax, UiTheme.TEXT);
        UiDraw.text(g, font, detail, textX, TOP + 17, textMax, UiTheme.TEXT_SECONDARY);
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double aimX = ahead != null ? ahead[0] : target.x();
        double aimZ = ahead != null ? ahead[2] : target.z();
        double px = Mth.lerp(partial, player.xo, player.getX());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        float angle = (float) screenAngle(aimX - px, aimZ - pz, player.getViewYRot(partial));
        arrow(g, x + PAD + ARROW_BOX / 2, TOP + HEIGHT / 2, angle, accent);
        if (ahead != null) {
            marker(g, minecraft, ahead, accent);
        }
    }

    static Component detail(Route route, RouteProgress.State state) {
        if (Navigator.status() == Navigator.Status.PLANNING) {
            return Component.translatable("hud.voxymap.route.planning");
        }
        if (route.flight()) {
            Capabilities caps = Navigator.capabilities();
            if (!caps.elytraWorn()) {
                return Component.translatable("hud.voxymap.route.elytra.wear");
            }
            return Component.translatable("hud.voxymap.route.elytra.rockets", Integer.toString(route.cruiseY()),
                    Integer.toString(route.rocketsNeeded()), Integer.toString(caps.rockets()));
        }
        if (route.direct()) {
            return Component.translatable("hud.voxymap.route.direct");
        }
        RouteProgress.Transition next = state.next();
        if (next == null) {
            return Component.translatable("hud.voxymap.route.follow");
        }
        Component distance = RouteText.distance(next.distance());
        if (next.unknown()) {
            return Component.translatable("hud.voxymap.route.next.unknown", distance);
        }
        return Component.translatable("hud.voxymap.route.next." + next.to().key(), distance);
    }

    static double screenAngle(double dx, double dz, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
        return Math.atan2(right, forward);
    }

    private static void arrow(GuiGraphicsExtractor g, int centerX, int centerY, float angle, int color) {
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().rotate(angle);
        for (int row = 0; row < 5; row++) {
            g.fill(-row - 1, -7 + row, row + 2, -6 + row, UiTheme.ROUTE_CASING);
        }
        for (int row = 0; row < 5; row++) {
            g.fill(-row, -6 + row, row + 1, -5 + row, color);
        }
        g.fill(-2, -2, 3, 7, UiTheme.ROUTE_CASING);
        g.fill(-1, -2, 2, 6, color);
        g.pose().popMatrix();
    }

    private static void marker(GuiGraphicsExtractor g, Minecraft minecraft, double[] point, int color) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        double dx = point[0] - cameraPos.x;
        double dy = point[1] + MARKER_LIFT - cameraPos.y;
        double dz = point[2] - cameraPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 2.0) {
            return;
        }
        double shrink = distance > PRECISION_DISTANCE ? PRECISION_DISTANCE / distance : 1.0;
        Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Vector4f clip = new Vector4f((float) (dx * shrink), (float) (dy * shrink), (float) (dz * shrink), 1.0f)
                .mul(viewProjection);
        if (clip.w <= 0.05f) {
            return;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (Math.abs(ndcX) > 1.0f || Math.abs(ndcY) > 1.0f) {
            return;
        }
        int sx = Math.round((ndcX + 1.0f) * 0.5f * g.guiWidth());
        int sy = Math.round((1.0f - ndcY) * 0.5f * g.guiHeight());
        diamond(g, sx, sy, MARKER_RADIUS + 1, UiTheme.ROUTE_CASING);
        diamond(g, sx, sy, MARKER_RADIUS, color);
    }

    private static void diamond(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            g.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
        }
    }
}
