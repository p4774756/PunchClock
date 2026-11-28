package com.example.ui;

import com.example.pet.DesktopPetSpriteLoader;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 粉紅傳檔進度條：軌道自繪，填充緣站萌寵小人（走路／跑步／待機／完成揮手）。
 */
public final class PetWalkerProgressBarUI extends BasicProgressBarUI {

    static final Color TRACK = new Color(0x2C, 0x20, 0x4C);
    static final Color FILL = new Color(0xFF, 0x78, 0xB5);

    private static final int TRACK_HEIGHT = 12;
    private static final int TEXT_PAD = 2;
    private static final int PET_TEXT_GAP = 2;
    private static final int WALK_MS = 90;
    private static final int RUN_MS = 55;
    private static final int INDET_MS = 140;
    private static final int IDLE_MS = 280;
    private static final double CHASE_WALK = 22.0;
    private static final double CHASE_RUN = 48.0;
    private static final int RUN_TARGET_JUMP = 80;
    private static final int WAVE_TICKS = 14;
    private static final int HOP_PEAK = 6;
    private static final int RUN_BOB_PEAK = 3;
    private static final String[] RUN_RIGHT = {
            "/pet/milu/run/right_01.png",
            "/pet/milu/run/right_02.png",
            "/pet/milu/run/right_03.png",
            "/pet/milu/run/right_04.png",
            "/pet/milu/run/right_05.png",
            "/pet/milu/run/right_06.png"
    };

    private enum Motion {
        IDLE, WALK, RUN, WAVE, INDETERMINATE
    }

    private DesktopPetSpriteLoader.Frames frames;
    private BufferedImage[] runFrames;
    private double displayValue;
    private int targetValue;
    private Motion motion = Motion.IDLE;
    private int animTick;
    private int waveLeft;
    private int hopTick = -1;
    private boolean waveDoneAtFull;
    private int indetFrame;
    private long lastTargetChangeMs;
    private int prevTargetValue;
    private Timer motionTimer;
    private PropertyChangeListener valueListener;
    private PropertyChangeListener indeterminateListener;
    private HierarchyListener hierarchyListener;
    private int lastFillEdgeX;
    private int lastPetMinX;
    private int lastPetMaxX;
    private int lastTextLeftX;

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        frames = DesktopPetSpriteLoader.load();
        runFrames = loadRunFrames();
        displayValue = progressBar.getValue();
        targetValue = progressBar.getValue();
        prevTargetValue = targetValue;
        lastTargetChangeMs = System.currentTimeMillis();
        valueListener = this::onValueChanged;
        indeterminateListener = e -> resetIndeterminate();
        hierarchyListener = this::onHierarchyChanged;
        progressBar.addPropertyChangeListener("value", valueListener);
        progressBar.addPropertyChangeListener("indeterminate", indeterminateListener);
        progressBar.addHierarchyListener(hierarchyListener);
        syncMotion();
    }

    @Override
    public void uninstallUI(JComponent c) {
        stopMotionTimer();
        if (valueListener != null) {
            progressBar.removePropertyChangeListener("value", valueListener);
            valueListener = null;
        }
        if (indeterminateListener != null) {
            progressBar.removePropertyChangeListener("indeterminate", indeterminateListener);
            indeterminateListener = null;
        }
        if (hierarchyListener != null) {
            progressBar.removeHierarchyListener(hierarchyListener);
            hierarchyListener = null;
        }
        frames = null;
        runFrames = null;
        super.uninstallUI(c);
    }

    /** 對話框關閉或聊天卡結束時停表，避免隱藏後計時器仍重繪。 */
    static void stopMotionIfPresent(JProgressBar bar) {
        if (bar != null && bar.getUI() instanceof PetWalkerProgressBarUI) {
            ((PetWalkerProgressBarUI) bar.getUI()).stopMotionTimer();
        }
    }

    private void onHierarchyChanged(HierarchyEvent event) {
        long flags = event.getChangeFlags();
        if ((flags & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED)) == 0) {
            return;
        }
        if (isBarActive()) {
            syncMotion();
        } else {
            stopMotionTimer();
        }
    }

    private boolean isBarActive() {
        return progressBar != null && progressBar.isVisible() && progressBar.isDisplayable();
    }

    private void onValueChanged(PropertyChangeEvent event) {
        int next = progressBar.getValue();
        long now = System.currentTimeMillis();
        if (Math.abs(next - prevTargetValue) >= RUN_TARGET_JUMP) {
            lastTargetChangeMs = now;
        }
        prevTargetValue = next;
        targetValue = next;
        if (targetValue < progressBar.getMaximum() && waveDoneAtFull) {
            waveDoneAtFull = false;
            hopTick = -1;
            waveLeft = 0;
        }
        syncMotion();
    }

    private void resetIndeterminate() {
        if (progressBar.isIndeterminate()) {
            motion = Motion.INDETERMINATE;
            indetFrame = 0;
            ensureMotionTimer(INDET_MS);
        } else {
            syncMotion();
        }
    }

    void syncMotion() {
        if (!isBarActive()) {
            stopMotionTimer();
            return;
        }
        if (progressBar.isIndeterminate()) {
            motion = Motion.INDETERMINATE;
            ensureMotionTimer(INDET_MS);
            return;
        }
        int max = Math.max(1, progressBar.getMaximum());
        if (targetValue >= max && Math.abs(displayValue - max) < 0.5) {
            if (!waveDoneAtFull) {
                motion = Motion.WAVE;
                waveLeft = WAVE_TICKS;
                hopTick = 0;
                ensureMotionTimer(WALK_MS);
            } else {
                motion = Motion.IDLE;
                ensureMotionTimer(IDLE_MS);
            }
            return;
        }
        if (chasing()) {
            motion = Motion.RUN;
            ensureMotionTimer(RUN_MS);
        } else {
            motion = Motion.IDLE;
            ensureMotionTimer(IDLE_MS);
        }
    }

    private boolean chasing() {
        return Math.abs(targetValue - displayValue) > 0.4;
    }

    private boolean shouldRun() {
        if (Math.abs(targetValue - prevTargetValue) >= RUN_TARGET_JUMP) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastTargetChangeMs;
        return elapsed < 450 && Math.abs(targetValue - displayValue) > 40;
    }

    private void ensureMotionTimer(int delayMs) {
        if (!isBarActive()) {
            stopMotionTimer();
            return;
        }
        if (motionTimer != null && motionTimer.getDelay() == delayMs && motionTimer.isRunning()) {
            return;
        }
        stopMotionTimer();
        motionTimer = new Timer(delayMs, e -> tick());
        motionTimer.setRepeats(true);
        motionTimer.start();
    }

    private void stopMotionTimer() {
        if (motionTimer != null) {
            motionTimer.stop();
            motionTimer = null;
        }
    }

    private void tick() {
        if (progressBar.isIndeterminate()) {
            indetFrame++;
            animTick++;
            progressBar.repaint();
            return;
        }
        if (motion == Motion.IDLE) {
            animTick++;
            progressBar.repaint();
            return;
        }
        int max = Math.max(1, progressBar.getMaximum());
        if (motion == Motion.WAVE) {
            animTick++;
            if (hopTick >= 0 && hopTick < WAVE_TICKS / 2) {
                hopTick++;
            } else if (hopTick >= WAVE_TICKS / 2) {
                hopTick++;
            }
            waveLeft--;
            if (waveLeft <= 0) {
                displayValue = max;
                waveDoneAtFull = true;
                motion = Motion.IDLE;
                hopTick = -1;
                stopMotionTimer();
            }
            progressBar.repaint();
            syncMotion();
            return;
        }
        double step = motion == Motion.RUN ? CHASE_RUN : CHASE_WALK;
        if (displayValue < targetValue) {
            displayValue = Math.min(targetValue, displayValue + step);
        } else if (displayValue > targetValue) {
            displayValue = Math.max(targetValue, displayValue - step);
        }
        animTick++;
        progressBar.repaint();
        syncMotion();
    }

    boolean isMotionTimerRunning() {
        return motionTimer != null && motionTimer.isRunning();
    }

    /** Headless 測試：定住進度並停住動畫。 */
    void freezeAtValue(int value) {
        stopMotionTimer();
        targetValue = value;
        displayValue = value;
        int max = Math.max(1, progressBar.getMaximum());
        if (value <= 0) {
            waveDoneAtFull = false;
            motion = Motion.IDLE;
            animTick = 0;
        } else if (value >= max) {
            waveDoneAtFull = true;
            motion = Motion.IDLE;
            animTick = 0;
        } else {
            waveDoneAtFull = false;
            motion = Motion.RUN;
            animTick = 2; // 第 3 幀跨步最明顯
        }
        hopTick = -1;
        progressBar.repaint();
    }

    /** Headless 測試：定住進度並指定跑步影格（0-based）。 */
    void freezeAtRunFrame(int value, int frame) {
        freezeAtValue(value);
        motion = Motion.RUN;
        animTick = Math.max(0, frame);
        progressBar.repaint();
    }

    /** Headless 測試：連續追趕用。 */
    void setTargetValueForTest(int value) {
        targetValue = value;
        progressBar.setValue(value);
        syncMotion();
    }

    /** Headless 測試：推進一幀動畫。 */
    void advanceAnimationTickForTest() {
        tick();
    }

    double displayValueForTest() {
        return displayValue;
    }

    int lastFillEdgeXForTest() {
        return lastFillEdgeX;
    }

    int lastPetMinXForTest() {
        return lastPetMinX;
    }

    int lastPetMaxXForTest() {
        return lastPetMaxX;
    }

    int lastTextLeftXForTest() {
        return lastTextLeftX;
    }

    @Override
    protected void paintDeterminate(Graphics g, JComponent c) {
        paintBar(g, c, false);
    }

    @Override
    protected void paintIndeterminate(Graphics g, JComponent c) {
        paintBar(g, c, true);
    }

    private void paintBar(Graphics g, JComponent c, boolean indeterminate) {
        Insets b = progressBar.getInsets();
        int width = progressBar.getWidth() - (b.right + b.left);
        int height = progressBar.getHeight() - (b.top + b.bottom);
        if (width <= 0 || height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int x = b.left;
            int y = b.top;
            PetMetrics pet = measurePet();
            String text = progressBar.isStringPainted() ? progressBar.getString() : null;
            int textLeft = x + width;
            if (text != null && !text.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics();
                int sw = fm.stringWidth(text);
                textLeft = x + width - sw - TEXT_PAD;
            }
            int petMinX = x;
            int petMaxX = Math.max(petMinX, text == null || text.isEmpty() ? x + width : textLeft - PET_TEXT_GAP);
            int trackRight = Math.max(x, petMaxX - pet.rightOverhang);
            int barWidth = Math.max(0, trackRight - x);
            int trackY = y + height - TRACK_HEIGHT;
            g2.setColor(TRACK);
            g2.fillRoundRect(x, trackY, barWidth, TRACK_HEIGHT, 6, 6);

            int fillEdgeX;
            if (indeterminate) {
                boxRect = getBox(boxRect);
                if (boxRect != null) {
                    int boxX = Math.max(x, boxRect.x);
                    int boxW = Math.min(boxRect.width, Math.max(0, trackRight - boxX));
                    if (boxW > 0) {
                        g2.setColor(FILL);
                        g2.fillRoundRect(boxX, trackY, boxW, TRACK_HEIGHT, 6, 6);
                    }
                    fillEdgeX = boxX + boxW;
                } else {
                    fillEdgeX = x;
                }
            } else {
                int max = Math.max(1, progressBar.getMaximum());
                int amount = (int) Math.round(barWidth * (displayValue / max));
                amount = Math.min(barWidth, Math.max(0, amount));
                if (amount > 0) {
                    g2.setColor(FILL);
                    g2.fillRoundRect(x, trackY, Math.max(amount, Math.min(6, barWidth)), TRACK_HEIGHT, 6, 6);
                }
                fillEdgeX = x + amount;
            }
            lastFillEdgeX = fillEdgeX;
            lastPetMinX = petMinX;
            lastPetMaxX = petMaxX;
            lastTextLeftX = textLeft;

            drawPet(g2, fillEdgeX, trackY + TRACK_HEIGHT, pet, petMinX, petMaxX);

            if (text != null && !text.isEmpty()) {
                paintStringRight(g2, x, y, width, height, text);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintStringRight(Graphics2D g2, int x, int y, int width, int height, String text) {
        FontMetrics fm = g2.getFontMetrics();
        int sw = fm.stringWidth(text);
        int sx = x + width - sw - TEXT_PAD;
        int sy = y + (height - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(Color.WHITE);
        g2.drawString(text, sx, sy);
    }

    private void drawPet(Graphics2D g2, int footX, int footBaselineY, PetMetrics pet, int minX, int maxX) {
        if (pet.sprite == null || pet.drawW <= 0 || pet.drawH <= 0) {
            return;
        }
        int hop = hopOffsetY();
        int drawX = footX - pet.footOffsetX;
        int drawY = footBaselineY - pet.footOffsetY - hop;
        if (drawX < minX) {
            drawX = minX;
        }
        if (drawX + pet.drawW > maxX) {
            drawX = maxX - pet.drawW;
        }
        if (drawX < minX) {
            drawX = minX;
        }
        g2.drawImage(pet.sprite, drawX, drawY, pet.drawW, pet.drawH, null);
        if (showsMotionFx()) {
            drawRunFx(g2, drawX, footBaselineY - hop, minX, maxX);
        }
    }

    private boolean showsMotionFx() {
        return motion == Motion.RUN || motion == Motion.WALK || motion == Motion.INDETERMINATE;
    }

    private void drawRunFx(Graphics2D g2, int petLeft, int footY, int minX, int maxX) {
        int phase = Math.floorMod(currentRunFrame(), 6);
        int originX = petLeft + 6;
        Color puff = new Color(255, 214, 232, 140);
        Color line = new Color(255, 230, 244, 180);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            int px = originX - 6 - i * 5 - (phase % 3);
            int py = footY - 3 - ((phase + i) % 3);
            int r = 2 + ((phase + i) % 3);
            if (px - r >= minX && px + r <= maxX) {
                g2.setColor(puff);
                g2.fillOval(px - r, py - r, r * 2, r * 2);
            }
        }
        g2.setColor(line);
        for (int i = 0; i < 2; i++) {
            int y = footY - 10 - i * 5;
            int x2 = originX - 2;
            int x1 = x2 - 8 - phase % 3;
            if (x1 >= minX && x2 <= maxX) {
                g2.drawLine(x1, y, x2, y - 1);
            }
        }
    }

    private int hopOffsetY() {
        if (motion == Motion.WAVE && hopTick >= 0) {
            double phase = hopTick / (double) (WAVE_TICKS / 2);
            if (phase > 1) {
                phase = 2 - phase;
            }
            return (int) Math.round(HOP_PEAK * Math.sin(phase * Math.PI));
        }
        if (motion == Motion.RUN || motion == Motion.WALK || motion == Motion.INDETERMINATE) {
            int[] bob = {0, 2, RUN_BOB_PEAK, 1, 2, RUN_BOB_PEAK - 1};
            return bob[Math.floorMod(currentRunFrame(), bob.length)];
        }
        if (motion == Motion.IDLE) {
            return (int) Math.round((1 + Math.sin(animTick * 0.5)) * 0.8);
        }
        return 0;
    }

    private PetMetrics measurePet() {
        PetMetrics metrics = new PetMetrics();
        metrics.sprite = pickSprite();
        if (metrics.sprite == null) {
            return metrics;
        }
        double petScale = computePetScale(metrics.sprite);
        metrics.drawW = Math.max(1, (int) Math.round(metrics.sprite.getWidth() * petScale));
        metrics.drawH = Math.max(1, (int) Math.round(metrics.sprite.getHeight() * petScale));
        metrics.footOffsetX = (int) Math.round(DesktopPetSpriteLoader.ANCHOR_X * petScale);
        metrics.footOffsetY = (int) Math.round(DesktopPetSpriteLoader.ANCHOR_Y * petScale);
        metrics.rightOverhang = Math.max(0, metrics.drawW - metrics.footOffsetX);
        return metrics;
    }

    private double computePetScale(java.awt.image.BufferedImage sprite) {
        int targetHeight = Math.min(38, Math.max(26, progressBar.getHeight() - 4));
        int srcFoot = DesktopPetSpriteLoader.ANCHOR_Y;
        return targetHeight / (double) Math.max(1, srcFoot);
    }

    private BufferedImage pickSprite() {
        BufferedImage running = runFrame(currentRunFrame());
        if (running != null) {
            return running;
        }
        if (frames == null) {
            return null;
        }
        int walkFrames = Math.max(1, frames.walkRightCount());
        switch (motion) {
            case WAVE:
                return frames.wave(Math.floorDiv(animTick, 2));
            case WALK:
            case RUN:
                return frames.walkRight(animTick);
            case INDETERMINATE:
                return frames.walkRight(Math.floorMod(indetFrame, walkFrames));
            case IDLE:
            default:
                return frames.walkRight(0);
        }
    }

    private int currentRunFrame() {
        if (motion == Motion.IDLE || motion == Motion.WAVE) {
            return 0;
        }
        if (motion == Motion.INDETERMINATE) {
            return indetFrame;
        }
        return animTick;
    }

    int runFrameCountForTest() {
        return runFrames == null ? 0 : runFrames.length;
    }

    BufferedImage runFrame(int index) {
        if (runFrames == null || runFrames.length == 0) {
            return null;
        }
        return runFrames[Math.floorMod(index, runFrames.length)];
    }

    static BufferedImage[] loadRunFrames() {
        List<BufferedImage> loaded = new ArrayList<BufferedImage>();
        for (String path : RUN_RIGHT) {
            try (InputStream in = PetWalkerProgressBarUI.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    loaded.add(image);
                }
            } catch (IOException ignored) {
                // 單張失敗就略過，其餘影格照常。
            }
        }
        return loaded.toArray(new BufferedImage[0]);
    }

    @Override
    protected Color getSelectionForeground() {
        return Color.WHITE;
    }

    @Override
    protected Color getSelectionBackground() {
        return new Color(30, 41, 59);
    }

    private static final class PetMetrics {
        java.awt.image.BufferedImage sprite;
        int drawW;
        int drawH;
        int footOffsetX;
        int footOffsetY;
        int rightOverhang;
    }
}
