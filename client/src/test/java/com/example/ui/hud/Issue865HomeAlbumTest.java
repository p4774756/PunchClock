package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.awt.Rectangle;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Issue865HomeAlbumTest {

    @Test
    public void albumOnShowsFirstThenNextAfterCycle() throws Exception {
        Path dir = Files.createTempDirectory("pc865-album");
        try {
            writeSolidPng(dir.resolve("a-red.png"), Color.RED);
            writeSolidPng(dir.resolve("b-blue.png"), Color.BLUE);
            SwingUtilities.invokeAndWait(() -> {
                AnimeShell shell = shellHome(380, 520);
                shell.showHome();
                shell.configureHomeAlbum(true, dir, 0, false);
                shell.awaitHomeAlbumForTests();
                shell.doLayout();
                assertTrue(shell.homeAlbumActiveForTests());
                assertTrue(isMostlyRed(shell.paintHomeFrameForTests()));
                assertFalse(shell.homeAlbumRotateTimerRunningForTests());
                shell.cycleHomeDisplay();
                shell.settleHomeAlbumForTests();
                assertTrue(isMostlyBlue(shell.paintHomeFrameForTests()));
            });
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void albumOffOrEmptyFallsBackToBuiltin() throws Exception {
        Path empty = Files.createTempDirectory("pc865-empty");
        Path broken = Files.createTempDirectory("pc865-broken");
        try {
            Files.write(broken.resolve("bad.jpg"), new byte[] {1, 2, 3, 4});
            SwingUtilities.invokeAndWait(() -> {
                AnimeShell shell = shellHome(380, 520);
                shell.configureHomeAlbum(false, empty, 30, false);
                shell.awaitHomeAlbumForTests();
                assertFalse(shell.homeAlbumActiveForTests());
                assertTrue(hasBuiltinIllustrationPixels(shell.paintHomeFrameForTests()));

                shell.configureHomeAlbum(true, empty, 30, false);
                shell.awaitHomeAlbumForTests();
                assertFalse(shell.homeAlbumActiveForTests());
                assertTrue(hasBuiltinIllustrationPixels(shell.paintHomeFrameForTests()));

                shell.configureHomeAlbum(true, broken, 30, false);
                shell.awaitHomeAlbumForTests();
                assertFalse(shell.homeAlbumActiveForTests());
                assertTrue(hasBuiltinIllustrationPixels(shell.paintHomeFrameForTests()));
            });
        } finally {
            deleteTree(empty);
            deleteTree(broken);
        }
    }

    @Test
    public void manualCycleRestartsAutoRotateTimer() throws Exception {
        Path dir = Files.createTempDirectory("pc865-manual-reset");
        try {
            writeSolidPng(dir.resolve("one.png"), Color.ORANGE);
            writeSolidPng(dir.resolve("two.png"), Color.CYAN);
            SwingUtilities.invokeAndWait(() -> {
                HomeAlbumSlideshow slideshow = new HomeAlbumSlideshow(
                        () -> { }, () -> true, () -> 40, true);
                slideshow.configure(true, dir, 15, false);
                slideshow.reload();
                slideshow.awaitReloadForTests(4000);
                slideshow.syncRotateTimer();
                assertTrue(slideshow.isRotateTimerRunning());
                long beforeManual = slideshow.rotateTimerStartedAtMsForTests();
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                slideshow.cycleNext(true);
                long afterManual = slideshow.rotateTimerStartedAtMsForTests();
                assertTrue("手動換圖應重置計時起點", afterManual >= beforeManual + 30);
            });
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void albumCoverDrawRectMatchesBuiltinIllustration() throws Exception {
        try (InputStream in = Issue865HomeAlbumTest.class.getResourceAsStream(
                HomeIllustration.ROOM_B.resourcePath())) {
            assertNotNull(in);
            var roomB = ImageIO.read(in);
            int imgW = roomB.getWidth();
            int imgH = roomB.getHeight();
            int crop = HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.ROOM_B, imgH);
            int sliceH = imgH - crop;
            Path dir = Files.createTempDirectory("pc865-cover-rect");
            try {
                writeSolidPng(dir.resolve("a.png"), imgW, sliceH, Color.MAGENTA);
                writeSolidPng(dir.resolve("b.png"), imgW, sliceH, Color.PINK);
                SwingUtilities.invokeAndWait(() -> {
                    AnimeShell shell = shellHome(380, 520);
                    shell.showHome();
                    Rectangle builtin = shell.homeCoverDrawRectInShellCoordsForTests();
                    shell.configureHomeAlbum(true, dir, 0, false);
                shell.awaitHomeAlbumForTests();
                    assertTrue(shell.homeAlbumActiveForTests());
                    Rectangle album = shell.homeCoverDrawRectInShellCoordsForTests();
                    assertRectsClose(builtin, album, 3);
                });
            } finally {
                deleteTree(dir);
            }
        }
    }

    @Test
    public void rotateTimerStopsWhenNotOnHomeOrManualInterval() throws Exception {
        Path dir = Files.createTempDirectory("pc865-timer");
        try {
            writeSolidPng(dir.resolve("one.png"), Color.GREEN);
            writeSolidPng(dir.resolve("two.png"), Color.YELLOW);
            SwingUtilities.invokeAndWait(() -> {
                AnimeShell shell = shellHome(380, 520);
                shell.showHome();
                shell.configureHomeAlbum(true, dir, 15, false);
                shell.awaitHomeAlbumForTests();
                assertTrue(shell.homeAlbumRotateTimerRunningForTests());

                shell.configureHomeAlbum(true, dir, 0, false);
                shell.awaitHomeAlbumForTests();
                assertFalse(shell.homeAlbumRotateTimerRunningForTests());

                shell.configureHomeAlbum(true, dir, 15, false);
                shell.awaitHomeAlbumForTests();
                assertTrue(shell.homeAlbumRotateTimerRunningForTests());

                shell.openTab(0);
                assertFalse(shell.homeAlbumRotateTimerRunningForTests());

                shell.showHome();
                assertTrue(shell.homeAlbumRotateTimerRunningForTests());

                shell.setMiniMode(true);
                assertFalse(shell.homeAlbumRotateTimerRunningForTests());
            });
        } finally {
            deleteTree(dir);
        }
    }

    private static AnimeShell shellHome(int w, int h) {
        AnimeShell shell = new AnimeShell(null, new JTabbedPane());
        shell.setSize(w, h);
        return shell;
    }

    private static void writeSolidPng(Path file, Color color) throws IOException {
        writeSolidPng(file, 400, 300, color);
    }

    private static void writeSolidPng(Path file, int w, int h, Color color) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    private static void assertRectsClose(Rectangle expected, Rectangle actual, int tolerancePx) {
        assertTrue("x", Math.abs(expected.x - actual.x) <= tolerancePx);
        assertTrue("y", Math.abs(expected.y - actual.y) <= tolerancePx);
        assertTrue("width", Math.abs(expected.width - actual.width) <= tolerancePx);
        assertTrue("height", Math.abs(expected.height - actual.height) <= tolerancePx);
    }

    private static boolean isMostlyRed(BufferedImage frame) {
        return dominantHue(frame) == Dominant.RED;
    }

    private static boolean isMostlyBlue(BufferedImage frame) {
        return dominantHue(frame) == Dominant.BLUE;
    }

    private enum Dominant { RED, BLUE, OTHER }

    private static Dominant dominantHue(BufferedImage frame) {
        int tab = ShellDimensions.TAB_BAR_HEIGHT;
        int x0 = frame.getWidth() / 4;
        int y0 = frame.getHeight() / 8;
        int x1 = frame.getWidth() * 3 / 4;
        int y1 = Math.max(y0 + 8, frame.getHeight() - tab - 8);
        long r = 0;
        long b = 0;
        int n = 0;
        for (int y = y0; y < y1; y += 4) {
            for (int x = x0; x < x1; x += 4) {
                int rgb = frame.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                b += rgb & 0xFF;
                n++;
            }
        }
        if (n == 0) {
            return Dominant.OTHER;
        }
        r /= n;
        b /= n;
        if (r > 90 && r > b + 25) {
            return Dominant.RED;
        }
        if (b > 90 && b > r + 25) {
            return Dominant.BLUE;
        }
        return Dominant.OTHER;
    }

    private static boolean hasBuiltinIllustrationPixels(BufferedImage frame) {
        int cx = frame.getWidth() / 2;
        int cy = frame.getHeight() / 3;
        int rgb = frame.getRGB(cx, cy);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return !(r > 200 && g < 80 && b < 80) && !(b > 200 && r < 80);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // temp cleanup best-effort
                        }
                    });
        }
    }
}