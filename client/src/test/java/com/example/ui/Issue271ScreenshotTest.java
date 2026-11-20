package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * headless 截圖供 #271 驗收。輸出不進版控。
 */
public class Issue271ScreenshotTest {

    private static final Path OUT_DIR = Paths.get(
            System.getProperty("user.home"), "PunchClock-oki", "shots271-self");

    @Test
    public void writeHomeAndSettingsAppearancePreviews() throws Exception {
        Files.createDirectories(OUT_DIR);
        Path home = OUT_DIR.resolve("home-default-1280x920.png");
        assertTrue(ImageIO.write(WindowChrome.renderHomePreview(1280, 920), "png", home.toFile()));
        assertTrue(Files.size(home) > 10_000L);

        PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
        java.awt.Font main = UiFonts.chinesePlain(13);
        java.awt.Font bold = UiFonts.chineseBold(13);
        java.awt.Font field = UiFonts.latinPlain(13);
        JTabbedPane tabs = PanelFactory.createServerConfigBody(refs, main, bold, field);
        tabs.setSelectedIndex(1);
        int w = 860;
        int h = 620;
        tabs.setSize(w, h);
        tabs.doLayout();
        BufferedImage settings = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = settings.createGraphics();
        try {
            g.setColor(new Color(28, 20, 48));
            g.fillRect(0, 0, w, h);
            tabs.paint(g);
        } finally {
            g.dispose();
        }
        Path appearance = OUT_DIR.resolve("settings-appearance-860x620.png");
        assertTrue(ImageIO.write(settings, "png", appearance.toFile()));
        assertTrue(Files.size(appearance) > 3_000L);
    }
}