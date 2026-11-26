package com.example.pet;

import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Issue674DesktopPetScaleTest {

    @Test
    public void idleSitSleepWalkShareGroundLineAndHeadBand() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        DesktopPetMiluLayout.Placement idle =
                DesktopPetMiluLayout.placement(spriteNamed(frames, "idle/breath_01"));
        DesktopPetMiluLayout.Placement sit =
                DesktopPetMiluLayout.placement(spriteNamed(frames, "sit/01"));
        DesktopPetMiluLayout.Placement sleep =
                DesktopPetMiluLayout.placement(spriteNamed(frames, "sleep/01"));
        DesktopPetAnimation walkAnim = new DesktopPetAnimation();
        walkAnim.forceWalk(1);
        DesktopPetMiluLayout.Placement walk =
                DesktopPetMiluLayout.placement(frames.spriteFor(walkAnim));

        assertEquals(idle.footDisplayY, sit.footDisplayY);
        assertEquals(idle.footDisplayY, sleep.footDisplayY);
        assertEquals(idle.footDisplayY, walk.footDisplayY);
        int standHeadSpread =
                Math.max(Math.max(idle.headDisplayTop, sit.headDisplayTop), walk.headDisplayTop)
                        - Math.min(Math.min(idle.headDisplayTop, sit.headDisplayTop), walk.headDisplayTop);
        assertTrue(standHeadSpread <= DesktopPetMiluLayout.HEAD_TOP_TOLERANCE);
    }

    @Test
    public void deviceBuffersCoverSameLogicalAreaAtOneAndTwoScale() {
        int lw = 176;
        int lh = 188;
        for (double pixelScale : new double[] {1, 2}) {
            int bw = DesktopPetMiluLayout.bufferPixelWidth(lw, pixelScale);
            int bh = DesktopPetMiluLayout.bufferPixelHeight(lh, pixelScale);
            Rectangle area =
                    DesktopPetMiluLayout.logicalCoverage(lw, lh, bw, bh, pixelScale);
            assertEquals(lw, area.width);
            assertEquals(lh, area.height);
        }
    }

    @Test
    public void defaultCareValuesWalkWithinIdleWindow() {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.setCareIdle(true, DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT);
        boolean walked = false;
        for (int i = 0; i <= DesktopPetAnimation.IDLE_TICKS; i++) {
            animation.tick();
            if (animation.mode() == DesktopPetAnimation.Mode.WALK) {
                walked = true;
                assertTrue(animation.stepX() != 0);
                break;
            }
        }
        assertTrue(walked);
    }

    @Test
    public void lowCareStillSometimesWalks() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new java.util.Random(17));
        animation.setCareIdle(true, 12, 30, 80);
        boolean walked = false;
        for (int i = 0; i < 600; i++) {
            animation.tick();
            if (animation.mode() == DesktopPetAnimation.Mode.WALK) {
                walked = true;
                break;
            }
        }
        assertTrue(walked);
    }

    @Test
    public void loadingShellMatchesStableUnionBeforeFramesArrive() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle estimate = DesktopPetWindow.estimatedMiluShell();
        Rectangle stable = DesktopPetWindow.stableShellFor(frames);
        assertEquals(stable, estimate);
    }

    private static DesktopPetSpriteLoader.Sprite spriteNamed(
            DesktopPetSpriteLoader.Frames frames, String name) {
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            if (name.equals(sprite.name)) {
                return sprite;
            }
        }
        throw new AssertionError("missing sprite " + name);
    }
}