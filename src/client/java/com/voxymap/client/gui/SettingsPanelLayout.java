package com.voxymap.client.gui;

/** Pure sizing and scrolling policy for the custom in-map settings panel. */
final class SettingsPanelLayout {
    private static final int MIN_PANEL_HEIGHT = 96;

    private SettingsPanelLayout() {
    }

    static Result calculate(int naturalHeight, int footerTop, int margin, int gap, int requestedScroll) {
        int availableHeight = Math.max(MIN_PANEL_HEIGHT, footerTop - margin - gap);
        int panelHeight = Math.min(naturalHeight, availableHeight);
        int maxScroll = Math.max(0, naturalHeight - panelHeight);
        int scroll = Math.max(0, Math.min(maxScroll, requestedScroll));
        return new Result(panelHeight, maxScroll, scroll);
    }

    static boolean rowVisible(int rowY, int panelY, int panelHeight, int rowHeight) {
        int contentTop = panelY + 28;
        int contentBottom = panelY + panelHeight - 6;
        return rowY >= contentTop && rowY + rowHeight - 2 <= contentBottom;
    }

    record Result(int panelHeight, int maxScroll, int scroll) {
    }
}
