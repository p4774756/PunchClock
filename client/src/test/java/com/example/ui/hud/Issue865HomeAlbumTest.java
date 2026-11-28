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

import static org.junit.Assert.assertFalse;
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
                assertFalse(shell.homeAlbumActiveForTests());
                assertTrue(hasBuiltinIllustrationPixels(shell.paintHomeFrameForTests()));

                shell.configureHomeAlbum(true, empty, 30, false);
                assertFalse(shell.homeAlbumActiveForTests());
                assertTrue(hasBuiltinIllustrationPixels(shell.paintHomeFrameForTests()));

                shell.configureHomeAlbum(true, broken, 30, false);
                assertFalse(shell.homeAlbumActiveForTests());
                assertTrue(hasBuiltinIllustrationPixels(shell.paintHomeFrameForTests()));
            });
        } finally {
            deleteTree(empty);
            deleteTree(broken);
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
                assertTrue(shell.homeAlbumRotateTimerRunningForTests());

                shell.configureHomeAlbum(true, dir, 0, false);
                assertFalse(shell.homeAlbumRotateTimerRunningForTests());

                shell.configureHomeAlbum(true, dir, 15, false);
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
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
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