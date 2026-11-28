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
    private static final int MAX_EDGE = 1600;

    private final Object loadLock = new Object();
    private int loadGeneration;
    private int loadedGeneration = -1;

    private final List<BufferedImage> images = new ArrayList<>();
    private final List<HomeBubbleAnchor.Spec> anchors = new ArrayList<>();
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
        int generation;
        synchronized (loadLock) {
            generation = ++loadGeneration;
            images.clear();
            anchors.clear();
            index = 0;
            outgoing = null;
            crossfading = false;
            fadeStart = 0;
            if (!enabled) {
                loadedGeneration = generation;
            }
        }
        if (!enabled) {
            syncRotateTimer();
            return;
        }
        final int gen = generation;
        final Path dir = directory;
        Thread thread = new Thread(() -> {
            loadGeneration(gen, dir);
            SwingUtilities.invokeLater(() -> {
                if (gen == loadGeneration) {
                    syncRotateTimer();
                    repaint.run();
                }
            });
        }, "home-album-load");
        thread.setDaemon(true);
        thread.start();
    }

    /** 測試用：在呼叫執行緒上等本輪載入結束（EDT 上會改為同步載入，避免跟 invokeLater 互鎖）。 */
    void awaitReloadForTests(long timeoutMs) {
        int gen = loadGeneration;
        if (SwingUtilities.isEventDispatchThread()) {
            loadGeneration(gen, directory);
            syncRotateTimer();
            return;
        }
        long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            synchronized (loadLock) {
                if (loadedGeneration >= gen) {
                    break;
                }
            }
            try {
                Thread.sleep(15);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        try {
            SwingUtilities.invokeAndWait(this::syncRotateTimer);
        } catch (Exception ignored) {
            // 測試後援
        }
    }

    private void loadGeneration(int generation, Path dir) {
        synchronized (loadLock) {
            if (loadedGeneration >= generation) {
                return;
            }
            if (generation != loadGeneration) {
                return;
            }
            images.clear();
            anchors.clear();
            if (!enabled) {
                loadedGeneration = generation;
                return;
            }
            try {
                List<Path> paths = PrivateAlbumPaths.listImages(dir);
                for (Path path : paths) {
                    if (generation != loadGeneration) {
                        return;
                    }
                    BufferedImage img = loadImage(path);
                    if (img != null) {
                        images.add(img);
                        anchors.add(HomeAlbumAnchor.resolve(path, img));
                    }
                }
            } catch (IOException ignored) {
                // 後備：無相簿圖
            }
            loadedGeneration = generation;
        }
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

    HomeBubbleAnchor.Spec currentAnchor() {
        if (!active() || anchors.isEmpty()) {
            return HomeBubbleAnchor.safeTopFallback();
        }
        int i = clampIndex(index);
        if (i >= anchors.size()) {
            return HomeBubbleAnchor.safeTopFallback();
        }
        HomeBubbleAnchor.Spec spec = anchors.get(i);
        return spec == null ? HomeBubbleAnchor.safeTopFallback() : spec;
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
            BufferedImage raw = ImageIO.read(path.toFile());
            return downscale(raw, MAX_EDGE);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    static BufferedImage downscale(BufferedImage raw, int maxEdge) {
        if (raw == null) {
            return null;
        }
        int w = raw.getWidth();
        int h = raw.getHeight();
        if (w <= 0 || h <= 0) {
            return raw;
        }
        int edge = Math.max(w, h);
        if (edge <= maxEdge) {
            return raw;
        }
        double scale = maxEdge / (double) edge;
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(raw, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
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