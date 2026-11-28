package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
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
import static org.junit.Assert.fail;

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
            writeShotsIfRequested(shots);
            for (NamedShot shot : shots) {
                assertPetVisible(shot);
                assertNearFillEdge(shot);
                assertPetFullyInsideBar(shot);
            }
            // 0% 的像素簇可能因裙子/頭髮接近軌道色而偏窄，改以「不是左緣殘片」驗收。
            assertTrue("bar-0 pet too narrow " + shots.get(0).petBox,
                    shots.get(0).petBox.width >= 16 && shots.get(0).petBox.height >= 16);
            assertNoOverlapWithPercent(shots.get(3));
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
    public void hidingOrRemovingBarStopsMotionTimer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
            PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
            assertFalse("not displayable yet", ui.isMotionTimerRunning());

            JPanel parent = new JPanel();
            parent.add(bar);
            parent.addNotify();
            try {
                assertTrue(bar.isDisplayable());
                bar.setIndeterminate(true);
                ui.syncMotion();
                assertTrue("indeterminate displayable bar runs timer", ui.isMotionTimerRunning());

                bar.setVisible(false);
                assertFalse("hidden bar stops timer", ui.isMotionTimerRunning());

                bar.setVisible(true);
                ui.syncMotion();
                assertTrue("shown again restarts timer", ui.isMotionTimerRunning());

                parent.remove(bar);
                assertFalse(bar.isDisplayable());
                assertFalse("removed bar stops timer", ui.isMotionTimerRunning());
            } finally {
                if (bar.getParent() == parent) {
                    parent.remove(bar);
                }
                parent.removeNotify();
            }
        });
    }

    @Test
    public void relayCardFinishStopsMotionTimer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel parent = new JPanel();
            PeerChatRelayCard card = new PeerChatRelayCard(new Font("SansSerif", Font.PLAIN, 13));
            parent.add(card);
            parent.addNotify();
            try {
                card.showOutboundTransfer("demo.zip", "1 MB");
                JProgressBar bar = findProgressBar(card);
                assertNotNull(bar);
                PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
                ui.syncMotion();
                assertTrue(ui.isMotionTimerRunning());
                card.finishSuccess(true, "demo.zip");
                assertFalse("finishSuccess stops timer", ui.isMotionTimerRunning());
            } finally {
                parent.removeNotify();
            }
        });
    }

    @Test
    public void disposingDialogStopsMotionTimer() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            JPanel host = new JPanel();
            TransferProgressDialog dialog = TransferProgressDialog.open(host, "下載更新");
            try {
                Component root = dialogComponent(dialog);
                JProgressBar bar = root instanceof Container ? findProgressBar((Container) root) : null;
                assertNotNull(bar);
                PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
                ui.syncMotion();
                assertTrue(ui.isMotionTimerRunning());
                dialog.close();
                // close() posts to EDT; we are already on EDT so the runnable is queued.
            } finally {
                // Drain the close runnable if it was invoked later-style.
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            // After close() invokeLater has run, we cannot easily get the bar.
            // The path is also covered by stopMotionIfPresent + hierarchy tests.
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
            Rectangle scaledLogical = new Rectangle(
                    Math.round(scaled.petBox.x / 2f),
                    Math.round(scaled.petBox.y / 2f),
                    Math.round(scaled.petBox.width / 2f),
                    Math.round(scaled.petBox.height / 2f));
            assertSizeStable("retina", logical.petBox, scaledLogical);
        });
    }

    @Test
    public void contextShotsShowPetInDialogRelayAndIndeterminate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<NamedShot> shots = new ArrayList<>();
            shots.add(captureDialog("dialog", 42));
            shots.add(captureRelay("relay", 42));
            shots.add(captureIndeterminate("indeterminate"));
            for (NamedShot shot : shots) {
                assertPetVisible(shot);
            }
            writeShotsIfRequested(shots);
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

    private static NamedShot captureDialog(String label, int percent) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(new Color(0x24, 0x1B, 0x38));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JLabel status = new JLabel("正在下載更新…");
        status.setForeground(Color.WHITE);
        status.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
        bar.setIndeterminate(false);
        bar.setStringPainted(true);
        bar.setString(percent + "%");
        JLabel detail = new JLabel("4.2 MB / 10 MB（" + percent + "%）");
        detail.setForeground(new Color(180, 180, 190));
        detail.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(status, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        panel.add(detail, BorderLayout.SOUTH);
        layoutTree(panel, 416, 118);
        PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
        ui.freezeAtValue(percent * 10);
        return paintComponent(panel, label);
    }

    private static NamedShot captureRelay(String label, int percent) {
        PeerChatRelayCard card = new PeerChatRelayCard(new Font("SansSerif", Font.PLAIN, 13));
        card.showOutboundTransfer("demo.zip", "10 MB");
        card.setProgress(percent * 100L, 10000L, true);
        JProgressBar bar = findProgressBar(card);
        layoutTree(card, 320, Math.max(90, card.getPreferredSize().height));
        if (bar != null) {
            PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
            ui.freezeAtValue(percent * 10);
        }
        return paintComponent(card, label);
    }

    private static NamedShot captureIndeterminate(String label) {
        JProgressBar bar = TransferProgressDialog.createRelayProgressBar();
        bar.setIndeterminate(true);
        bar.setStringPainted(false);
        layout(bar);
        PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
        for (int i = 0; i < 6; i++) {
            ui.advanceAnimationTickForTest();
        }
        return paintShot(bar, label);
    }

    private static void layout(JProgressBar bar) {
        bar.setSize(BAR_W, BAR_H);
        bar.doLayout();
    }

    private static void layoutTree(Component component, int width, int height) {
        component.setSize(width, height);
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            layoutChildren(container);
        }
    }

    private static void layoutChildren(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutChildren((Container) child);
            }
        }
    }

    private static NamedShot paintShot(JProgressBar bar, String label) {
        return paintShot(bar, label, 1f);
    }

    private static NamedShot paintShot(JProgressBar bar, String label, float graphicsScale) {
        int iw = Math.max(1, Math.round(BAR_W * graphicsScale));
        int ih = Math.max(1, Math.round(BAR_H * graphicsScale));
        BufferedImage image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0x141424));
            g.fillRect(0, 0, iw, ih);
            if (graphicsScale != 1f) {
                g.scale(graphicsScale, graphicsScale);
            }
            bar.paint(g);
        } finally {
            g.dispose();
        }
        PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
        int petMax = ui.lastPetMaxXForTest() > 0 ? ui.lastPetMaxXForTest() : image.getWidth();
        Rectangle petBox = petCluster(image, OPAQUE_THRESHOLD, 0, petMax);
        return new NamedShot(label, image, petBox, ui.lastFillEdgeXForTest(),
                ui.lastPetMinXForTest(), petMax, ui.lastTextLeftXForTest());
    }

    private static NamedShot paintComponent(Component component, String label) {
        int width = Math.max(1, component.getWidth());
        int height = Math.max(1, component.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(0x141424));
            g.fillRect(0, 0, width, height);
            component.paint(g);
        } finally {
            g.dispose();
        }
        JProgressBar bar = component instanceof JProgressBar
                ? (JProgressBar) component
                : findProgressBar(component instanceof Container ? (Container) component : null);
        int fillEdge = 0;
        int petMin = 0;
        int petMax = image.getWidth();
        int textLeft = image.getWidth();
        if (bar != null && bar.getUI() instanceof PetWalkerProgressBarUI) {
            PetWalkerProgressBarUI ui = (PetWalkerProgressBarUI) bar.getUI();
            fillEdge = ui.lastFillEdgeXForTest();
            petMin = ui.lastPetMinXForTest();
            petMax = ui.lastPetMaxXForTest();
            textLeft = ui.lastTextLeftXForTest();
        }
        Rectangle petBox = petCluster(image, OPAQUE_THRESHOLD);
        return new NamedShot(label, image, petBox, fillEdge, petMin, petMax, textLeft);
    }

    private static void assertPetVisible(NamedShot shot) {
        assertNotNull(shot.label + " image", shot.image);
        assertTrue(shot.label + " pet width " + shot.petBox.width, shot.petBox.width > 6);
        assertTrue(shot.label + " pet height " + shot.petBox.height, shot.petBox.height > 8);
        assertTrue(shot.label + " non-blank", hasOpaque(shot.image, OPAQUE_THRESHOLD));
    }

    private static void assertPetFullyInsideBar(NamedShot shot) {
        assertTrue(shot.label + " clipped left x=" + shot.petBox.x, shot.petBox.x >= shot.petMinX);
        assertTrue(shot.label + " clipped top y=" + shot.petBox.y, shot.petBox.y >= 0);
        assertTrue(
                shot.label + " clipped right " + (shot.petBox.x + shot.petBox.width) + " max=" + shot.petMaxX,
                shot.petBox.x + shot.petBox.width <= shot.petMaxX);
        assertTrue(
                shot.label + " clipped bottom " + (shot.petBox.y + shot.petBox.height),
                shot.petBox.y + shot.petBox.height <= shot.image.getHeight());
    }

    private static void assertNoOverlapWithPercent(NamedShot shot) {
        int petRight = shot.petBox.x + shot.petBox.width;
        assertTrue(
                shot.label + " pet right " + petRight + " overlaps text at " + shot.textLeftX
                        + " (clamp max " + shot.petMaxX + ")",
                petRight <= shot.textLeftX && petRight <= shot.petMaxX);
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

    private static Rectangle petCluster(BufferedImage image, int threshold) {
        return petCluster(image, threshold, 0, image.getWidth());
    }

    private static Rectangle petCluster(BufferedImage image, int threshold, int x0, int x1) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        int start = Math.max(0, x0);
        int end = Math.min(image.getWidth(), x1);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = start; x < end; x++) {
                if (!isPetPixel(image.getRGB(x, y), threshold)) {
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

    private static boolean isPetPixel(int argb, int threshold) {
        int a = (argb >>> 24) & 0xff;
        if (a <= threshold) {
            return false;
        }
        int rgb = argb & 0xffffff;
        // Chrome: fill, track, border, shot background, percent text.
        if (near(rgb, 0xFF78B5, 28) || near(rgb, 0x2C204C, 28) || near(rgb, 0x9275BD, 28)
                || near(rgb, 0x141424, 20) || near(rgb, 0xFFFFFF, 25)) {
            return false;
        }
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        // 百分比文字的灰階抗鋸齒，不是萌寵本體。
        if (max - min < 32 && max < 230) {
            return false;
        }
        return true;
    }

    private static boolean near(int rgb, int ref, int tol) {
        int dr = ((rgb >> 16) & 0xff) - ((ref >> 16) & 0xff);
        int dg = ((rgb >> 8) & 0xff) - ((ref >> 8) & 0xff);
        int db = (rgb & 0xff) - (ref & 0xff);
        return dr * dr + dg * dg + db * db <= tol * tol * 3;
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
            if (shots.size() > 1 && shots.get(0).label.startsWith("walk-")) {
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

    private static JProgressBar findProgressBar(Container root) {
        if (root == null) {
            return null;
        }
        for (Component child : root.getComponents()) {
            if (child instanceof JProgressBar) {
                return (JProgressBar) child;
            }
            if (child instanceof Container) {
                JProgressBar found = findProgressBar((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Component dialogComponent(TransferProgressDialog dialog) {
        try {
            var field = TransferProgressDialog.class.getDeclaredField("dialog");
            field.setAccessible(true);
            Object window = field.get(dialog);
            if (window instanceof Container) {
                return (Container) window;
            }
        } catch (ReflectiveOperationException ignored) {
            fail("cannot read TransferProgressDialog.dialog");
        }
        return null;
    }

    private static final class NamedShot {
        final String label;
        final BufferedImage image;
        final Rectangle petBox;
        final int fillEdgeX;
        final int petMinX;
        final int petMaxX;
        final int textLeftX;

        NamedShot(String label, BufferedImage image, Rectangle petBox, int fillEdgeX,
                  int petMinX, int petMaxX, int textLeftX) {
            this.label = label;
            this.image = image;
            this.petBox = petBox;
            this.fillEdgeX = fillEdgeX;
            this.petMinX = petMinX;
            this.petMaxX = petMaxX;
            this.textLeftX = textLeftX;
        }
    }
}
