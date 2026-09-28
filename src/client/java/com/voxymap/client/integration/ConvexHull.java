package com.voxymap.client.integration;

import java.util.ArrayList;
import java.util.List;

final class ConvexHull {
    private ConvexHull() {
    }

    static List<double[]> compute(List<double[]> input) {
        List<double[]> points = new ArrayList<>(input);
        points.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        dedupeAdjacent(points);

        int n = points.size();
        if (n < 3) {
            return points;
        }

        double[][] hull = new double[2 * n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(hull[k - 2], hull[k - 1], points.get(i)) <= 0) {
                k--;
            }
            hull[k++] = points.get(i);
        }
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            while (k >= lower && cross(hull[k - 2], hull[k - 1], points.get(i)) <= 0) {
                k--;
            }
            hull[k++] = points.get(i);
        }

        List<double[]> result = new ArrayList<>(k - 1);
        for (int i = 0; i < k - 1; i++) {
            result.add(hull[i]);
        }
        return result;
    }

    private static void dedupeAdjacent(List<double[]> sortedPoints) {
        for (int i = sortedPoints.size() - 1; i > 0; i--) {
            double[] a = sortedPoints.get(i);
            double[] b = sortedPoints.get(i - 1);
            if (a[0] == b[0] && a[1] == b[1]) {
                sortedPoints.remove(i);
            }
        }
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }
}
