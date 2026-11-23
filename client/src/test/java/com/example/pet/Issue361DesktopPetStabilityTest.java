package com.example.pet;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 桌面萌寵睡覺／走路時視窗外框與點擊區穩定，不閃不跳。 */
public class Issue361DesktopPetStabilityTest {

    @Test
    public void viewForPoseStableWithinSleepLoop() {
        assertViewStableForMode(a -> a.forceSleep());
    }

    @Test
    public void viewForPoseStableWithinWalkLoop() {
        assertViewStableForMode(a -> a.forceWalk(1));
    }

    @Test
    public void viewForPoseStableWithinReactLoop() {
        assertViewStableForMode(DesktopPetAnimation::poke);
    }

    private static void assertViewStableForMode(java.util.function.Consumer<DesktopPetAnimation> arm) {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.shellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        arm.accept(animation);
        int loopCount = frames.sleepCount();
        if (animation.mode() == DesktopPetAnimation.Mode.WALK) {
            loopCount = Math.max(loopCount, frames.walkLeftCount());
        } else if (animation.mode() == DesktopPetAnimation.Mode.REACT) {
            loopCount = Math.max(loopCount, frames.waveCount());
        }
        int ticks = DesktopPetAnimation.ACTION_HOLD_TICKS * Math.max(1, loopCount) * 5;
        Rectangle first = null;
        for (int t = 0; t < ticks; t++) {
            animation.tick();
            Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
            if (first == null) {
                first = view;
            } else {
                assertEquals("tick " + t, first, view);
            }
        }
    }

    @Test
    public void placementStableWhenMainAtTopEdge() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.shellFor(frames);
        Rectangle usable = new Rectangle(0, 25, 1440, 875);
        Rectangle main = new Rectangle(0, 25, 900, 700);
        Rectangle[] screens = {usable};
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.forceSleep();
        Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
        int overlapX = main.x + 50;
        int overlapY = main.y + 50;
        Point loc = DesktopPetWindow.nudgeOutsideMainWindow(
                overlapX, overlapY, view.width, view.height, main, usable, screens);
        Rectangle placed = new Rectangle(loc.x, loc.y, view.width, view.height);
        assertFalse(placed.intersects(main));
        assertTrue(usable.contains(placed));
        assertEquals(loc, DesktopPetWindow.nudgeOutsideMainWindow(
                loc.x, loc.y, view.width, view.height, main, usable, screens));
        int stableX = loc.x;
        int stableY = loc.y;
        int stableW = view.width;
        int stableH = view.height;
        for (int t = 0; t < 100; t++) {
            animation.tick();
            Rectangle nextView = DesktopPetWindow.viewForPose(frames, shell, animation);
            int dx = nextView.x - view.x;
            int dy = nextView.y - view.y;
            if (!nextView.equals(view)) {
                view = nextView;
                loc.x += dx;
                loc.y += dy;
            }
            assertEquals(stableX, loc.x);
            assertEquals(stableY, loc.y);
            assertEquals(stableW, view.width);
            assertEquals(stableH, view.height);
            Rectangle box = new Rectangle(loc.x, loc.y, view.width, view.height);
            assertFalse(box.intersects(main));
            assertTrue(usable.contains(box));
        }
    }

    @Test
    public void spriteHitStableAcrossTicks() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        Rectangle shell = DesktopPetWindow.shellFor(frames);
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.forceSleep();
        int size = DesktopPetSpriteLoader.DISPLAY;
        int ticks = DesktopPetAnimation.ACTION_HOLD_TICKS * frames.sleepCount() * 5;
        for (int t = 0; t < ticks; t++) {
            animation.tick();
            Rectangle view = DesktopPetWindow.viewForPose(frames, shell, animation);
            int spriteX = (DesktopPetWindow.WIDTH - size) / 2;
            int spriteY = DesktopPetWindow.BUBBLE_SLOT + animation.bob();
            int cx = spriteX + size / 2 - view.x;
            int cy = spriteY + size / 2 - view.y;
            assertTrue(
                    "tick " + t + " view=" + view + " center=" + cx + "," + cy,
                    DesktopPetWindow.hitMiluAtStage(view, animation, cx, cy));
        }
    }
}