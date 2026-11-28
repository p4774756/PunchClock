package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 粉紅傳檔進度條萌寵小人；headless 離屏繪製，不開視窗。
 */
public class Issue857ProgressWalkerTest {

    private static final int OPAQUE_THRESHOLD = 20;
    private static final int BAR_W = 320;
    private static final int BAR_H = 40;
    private static final double SIZE_CEILING = 1.42;

    @Test
    public void snapshotsAtKeyPercentsShowPetNearFillEdge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<NamedShot> shots = new ArrayList<>();
            shots.add(capturePercent("bar-0", 0));
            shots.add(capturePercent("bar-30", 30));
            shots.add(capturePercent("bar-70", 70));
            shots.add(capturePercent("bar-100", 100));
            for (NamedShot shot : shots) {
                assertPetVisible(shot);
                assertNearFillEdge(shot);
            }
            Rectangle ref = shots.get(1).petBox;
            for (int i = 1; i < shots.size() - 1; i++) {
                assertSizeStable(shots.get(i).label, ref, shots.get(i).petBox);
            }
            writeShotsIfRequested(shots);
        });
    }

    @Test
    public void chaseWalkFramesStayVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
            bar.setIndeterminate(false);
            bar.setValue(200);
            layout(bar);
            PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
            ui.setTargetValueForTest(600);
            List<NamedShot> walk = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                ui.advanceAnimationTickForTest();
                NamedShot shot = paintShot(bar, "walk-" + String.format(Locale.ROOT, "%02d", i));
                assertPetVisible(shot);
                walk.add(shot);
            }
            writeShotsIfRequested(walk);
        });
    }

    @Test
    public void idleStopsMotionTimer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
            bar.setIndeterminate(false);
            layout(bar);
            PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
            ui.freezeAtValue(350);
            assertFalse(ui.isMotionTimerRunning());
        });
    }

    @Test
    public void retinaScaleDoesNotDoublePetSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
            bar.setIndeterminate(false);
            layout(bar);
            PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
            ui.freezeAtValue(500);
            NamedShot logical = paintShot(bar, "logical", 1f);
            NamedShot scaled = paintShot(bar, "scaled", 2f);
            assertSizeStable("retina", logical.petBox, scaled.petBox);
        });
    }

    private static NamedShot capturePercent(String label, int percent) {
        JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
        bar.setIndeterminate(false);
        bar.setStringPainted(percent > 0);
        if (percent > 0) {
            bar.setString(percent + "%");
        }
        layout(bar);
        PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
        ui.freezeAtValue(percent * 10);
        return paintShot(bar, label);
    }

    private static void layout(JProgressBar bar) {
        bar.setSize(BAR_W, BAR_H);
        bar.doLayout();
    }

    private static NamedShot paintShot(JProgressBar bar, String label) {
        return paintShot(bar, label, 1f);
    }

    private static NamedShot paintShot(JProgressBar bar, String label, float graphicsScale) {
        BufferedImage image = new BufferedImage(BAR_W, BAR_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0x141424));
            g.fillRect(0, 0, BAR_W, BAR_H);
            if (graphicsScale != 1f) {
                g.scale(graphicsScale, graphicsScale);
            }
            bar.paint(g);
        } finally {
            g.dispose();
        }
        Rectangle petBox = largestOpaqueCluster(image, OPAQUE_THRESHOLD);
        int fillEdge = expectedFillEdge(bar);
        return new NamedShot(label, image, petBox, fillEdge);
    }

    private static int expectedFillEdge(JProgressBar bar) {
        PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
        int insets = 3;
        int stringReserve = bar.isStringPainted() ? 44 : 0;
        int barWidth = BAR_W - insets * 2 - stringReserve;
        int max = Math.max(1, bar.getMaximum());
        int amount = (int) Math.round(barWidth * (ui.displayValueForTest() / max));
        return insets + Math.min(barWidth, Math.max(0, amount));
    }

    private static void assertPetVisible(NamedShot shot) {
        assertNotNull(shot.label + " image", shot.image);
        assertTrue(shot.label + " pet width", shot.petBox.width > 6);
        assertTrue(shot.label + " pet height", shot.petBox.height > 8);
        assertTrue(shot.label + " non-blank", hasOpaque(shot.image, OPAQUE_THRESHOLD));
    }

    private static void assertNearFillEdge(NamedShot shot) {
        int margin = Math.max(12, shot.image.getWidth() / 12);
        int left = shot.petBox.x - margin;
        int right = shot.petBox.x + shot.petBox.width + margin;
        assertTrue(
                shot.label + " fill " + shot.fillEdgeX + " outside pet span " + left + ".." + right,
                shot.fillEdgeX >= left && shot.fillEdgeX <= right);
    }

    private static void assertSizeStable(String label, Rectangle ref, Rectangle box) {
        double wr = ratio(box.width, ref.width);
        double hr = ratio(box.height, ref.height);
        assertTrue(label + " width ratio " + wr, wr <= SIZE_CEILING && 1 / wr <= SIZE_CEILING);
        assertTrue(label + " height ratio " + hr, hr <= SIZE_CEILING && 1 / hr <= SIZE_CEILING);
    }

    private static double ratio(int a, int b) {
        if (b < 1) {
            return a < 1 ? 1 : Double.POSITIVE_INFINITY;
        }
        return a / (double) b;
    }

    private static boolean hasOpaque(BufferedImage image, int threshold) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Rectangle largestOpaqueCluster(BufferedImage image, int threshold) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int a = (image.getRGB(x, y) >>> 24) & 0xff;
                if (a <= threshold) {
                    continue;
                }
                int rgb = image.getRGB(x, y) & 0xffffff;
                if (rgb == 0xFF78B5 || rgb == 0x2C204C) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) {
            return new Rectangle();
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static void writeShotsIfRequested(List<NamedShot> shots) {
        try {
            String dir = System.getenv("SHOTS857_DIR");
            if (dir == null || dir.isEmpty()) {
                dir = System.getProperty("review.shots.dir");
            }
            if (dir == null || dir.isEmpty()) {
                return;
            }
            Path out = Paths.get(dir);
            Files.createDirectories(out);
            for (NamedShot shot : shots) {
                ImageIO.write(shot.image, "png", out.resolve(shot.label + ".png").toFile());
            }
            if (shots.size() > 1) {
                ImageIO.write(composeStrip(shots), "png", out.resolve("walk-strip.png").toFile());
            }
        } catch (Exception ignored) {
            // 選用輸出，失敗不影響測試。
        }
    }

    private static BufferedImage composeStrip(List<NamedShot> shots) {
        int gap = 4;
        int w = gap;
        int h = 0;
        for (NamedShot shot : shots) {
            w += shot.image.getWidth() + gap;
            h = Math.max(h, shot.image.getHeight());
        }
        BufferedImage sheet = new BufferedImage(w, h + 14, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            int x = gap;
            for (NamedShot shot : shots) {
                g.drawImage(shot.image, x, 0, null);
                g.drawString(shot.label, x, h + 12);
                x += shot.image.getWidth() + gap;
            }
        } finally {
            g.dispose();
        }
        return sheet;
    }

    private static final class NamedShot {
        final String label;
        final BufferedImage image;
        final Rectangle petBox;
        final int fillEdgeX;

        NamedShot(String label, BufferedImage image, Rectangle petBox, int fillEdgeX) {
            this.label = label;
            this.image = image;
            this.petBox = petBox;
            this.fillEdgeX = fillEdgeX;
        }
    }
}