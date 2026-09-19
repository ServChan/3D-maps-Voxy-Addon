package com.voxymap.client.gui;

/** Scanline geometry for filling a screen-space triangle one row at a time. */
final class ScreenTriangle {
    private ScreenTriangle() {
    }

    /**
     * The horizontal extent of the triangle {@code (xs[i], ys[i])} on the row at {@code scanY}.
     *
     * @return {@code {left, right}}, or null when the row misses the triangle or it is degenerate there
     */
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
