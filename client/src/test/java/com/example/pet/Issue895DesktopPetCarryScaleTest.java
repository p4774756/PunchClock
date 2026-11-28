package com.example.pet;

import org.junit.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 抓起／拖曳時不因傾角縮放而忽大忽小。
 */
public class Issue895DesktopPetCarryScaleTest {

    private static final int THRESHOLD = 24;
    private static final double SIZE_RATIO = 1.18;

    @Test
    public void grabAndStruggleFramesStaySimilarSizeAtZeroAndTenDegreeLean() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.stableShellFor(frames);
        DesktopPetAnimation grab = new DesktopPetAnimation();
        grab.setCarried(true);
        grab.tick();
        Rectangle upright = visible(frames, shell, grab, 0);
        Rectangle leaned = visible(frames, shell, grab, 10);
        assertSimilar("grab lean 0 vs 10", upright, leaned);

        for (int i = 0; i < DesktopPetAnimation.GRAB_TICKS + 8; i++) {
            grab.tick();
        }
        Rectangle struggle0 = visible(frames, shell, grab, 0);
        Rectangle struggle10 = visible(frames, shell, grab, 10);
        assertSimilar("struggle lean 0 vs 10", struggle0, struggle10);
        assertSimilar("grab vs struggle", upright, struggle0);
    }

    private static Rectangle visible(
            DesktopPetSpriteLoader.Frames frames,
            Rectangle shell,
            DesktopPetAnimation animation,
            double leanDegrees) {
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        double radians = Math.toRadians(leanDegrees);
        double pivotX = view.width / 2.0;
        double pivotY = view.height / 3.0;
        // 與修正後的拖曳路徑相同：scale 永遠為 1。
        BufferedImage image = DesktopPetWindow.paintMiluViewport(
                view, frames, animation, false, pivotX, pivotY, radians, 1.0);
        return DesktopPetWindow.opaqueBounds(image, THRESHOLD);
    }

    private static void assertSimilar(String label, Rectangle a, Rectangle b) {
        assertTrue(label + " expected a visible body " + a, a.width > 8 && a.height > 8);
        assertTrue(label + " expected a visible body " + b, b.width > 8 && b.height > 8);
        double wr = a.width / (double) b.width;
        double hr = a.height / (double) b.height;
        assertTrue(label + " width jumped " + a.width + " vs " + b.width,
                wr <= SIZE_RATIO && (1 / wr) <= SIZE_RATIO);
        assertTrue(label + " height jumped " + a.height + " vs " + b.height,
                hr <= SIZE_RATIO && (1 / hr) <= SIZE_RATIO);
    }
}
