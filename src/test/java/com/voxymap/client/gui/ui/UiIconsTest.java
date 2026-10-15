package com.voxymap.client.gui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiIconsTest {
    @Test
    void everyIconIsANineByNineBitmapWithPixels() {
        for (UiIcons.Icon icon : UiIcons.Icon.values()) {
            int[][] runs = icon.runs();
            assertTrue(runs.length > 0, icon.name());
            for (int[] run : runs) {
                assertTrue(run[0] >= 0 && run[0] + run[2] <= UiTheme.ICON_SIZE, icon.name());
                assertTrue(run[1] >= 0 && run[1] < UiTheme.ICON_SIZE, icon.name());
            }
        }
    }

    @Test
    void runsMergeNeighbouringPixels() {
        String[] rows = {"##.##....", ".........", ".........", ".........", ".........", ".........",
                ".........", ".........", "#########"};
        int[][] runs = UiIcons.encode(rows);
        assertArrayEquals(new int[]{0, 0, 2}, runs[0]);
        assertArrayEquals(new int[]{3, 0, 2}, runs[1]);
        assertArrayEquals(new int[]{0, 8, 9}, runs[2]);
    }

    @Test
    void malformedBitmapsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> UiIcons.encode(new String[]{"#"}));
    }
}
