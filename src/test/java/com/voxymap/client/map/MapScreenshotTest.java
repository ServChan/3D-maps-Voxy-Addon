package com.voxymap.client.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapScreenshotTest {
    @BeforeEach
    void reset() {
        MapScreenshot.resetForTest();
    }

    @Test
    void screenshotOutsideTheMapIsLeftToVanilla() {
        assertFalse(MapScreenshot.begin(false));
        assertFalse(MapScreenshot.afterGuiExtracted(false));
        assertFalse(MapScreenshot.takeDueCapture(false));
    }

    @Test
    void hidesTheInterfaceForExactlyOneFrameThenCapturesIt() {
        assertTrue(MapScreenshot.begin(true), "vanilla's screenshot must be replaced on the map");

        assertFalse(MapScreenshot.takeDueCapture(true), "nothing is rendered yet, so nothing to save");
        assertTrue(MapScreenshot.afterGuiExtracted(true), "the next frame's interface is dropped");
        assertTrue(MapScreenshot.takeDueCapture(true), "the clean frame is saved at the next tick");

        assertFalse(MapScreenshot.afterGuiExtracted(true), "the interface comes back afterwards");
        assertFalse(MapScreenshot.takeDueCapture(true), "and is saved only once");
    }

    @Test
    void repeatedKeyPressesDoNotStackCaptures() {
        assertTrue(MapScreenshot.begin(true));
        assertTrue(MapScreenshot.begin(true), "a second press is swallowed, not passed to vanilla");
        assertTrue(MapScreenshot.afterGuiExtracted(true));
        assertTrue(MapScreenshot.begin(true));
        assertTrue(MapScreenshot.takeDueCapture(true));
        assertFalse(MapScreenshot.takeDueCapture(true));
    }

    @Test
    void closingTheMapMidSequenceNeverBlanksTheNormalInterface() {
        assertTrue(MapScreenshot.begin(true));

        assertFalse(MapScreenshot.afterGuiExtracted(false));
        assertFalse(MapScreenshot.afterGuiExtracted(true), "the sequence was cancelled, not merely paused");
        assertFalse(MapScreenshot.takeDueCapture(true));
    }

    @Test
    void closingTheMapAfterTheCleanFrameSkipsTheSave() {
        assertTrue(MapScreenshot.begin(true));
        assertTrue(MapScreenshot.afterGuiExtracted(true));

        assertFalse(MapScreenshot.takeDueCapture(false), "the frame no longer shows the map");
        assertFalse(MapScreenshot.takeDueCapture(true));
    }
}
