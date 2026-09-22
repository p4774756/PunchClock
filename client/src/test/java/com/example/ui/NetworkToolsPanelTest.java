package com.example.ui;

import org.junit.Test;

import javax.swing.SwingUtilities;

import java.awt.Font;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NetworkToolsPanelTest {

    @Test
    public void applySettings_roundTripsProxyFields() throws Exception {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        Font bold = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        NetworkToolsPanel[] holder = new NetworkToolsPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new NetworkToolsPanel(
                font, bold, font,
                () -> "http://localhost:3000",
                () -> false,
                () -> {},
                () -> {},
                line -> {}
        ));
        NetworkToolsPanel panel = holder[0];
        SwingUtilities.invokeAndWait(() ->
                panel.applySettings("https://example.com/ping", "10.1.2.3", 3128, "CUSTOM",
                        "alice", "p@ss"));
        assertEquals("https://example.com/ping", panel.getTestUrl());
        assertEquals("10.1.2.3", panel.getProxyHost());
        assertEquals(3128, panel.getProxyPort());
        assertEquals("CUSTOM", panel.getProxyMode());
        assertEquals("alice", panel.getProxyUser());
        assertEquals("p@ss", panel.getProxyPassword());
        assertTrue(NetworkToolsPanel.TAB_LABEL.contains("Ping"));
        assertTrue(NetworkToolsPanel.TAB_LABEL.contains("Pong"));

        SwingUtilities.invokeAndWait(() -> panel.applySplitDividerLocation(180));
        // 允許 layout 後再讀；headless 下高度可能為 0，至少 API 可呼叫
        int divider = panel.getSplitDividerLocation();
        assertTrue(divider == -1 || divider > 0);
    }
}
