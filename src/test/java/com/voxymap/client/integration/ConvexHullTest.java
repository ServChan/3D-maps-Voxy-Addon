package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvexHullTest {
    @Test
    void squareWithAnInteriorPointDropsTheInteriorPoint() {
        List<double[]> points = List.of(
                new double[]{0, 0}, new double[]{10, 0}, new double[]{10, 10}, new double[]{0, 10},
                new double[]{5, 5});

        List<double[]> hull = ConvexHull.compute(points);

        assertEquals(4, hull.size(), "the interior point must not appear in the hull");
        for (double[] corner : List.of(new double[]{0, 0}, new double[]{10, 0}, new double[]{10, 10}, new double[]{0, 10})) {
            assertTrue(containsPoint(hull, corner), "hull missing corner " + corner[0] + "," + corner[1]);
        }
    }

    @Test
    void fewerThanThreePointsReturnsThemUnchanged() {
        assertEquals(0, ConvexHull.compute(List.of()).size());
        assertEquals(1, ConvexHull.compute(List.of(new double[]{1, 2})).size());
        assertEquals(2, ConvexHull.compute(List.of(new double[]{1, 2}, new double[]{3, 4})).size());
    }

    @Test
    void collinearPointsDoNotProduceADegenerateBlowup() {
        List<double[]> points = List.of(
                new double[]{0, 0}, new double[]{1, 0}, new double[]{2, 0}, new double[]{3, 0});

        List<double[]> hull = ConvexHull.compute(points);

        assertTrue(hull.size() <= 4);
    }

    @Test
    void duplicatePointsAreNotRepeatedInTheHull() {
        List<double[]> points = List.of(
                new double[]{0, 0}, new double[]{0, 0}, new double[]{10, 0}, new double[]{5, 10});

        List<double[]> hull = ConvexHull.compute(points);

        assertEquals(3, hull.size());
    }

    private static boolean containsPoint(List<double[]> points, double[] target) {
        for (double[] point : points) {
            if (point[0] == target[0] && point[1] == target[1]) {
                return true;
            }
        }
        return false;
    }
}
