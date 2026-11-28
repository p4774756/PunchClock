package com.example.pet;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 萌寵操控：前進跳拋物線、原地跳、邊界與空中轉向限制。 */
public class Issue803DesktopPetControlJumpTest {

    @Test
    public void forwardJumpHasPositiveHorizontalTravelAndParabolicVerticalArc() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_D);
        control.startJump();
        int x = 0;
        int[] lifts = new int[DesktopPetControl.JUMP_TICKS];
        for (int tick = 0; tick < DesktopPetControl.JUMP_TICKS; tick++) {
            lifts[tick] = control.jumpLift();
            int dx = control.horizontalDelta();
            assertTrue("每格空中應持續前進", dx > 0);
            x += dx;
            control.tickJump();
        }
        assertTrue("前進跳水平位移應大於 0", x > 0);
        assertEquals(0, lifts[0]);
        int peakIdx = 0;
        for (int i = 1; i < lifts.length; i++) {
            if (lifts[i] > lifts[peakIdx]) {
                peakIdx = i;
            }
        }
        int mid = DesktopPetControl.JUMP_TICKS / 2;
        assertTrue("垂直軌跡頂點應近中段", peakIdx >= mid - 1 && peakIdx <= mid + 1);
        assertTrue(lifts[peakIdx] >= DesktopPetControl.JUMP_LIFT - 2);
        assertEquals(0, control.jumpLift());
    }

    @Test
    public void inPlaceJumpHasZeroHorizontalDeltaEachTick() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.startJump();
        for (int tick = 0; tick < DesktopPetControl.JUMP_TICKS; tick++) {
            assertEquals(0, control.horizontalDelta());
            control.tickJump();
        }
        assertEquals(0, control.horizontalDelta());
    }

    @Test
    public void jumpAtScreenEdgeDoesNotCrossBoundary() {
        Rectangle screen = new Rectangle(0, 0, 400, 300);
        Rectangle[] screens = {screen};
        int w = DesktopPetWindow.WIDTH;
        int h = DesktopPetWindow.HEIGHT;
        int startX = screen.x + screen.width - w;
        int baseY = screen.y + screen.height - h;
        int x = startX;
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_RIGHT);
        control.startJump();
        for (int tick = 0; tick < DesktopPetControl.JUMP_TICKS; tick++) {
            int dx = control.horizontalDelta();
            Point next = DesktopPetControl.clampedMove(
                    x, baseY, dx, control.jumpLift(), w, h, screens);
            assertTrue(next.x <= startX);
            assertEquals(startX, next.x);
            x = next.x;
            control.tickJump();
        }
    }

    @Test
    public void airReverseKeyDoesNotFlipHorizontalVelocity() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_RIGHT);
        control.startJump();
        int airDx = control.horizontalDelta();
        assertTrue(airDx > 0);
        control.applyKeyReleased(KeyEvent.VK_RIGHT);
        control.applyKeyPressed(KeyEvent.VK_LEFT);
        assertEquals(airDx, control.horizontalDelta());
    }
}