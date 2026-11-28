package com.example.ui.hud;

import com.example.service.PrivateAlbumPaths;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** 首頁私房相簿輪播（#865）；單一 {@link Timer} 負責自動換圖。 */
final class HomeAlbumSlideshow {

    static final int CROSS_FADE_MS = 320;

    private final Runnable repaint;
    private final BooleanSupplier mayAutoRotate;
    private final IntSupplier tickMs;
    private final boolean reducedMotion;
    private final Random random = new Random();

    private boolean enabled;
    private Path directory = PrivateAlbumPaths.defaultDirectory();
    private int intervalSeconds = 30;
    private boolean randomOrder;

    private final List<BufferedImage> images = new ArrayList<>();
    private int index;
    private BufferedImage outgoing;
    private long fadeStart;
    private boolean crossfading;

    private Timer rotateTimer;
    private long rotateTimerStartedAtMs;

    HomeAlbumSlideshow(Runnable repaint, BooleanSupplier mayAutoRotate, IntSupplier tickMs, boolean reducedMotion) {
        this.repaint = repaint == null ? () -> { } : repaint;
        this.mayAutoRotate = mayAutoRotate == null ? () -> false : mayAutoRotate;
        this.tickMs = tickMs == null ? () -> 40 : tickMs;
        this.reducedMotion = reducedMotion;
    }

    void configure(boolean enabled, Path directory, int intervalSeconds, boolean randomOrder) {
        this.enabled = enabled;
        this.directory = directory == null ? PrivateAlbumPaths.defaultDirectory() : directory;
        this.intervalSeconds = normalizeInterval(intervalSeconds);
        this.randomOrder = randomOrder;
    }

    void reload() {
        images.clear();
        index = 0;
        outgoing = null;
        crossfading = false;
        fadeStart = 0;
        if (!enabled) {
            syncRotateTimer();
            return;
        }
        try {
            List<Path> paths = PrivateAlbumPaths.listImages(directory);
            for (Path path : paths) {
                BufferedImage img = loadImage(path);
                if (img != null) {
                    images.add(img);
                }
            }
        } catch (IOException ignored) {
            // 後備：無相簿圖
        }
        syncRotateTimer();
    }

    boolean active() {
        return enabled && !images.isEmpty();
    }

    BufferedImage currentImage() {
        if (!active()) {
            return null;
        }
        return images.get(clampIndex(index));
    }

    int currentWidth() {
        BufferedImage img = currentImage();
        return img == null ? 1 : img.getWidth();
    }

    int currentHeight() {
        BufferedImage img = currentImage();
        return img == null ? 1 : img.getHeight();
    }

    void paint(Graphics2D g, Rectangle content) {
        if (!active() || content == null) {
            return;
        }
        BufferedImage current = currentImage();
        if (current == null) {
            return;
        }
        if (crossfading && outgoing != null && !reducedMotion) {
            float t = crossfadeProgress();
            if (t >= 1f) {
                crossfading = false;
                outgoing = null;
                drawCover(g, current, content, 1f);
            } else {
                drawCover(g, outgoing, content, 1f - t);
                drawCover(g, current, content, t);
            }
        } else {
            drawCover(g, current, content, 1f);
        }
    }

    boolean cycleNext(boolean manual) {
        if (!active()) {
            return false;
        }
        if (manual) {
            restartRotateTimerIfRunning();
        }
        if (images.size() == 1) {
            syncRotateTimer();
            return true;
        }
        int next = manual && randomOrder ? pickRandomOther(index) : nextSequential(index);
        beginTransition(next);
        syncRotateTimer();
        return true;
    }

    void syncRotateTimer() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::syncRotateTimer);
            return;
        }
        boolean run = intervalSeconds > 0 && active() && mayAutoRotate.getAsBoolean();
        if (!run) {
            stopRotateTimer();
            return;
        }
        int delay = Math.max(1, intervalSeconds) * 1000;
        if (rotateTimer == null) {
            rotateTimer = new Timer(delay, e -> onRotateTick());
            rotateTimer.setRepeats(true);
        } else if (rotateTimer.getDelay() != delay) {
            rotateTimer.setInitialDelay(delay);
            rotateTimer.setDelay(delay);
        }
        if (!rotateTimer.isRunning()) {
            rotateTimer.start();
            rotateTimerStartedAtMs = System.currentTimeMillis();
        }
    }

    private void restartRotateTimerIfRunning() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::restartRotateTimerIfRunning);
            return;
        }
        if (rotateTimer != null && rotateTimer.isRunning()) {
            rotateTimer.restart();
            rotateTimerStartedAtMs = System.currentTimeMillis();
        }
    }

    long rotateTimerStartedAtMsForTests() {
        return rotateTimerStartedAtMs;
    }

    void stopRotateTimer() {
        if (rotateTimer != null && rotateTimer.isRunning()) {
            rotateTimer.stop();
        }
    }

    boolean isRotateTimerRunning() {
        return rotateTimer != null && rotateTimer.isRunning();
    }

    void settleDisplayForTests() {
        crossfading = false;
        outgoing = null;
    }

    boolean tickCrossfade() {
        if (!crossfading || reducedMotion) {
            return false;
        }
        float t = crossfadeProgress();
        if (t >= 1f) {
            crossfading = false;
            outgoing = null;
            repaint.run();
            return true;
        }
        repaint.run();
        return true;
    }

    static int normalizeInterval(int seconds) {
        if (seconds == 0 || seconds == 15 || seconds == 30 || seconds == 60) {
            return seconds;
        }
        return 30;
    }

    private void onRotateTick() {
        if (!active() || !mayAutoRotate.getAsBoolean()) {
            syncRotateTimer();
            return;
        }
        int next = randomOrder ? pickRandomOther(index) : nextSequential(index);
        beginTransition(next);
    }

    private void beginTransition(int nextIndex) {
        if (!active()) {
            return;
        }
        BufferedImage next = images.get(clampIndex(nextIndex));
        BufferedImage prev = images.get(clampIndex(index));
        index = clampIndex(nextIndex);
        if (reducedMotion || prev == next) {
            outgoing = null;
            crossfading = false;
            repaint.run();
            return;
        }
        outgoing = prev;
        fadeStart = System.currentTimeMillis();
        crossfading = true;
        repaint.run();
    }

    private float crossfadeProgress() {
        if (!crossfading) {
            return 1f;
        }
        long elapsed = System.currentTimeMillis() - fadeStart;
        return Math.min(1f, elapsed / (float) CROSS_FADE_MS);
    }

    private int nextSequential(int current) {
        return (current + 1) % images.size();
    }

    private int pickRandomOther(int current) {
        if (images.size() <= 1) {
            return current;
        }
        int next = current;
        for (int tries = 0; tries < 8 && next == current; tries++) {
            next = random.nextInt(images.size());
        }
        if (next == current) {
            next = nextSequential(current);
        }
        return next;
    }

    private static int clampIndex(int i) {
        return Math.max(0, i);
    }

    private static BufferedImage loadImage(Path path) {
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static void drawCover(Graphics2D g, BufferedImage img, Rectangle content, float alpha) {
        if (img == null || content.width < 1 || content.height < 1) {
            return;
        }
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        Rectangle draw = HomeBubbleAnchor.imageDrawRect(content.width, content.height, imgW, imgH);
        int x = content.x + draw.x;
        int y = content.y + draw.y;
        Composite saved = g.getComposite();
        if (alpha < 1f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, x, y, draw.width, draw.height, null);
        g.setComposite(saved);
    }
}