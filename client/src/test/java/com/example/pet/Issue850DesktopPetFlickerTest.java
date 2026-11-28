package com.example.pet;

import org.junit.Test;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 待機呼吸／眨眼／走路與操控跳躍連續擷幀不得空白或接近全透明；視野外框在同一姿勢內穩定。 */
public class Issue850DesktopPetFlickerTest {

    private static final int OPAQUE_THRESHOLD = 16;

    @Test
    public void idleBreathFullCycleStaysOpaqueAcrossFrameBoundaries() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        int ticksPerBreath = DesktopPetAnimation.BREATH_HOLD_TICKS;
        int totalTicks = ticksPerBreath * 4 + ticksPerBreath;
        for (int t = 0; t < totalTicks; t++) {
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            assertFrameOpaque(frames, shell, animation, t, "breath");
        }
    }

    @Test
    public void blinkRoundStaysOpaqueAndViewStable() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        animation.armBlinkNow();
        int blinkTicks =
                DesktopPetAnimation.BLINK_HOLD_TICKS * 4 + DesktopPetAnimation.BLINK_HOLD_TICKS;
        for (int t = 0; t < blinkTicks; t++) {
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            assertFrameOpaque(frames, shell, animation, t, "blink");
        }
    }

    @Test
    public void walkSegmentStaysOpaque() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.forceWalk(1);
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        int ticks = DesktopPetAnimation.ACTION_HOLD_TICKS
                * Math.max(1, frames.walkLeftCount()) * 2;
        for (int t = 0; t < ticks; t++) {
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            assertFrameOpaque(frames, shell, animation, t, "walk");
        }
    }

    @Test
    public void controlJumpArcStaysOpaque() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.setPlayerControl(true);
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_D);
        control.startJump();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        for (int t = 0; t < DesktopPetControl.JUMP_TICKS; t++) {
            control.tickJump();
            animation.syncPlayerWalk(control.walkDirection());
            animation.tick();
            assertEquals(view, DesktopPetWindow.viewForPose(frames, shell, animation));
            assertFrameOpaque(frames, shell, animation, t, "jump");
        }
    }

    private static void assertFrameOpaque(
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            DesktopPetAnimation animation,
            int tick,
            String phase) {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, animation, false);
        try {
            DesktopPetWindow.assertOpaqueWithinViewport(shot, OPAQUE_THRESHOLD);
        } catch (AssertionError ex) {
            throw new AssertionError(phase + " tick " + tick + ": " + ex.getMessage(), ex);
        }
        int size = DesktopPetSpriteLoader.DISPLAY;
        int spriteX = (DesktopPetWindow.WIDTH - size) / 2;
        int spriteY = DesktopPetWindow.BUBBLE_SLOT + animation.bob();
        int cx = spriteX + size / 2 - view.x;
        int cy = spriteY + size / 2 - view.y;
        assertTrue(
                phase + " tick " + tick + " center alpha",
                centerAlpha(shot, cx, cy) > OPAQUE_THRESHOLD);
    }

    private static int centerAlpha(BufferedImage shot, int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= shot.getWidth() || cy >= shot.getHeight()) {
            return 0;
        }
        return (shot.getRGB(cx, cy) >>> 24) & 0xff;
    }
}