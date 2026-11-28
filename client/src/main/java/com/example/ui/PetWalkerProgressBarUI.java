package com.example.ui;

import com.example.pet.DesktopPetSpriteLoader;

import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * 粉紅傳檔進度條：軌道自繪，填充緣站萌寵小人（走路／跑步／待機／完成揮手）。
 */
public final class PetWalkerProgressBarUI extends BasicProgressBarUI {

    static final Color TRACK = new Color(0x2C, 0x20, 0x4C);
    static final Color FILL = new Color(0xFF, 0x78, 0xB5);

    private static final int TRACK_HEIGHT = 12;
    private static final int TEXT_PAD = 2;
    private static final int PET_TEXT_GAP = 2;
    private static final int WALK_MS = 120;
    private static final int RUN_MS = 70;
    private static final int INDET_MS = 110;
    private static final double CHASE_WALK = 22.0;
    private static final double CHASE_RUN = 48.0;
    private static final int RUN_TARGET_JUMP = 80;
    private static final int WAVE_TICKS = 14;
    private static final int HOP_PEAK = 6;

    private enum Motion {
        IDLE, WALK, RUN, WAVE, INDETERMINATE
    }

    private DesktopPetSpriteLoader.Frames frames;
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
                stopMotionTimer();
            }
            return;
        }
        if (chasing()) {
            motion = shouldRun() ? Motion.RUN : Motion.WALK;
            ensureMotionTimer(shouldRun() ? RUN_MS : WALK_MS);
        } else {
            motion = Motion.IDLE;
            stopMotionTimer();
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
        if (value >= max) {
            waveDoneAtFull = true;
            motion = Motion.IDLE;
        } else {
            waveDoneAtFull = false;
            motion = Motion.IDLE;
        }
        animTick = 0;
        hopTick = -1;
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
    }

    private int hopOffsetY() {
        if (hopTick < 0 || motion != Motion.WAVE) {
            return 0;
        }
        double phase = hopTick / (double) (WAVE_TICKS / 2);
        if (phase > 1) {
            phase = 2 - phase;
        }
        return (int) Math.round(HOP_PEAK * Math.sin(phase * Math.PI));
    }

    private PetMetrics measurePet() {
        PetMetrics metrics = new PetMetrics();
        if (frames == null) {
            return metrics;
        }
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

    private java.awt.image.BufferedImage pickSprite() {
        int walkFrames = Math.max(1, frames.walkRightCount());
        switch (motion) {
            case WAVE:
                return frames.wave(Math.floorDiv(animTick, 2));
            case WALK:
            case RUN:
                return frames.walkRight(Math.floorDiv(animTick, motion == Motion.RUN ? 1 : 2));
            case INDETERMINATE:
                return frames.walkRight(Math.floorMod(indetFrame / 2, walkFrames));
            case IDLE:
            default:
                return frames.breath(0);
        }
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
