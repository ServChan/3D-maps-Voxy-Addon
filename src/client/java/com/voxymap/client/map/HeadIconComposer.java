package com.voxymap.client.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class HeadIconComposer {
    static final int MAX_SIZE = 48;
    static final int MIN_OPAQUE_ALPHA = 128;
    private static final double EDGE_TOLERANCE = 1.0e-4;

    record Quad(float[] xs, float[] ys, float[] us, float[] vs, float depth) {
        double edgeX(int to) {
            return xs[to] - xs[0];
        }

        double edgeY(int to) {
            return ys[to] - ys[0];
        }

        double determinant() {
            return edgeX(1) * edgeY(3) - edgeY(1) * edgeX(3);
        }
    }

    record Icon(int width, int height, int[] argb) {
    }

    private HeadIconComposer() {
    }

    static Icon compose(List<Quad> quads, int textureWidth, int textureHeight, int[] texture) {
        if (quads.isEmpty() || textureWidth <= 0 || textureHeight <= 0) {
            return null;
        }
        double texelsPerUnit = texelsPerUnit(quads.getFirst(), textureWidth, textureHeight);
        if (!(texelsPerUnit > 0.0) || !Double.isFinite(texelsPerUnit)) {
            return null;
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Quad quad : quads) {
            for (int i = 0; i < 4; i++) {
                minX = Math.min(minX, quad.xs()[i]);
                minY = Math.min(minY, quad.ys()[i]);
                maxX = Math.max(maxX, quad.xs()[i]);
                maxY = Math.max(maxY, quad.ys()[i]);
            }
        }
        int width = clampSize((int) Math.round((maxX - minX) * texelsPerUnit));
        int height = clampSize((int) Math.round((maxY - minY) * texelsPerUnit));
        double scaleX = width / (double) (maxX - minX);
        double scaleY = height / (double) (maxY - minY);

        List<Quad> backToFront = new ArrayList<>(quads);
        backToFront.sort(Comparator.comparingDouble(Quad::depth).reversed());
        int[] out = new int[width * height];
        boolean any = false;
        for (Quad quad : backToFront) {
            double determinant = quad.determinant();
            if (Math.abs(determinant) < 1.0e-9) {
                continue;
            }
            for (int py = 0; py < height; py++) {
                double y = (py + 0.5) / scaleY + minY - quad.ys()[0];
                for (int px = 0; px < width; px++) {
                    double x = (px + 0.5) / scaleX + minX - quad.xs()[0];
                    double s = (x * quad.edgeY(3) - y * quad.edgeX(3)) / determinant;
                    double t = (quad.edgeX(1) * y - quad.edgeY(1) * x) / determinant;
                    if (s < -EDGE_TOLERANCE || s > 1.0 + EDGE_TOLERANCE || t < -EDGE_TOLERANCE || t > 1.0 + EDGE_TOLERANCE) {
                        continue;
                    }
                    double u = quad.us()[0] + s * (quad.us()[1] - quad.us()[0]) + t * (quad.us()[3] - quad.us()[0]);
                    double v = quad.vs()[0] + s * (quad.vs()[1] - quad.vs()[0]) + t * (quad.vs()[3] - quad.vs()[0]);
                    int texelX = clamp((int) Math.floor(u * textureWidth), textureWidth);
                    int texelY = clamp((int) Math.floor(v * textureHeight), textureHeight);
                    int color = texture[texelY * textureWidth + texelX];
                    if ((color >>> 24) >= MIN_OPAQUE_ALPHA) {
                        out[py * width + px] = color | 0xFF000000;
                        any = true;
                    }
                }
            }
        }
        return any ? new Icon(width, height, out) : null;
    }

    static double texelsPerUnit(Quad quad, int textureWidth, int textureHeight) {
        double spatial = Math.hypot(quad.edgeX(1), quad.edgeY(1));
        double texels = Math.hypot((quad.us()[1] - quad.us()[0]) * textureWidth, (quad.vs()[1] - quad.vs()[0]) * textureHeight);
        return spatial == 0.0 ? 0.0 : texels / spatial;
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(MAX_SIZE, size));
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }
}
