package com.example.ui;

import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertTrue;

public class WindowChromeOffscreenRenderTest {

    @Test
    public void settingsOffscreenRenderHasOpaqueContent() {
        PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
        java.awt.Font main = UiFonts.chinesePlain(13);
        java.awt.Font bold = UiFonts.chineseBold(13);
        java.awt.Font field = UiFonts.latinPlain(13);
        JTabbedPane tabs = PanelFactory.createServerConfigBody(refs, main, bold, field);
        tabs.setSelectedIndex(1);
        int w = 860;
        int h = 620;
        BufferedImage image = WindowChrome.renderOffscreen(
                tabs, w, h, new Color(28, 20, 48));
        int opaque = 0;
        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                if ((image.getRGB(x, y) & 0xFF000000) != 0) {
                    int rgb = image.getRGB(x, y) & 0xFFFFFF;
                    if (rgb != 0x1C1430) {
                        opaque++;
                    }
                }
            }
        }
        assertTrue("settings tab should render non-background pixels", opaque > 80);
    }
}