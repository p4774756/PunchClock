package com.example.pet;

import com.example.pet.DesktopPetAnimation.Mode;
import org.junit.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 桌面萌寵換動作與連續繪製時外框穩定、角色不會整張變透明。 */
public class Issue556DesktopPetStabilityTest {

    @Test
    public void viewForPoseStableAcrossModeSwitches() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        Rectangle baseline = DesktopPetWindow.viewForPose(frames, shell, animation);
        assertViewStableAcrossMode(frames, shell, baseline, a -> { /* idle */ });
        assertViewStableAcrossMode(frames, shell, baseline, DesktopPetAnimation::poke);
        assertViewStableAcrossMode(frames, shell, baseline, a -> a.forceWalk(1));
        assertViewStableAcrossMode(frames, shell, baseline, a -> a.forceWalk(-1));
        assertViewStableAcrossMode(frames, shell, baseline, DesktopPetAnimation::forceSleep);
        assertViewStableAcrossMode(frames, shell, baseline, a -> a.setCursorHold(Mode.THINK));
        assertViewStableAcrossMode(frames, shell, baseline, a -> a.setCursorHold(Mode.BUSY));
    }

    private static void assertViewStableAcrossMode(
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            Rectangle baseline,
            Consumer<DesktopPetAnimation> arm) {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        arm.accept(animation);
        for (int t = 0; t < 200; t++) {
            animation.tick();
            Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
            assertEquals("mode " + animation.mode() + " tick " + t, baseline, view);
        }
    }

    @Test
    public void paintedCharacterStaysOpaqueAcrossTicksAndModes() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        exercisePaint(frames, shell, animation);
        animation.poke();
        exercisePaint(frames, shell, animation);
        animation.forceWalk(1);
        exercisePaint(frames, shell, animation);
        animation.forceSleep();
        exercisePaint(frames, shell, animation);
    }

    private static void exercisePaint(
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            DesktopPetAnimation animation) {
        for (int t = 0; t < 120; t++) {
            animation.tick();
            Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
            BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, animation, false);
            assertTrue(
                    "mode " + animation.mode() + " tick " + t,
                    centerCharacterAlpha(shot, view) > 16);
        }
    }

    private static int centerCharacterAlpha(BufferedImage shot, Rectangle view) {
        int size = DesktopPetSpriteLoader.DISPLAY;
        int spriteX = (DesktopPetWindow.WIDTH - size) / 2;
        int spriteY = DesktopPetWindow.BUBBLE_SLOT;
        int cx = spriteX + size / 2 - view.x;
        int cy = spriteY + size / 2 - view.y;
        if (cx < 0 || cy < 0 || cx >= shot.getWidth() || cy >= shot.getHeight()) {
            return 0;
        }
        return (shot.getRGB(cx, cy) >>> 24) & 0xff;
    }
}