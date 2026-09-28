package com.voxymap.client.gui;

final class ScreenTriangle {
    private ScreenTriangle() {
    }

    static double[] span(double scanY, int[] xs, int[] ys) {
        double left = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            int j = (i + 1) % 3;
            if (ys[i] == ys[j] || scanY < Math.min(ys[i], ys[j]) || scanY > Math.max(ys[i], ys[j])) {
                continue;
            }
            double x = xs[i] + (scanY - ys[i]) * (xs[j] - xs[i]) / (double) (ys[j] - ys[i]);
            left = Math.min(left, x);
            right = Math.max(right, x);
        }
        return left > right ? null : new double[] {left, right};
    }
}
