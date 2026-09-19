package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScreenTriangleTest {
    private static final int[] XS = {0, 10, 0};
    private static final int[] YS = {0, 0, 10};

    @Test
    void spansShrinkTowardsTheApex() {
        assertArrayEquals(new double[] {0.0, 9.5}, ScreenTriangle.span(0.5, XS, YS), 1e-9);
        assertArrayEquals(new double[] {0.0, 5.0}, ScreenTriangle.span(5.0, XS, YS), 1e-9);
        assertArrayEquals(new double[] {0.0, 0.5}, ScreenTriangle.span(9.5, XS, YS), 1e-9);
    }

    @Test
    void rowsOutsideTheTriangleHaveNoSpan() {
        assertNull(ScreenTriangle.span(-1.0, XS, YS));
        assertNull(ScreenTriangle.span(11.0, XS, YS));
    }

    @Test
    void aFlatTriangleHasNoSpanAndNeverDividesByZero() {
        assertNull(ScreenTriangle.span(0.0, new int[] {0, 5, 10}, new int[] {0, 0, 0}));
    }
}
