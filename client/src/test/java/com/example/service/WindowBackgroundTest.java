package com.example.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class WindowBackgroundTest {

    private Path tempDir;
    private Path backgroundFile;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("punchclock-bg-test");
        backgroundFile = tempDir.resolve("background.jpg");
    }

    @After
    public void tearDown() throws Exception {
        if (tempDir != null && Files.isDirectory(tempDir)) {
            try (java.util.stream.Stream<Path> stream = Files.list(tempDir)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                });
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void fileBeside_usesConfigParent() {
        Path config = tempDir.resolve("config.json");
        assertEquals(tempDir.resolve(WindowBackground.FILE_NAME), WindowBackground.fileBeside(config));
    }

    @Test
    public void importAndLoad_roundTrip() throws Exception {
        Path source = tempDir.resolve("source.png");
        BufferedImage original = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 120, 80);
        g.dispose();
        ImageIO.write(original, "png", source.toFile());

        BufferedImage imported = WindowBackground.importImage(source, backgroundFile);
        assertNotNull(imported);
        assertTrue(Files.isRegularFile(backgroundFile));

        BufferedImage loaded = WindowBackground.load(backgroundFile);
        assertNotNull(loaded);
        assertEquals(imported.getWidth(), loaded.getWidth());
        assertEquals(imported.getHeight(), loaded.getHeight());
    }

    @Test
    public void downscaleIfNeeded_shrinksLongEdge() {
        BufferedImage large = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB);
        BufferedImage scaled = WindowBackground.downscaleIfNeeded(large, 1920);
        assertTrue(scaled.getWidth() <= 1920);
        assertTrue(scaled.getHeight() <= 1920);
        assertEquals(1920, scaled.getWidth());
    }

    @Test
    public void previewIcon_nullShowsPlaceholder() {
        assertNotNull(WindowBackground.previewIcon(null));
    }

    @Test
    public void blurRadiusForPercent_mapsRange() {
        assertEquals(0, WindowBackground.blurRadiusForPercent(0));
        assertTrue(WindowBackground.blurRadiusForPercent(50) > 0);
        assertEquals(WindowBackground.MAX_BLUR_RADIUS, WindowBackground.blurRadiusForPercent(100));
    }

    @Test
    public void applyEffects_blurChangesPixels() {
        BufferedImage source = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 20, 40);
        g.setColor(Color.WHITE);
        g.fillRect(20, 0, 20, 40);
        g.dispose();

        BufferedImage blurred = WindowBackground.applyEffects(source, 80);
        assertNotNull(blurred);
        assertEquals(40, blurred.getWidth());
        int edge = blurred.getRGB(19, 20);
        int r = (edge >> 16) & 0xff;
        assertTrue("blur should soften hard edge", r > 0 && r < 255);
    }

    @Test
    public void clampOpacityAndBlur() {
        assertEquals(0, WindowBackground.clampBlurPercent(-3));
        assertEquals(100, WindowBackground.clampBlurPercent(140));
        assertEquals(0, WindowBackground.clampOpacityPercent(-1));
        assertEquals(100, WindowBackground.clampOpacityPercent(200));
    }

    @Test
    public void deleteFile_removesBackground() throws Exception {
        Files.writeString(backgroundFile, "x");
        WindowBackground.deleteFile(backgroundFile);
        assertFalse(Files.exists(backgroundFile));
    }
}
