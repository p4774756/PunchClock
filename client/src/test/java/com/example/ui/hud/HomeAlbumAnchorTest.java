package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HomeAlbumAnchorTest {

    @Test
    public void sidecarOverridesEstimate() throws Exception {
        Path dir = Files.createTempDirectory("pc868-sidecar");
        try {
            Path img = dir.resolve("portrait.png");
            writeFaceScene(img, 0.70, 0.28, 70, Color.PINK);
            Files.writeString(
                    dir.resolve("portrait.anchor.json"),
                    "{\"mouthX\":0.22,\"mouthY\":0.40,\"bubbleX\":0.18,\"bubbleY\":0.12,"
                            + "\"faceX\":0.10,\"faceY\":0.20,\"faceW\":0.24,\"faceH\":0.30}",
                    StandardCharsets.UTF_8);
            BufferedImage image = ImageIO.read(img.toFile());
            HomeBubbleAnchor.Spec spec = HomeAlbumAnchor.resolve(img, image);
            assertEquals(0.22, spec.mouthX, 0.001);
            assertEquals(0.40, spec.mouthY, 0.001);
            assertEquals(0.18, spec.bubbleX, 0.001);
            assertEquals(0.12, spec.bubbleY, 0.001);
            assertTrue(spec.hasFaceBox());
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void estimateFindsUpperFaceAndPlacesBubbleBesideIt() throws Exception {
        Path dir = Files.createTempDirectory("pc868-estimate");
        try {
            Path img = dir.resolve("right-face.png");
            writeFaceScene(img, 0.70, 0.26, 80, new Color(232, 176, 150));
            BufferedImage image = ImageIO.read(img.toFile());
            HomeBubbleAnchor.Spec spec = HomeAlbumAnchor.estimate(image);
            assertNotNull(spec);
            assertTrue("mouth should sit in the right-hand face", spec.mouthX > 0.55);
            assertTrue(spec.mouthY > 0.15 && spec.mouthY < 0.45);
            assertTrue(spec.hasFaceBox());
            assertTrue("bubble should prefer the open left side", spec.bubbleX < spec.faceX + 0.02);
            Rectangle face = new Rectangle(
                    (int) Math.round(spec.faceX * 1000),
                    (int) Math.round(spec.faceY * 1000),
                    (int) Math.round(spec.faceW * 1000),
                    (int) Math.round(spec.faceH * 1000));
            Rectangle bubble = new Rectangle(
                    (int) Math.round(spec.bubbleX * 1000) - 80,
                    (int) Math.round(spec.bubbleY * 1000) - 40,
                    160,
                    50);
            assertFalse("preferred bubble centre should not sit inside the face box",
                    face.contains(bubble.getCenterX(), bubble.getCenterY()));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void solidColorFallsBackToSafeTop() throws Exception {
        Path dir = Files.createTempDirectory("pc868-fallback");
        try {
            Path img = dir.resolve("blue.png");
            writeSolid(img, 400, 500, Color.BLUE);
            HomeBubbleAnchor.Spec spec = HomeAlbumAnchor.resolve(img, ImageIO.read(img.toFile()));
            HomeBubbleAnchor.Spec fallback = HomeBubbleAnchor.safeTopFallback();
            assertEquals(fallback.mouthX, spec.mouthX, 0.001);
            assertEquals(fallback.bubbleX, spec.bubbleX, 0.001);
            assertEquals(fallback.bubbleY, spec.bubbleY, 0.001);
            assertTrue(spec.bubbleY <= 0.16);
            assertTrue(spec.bubbleX <= 0.30);
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void cycleSwitchesPerImageAnchor() throws Exception {
        Path dir = Files.createTempDirectory("pc868-cycle");
        try {
            writeFaceScene(dir.resolve("a.png"), 0.28, 0.30, 70, new Color(230, 170, 140));
            writeFaceScene(dir.resolve("b.png"), 0.74, 0.30, 70, new Color(230, 170, 140));
            Files.writeString(
                    dir.resolve("a.anchor.json"),
                    "{\"mouthX\":0.25,\"mouthY\":0.30,\"bubbleX\":0.70,\"bubbleY\":0.12}",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    dir.resolve("b.anchor.json"),
                    "{\"mouthX\":0.78,\"mouthY\":0.31,\"bubbleX\":0.22,\"bubbleY\":0.13}",
                    StandardCharsets.UTF_8);
            SwingUtilities.invokeAndWait(() -> {
                AnimeShell shell = new AnimeShell(null, new JTabbedPane());
                shell.setSize(380, 520);
                shell.showHome();
                shell.configureHomeAlbum(true, dir, 0, false);
                assertTrue(shell.homeAlbumActiveForTests());
                HomeBubbleAnchor.Spec first = shell.homeAlbumAnchorForTests();
                assertEquals(0.25, first.mouthX, 0.001);
                shell.cycleHomeDisplay();
                shell.settleHomeAlbumForTests();
                HomeBubbleAnchor.Spec second = shell.homeAlbumAnchorForTests();
                assertEquals(0.78, second.mouthX, 0.001);
                assertTrue(Math.abs(first.bubbleX - second.bubbleX) > 0.2);
            });
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void paintedBubbleStaysOutsideEstimatedFace() throws Exception {
        Path dir = Files.createTempDirectory("pc868-paint");
        try {
            Path img = dir.resolve("face.png");
            writeFaceScene(img, 0.70, 0.26, 90, new Color(232, 176, 150));
            SwingUtilities.invokeAndWait(() -> {
                AnimeShell shell = new AnimeShell(null, new JTabbedPane());
                shell.setSize(380, 520);
                shell.showHome();
                shell.doLayout();
                shell.configureHomeAlbum(true, dir, 0, false);
                shell.showHomeBubbleImmediatelyForTests("午安，記得喝口水。");
                shell.paintHomeFrameForTests();
                HomeBubbleAnchor.Spec spec = shell.homeAlbumAnchorForTests();
                Rectangle bubble = shell.homeBubbleBoxForTests();
                assertTrue(bubble.width > 0);
                Rectangle content = new Rectangle(0, 0, 380, 520 - ShellDimensions.TAB_BAR_HEIGHT);
                Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                        content.width, content.height, 400, 520, 0);
                Rectangle face = HomeBubbleAnchor.imageNormRectToPanel(
                        spec.faceX, spec.faceY, spec.faceW, spec.faceH,
                        draw, 400, 520, 0);
                face.translate(content.x, content.y);
                Rectangle hit = bubble.intersection(face);
                assertFalse(
                        "bubble " + bubble + " overlaps face " + face,
                        hit.width > 4 && hit.height > 4);
            });
        } finally {
            deleteTree(dir);
        }
    }

    private static void writeFaceScene(Path file, double cx, double cy, int radius, Color skin)
            throws Exception {
        int w = 400;
        int h = 520;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 70, 140));
        g.fillRect(0, 0, w, h);
        g.setColor(skin);
        int x = (int) Math.round(cx * w) - radius;
        int y = (int) Math.round(cy * h) - radius;
        g.fillOval(x, y, radius * 2, (int) (radius * 2.3));
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    private static void writeSolid(Path file, int w, int h, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        }
    }
}
