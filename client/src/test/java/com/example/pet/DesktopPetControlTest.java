package com.example.pet;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DesktopPetControlTest {

    @Test
    public void enterExitAndHint() {
        DesktopPetControl control = new DesktopPetControl();
        assertFalse(control.isActive());
        assertNull(control.hintText());
        control.enter();
        assertTrue(control.isActive());
        assertEquals(DesktopPetControl.HINT, control.hintText());
        control.exit();
        assertFalse(control.isActive());
        assertNull(control.hintText());
    }

    @Test
    public void keyActionMapsArrowsWasdSpaceAndEsc() {
        assertEquals(DesktopPetControl.KeyAction.WALK_LEFT, DesktopPetControl.keyAction(KeyEvent.VK_LEFT));
        assertEquals(DesktopPetControl.KeyAction.WALK_LEFT, DesktopPetControl.keyAction(KeyEvent.VK_A));
        assertEquals(DesktopPetControl.KeyAction.WALK_RIGHT, DesktopPetControl.keyAction(KeyEvent.VK_RIGHT));
        assertEquals(DesktopPetControl.KeyAction.WALK_RIGHT, DesktopPetControl.keyAction(KeyEvent.VK_D));
        assertEquals(DesktopPetControl.KeyAction.JUMP, DesktopPetControl.keyAction(KeyEvent.VK_SPACE));
        assertEquals(DesktopPetControl.KeyAction.EXIT, DesktopPetControl.keyAction(KeyEvent.VK_ESCAPE));
        assertEquals(DesktopPetControl.KeyAction.NONE, DesktopPetControl.keyAction(KeyEvent.VK_Q));
    }

    @Test
    public void walkKeysSetDirectionAndReleaseClears() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_RIGHT);
        assertEquals(1, control.walkDirection());
        assertEquals(DesktopPetAnimation.STEP, control.horizontalDelta());
        control.applyKeyPressed(KeyEvent.VK_LEFT);
        assertEquals(0, control.walkDirection());
        assertEquals(0, control.horizontalDelta());
        control.applyKeyReleased(KeyEvent.VK_RIGHT);
        control.applyKeyPressed(KeyEvent.VK_A);
        assertEquals(-1, control.walkDirection());
        assertEquals(-DesktopPetAnimation.STEP, control.horizontalDelta());
    }

    @Test
    public void jumpArcPeaksThenLandsAndBlocksWalk() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_RIGHT);
        control.startJump();
        assertTrue(control.isJumping());
        assertEquals(0, control.horizontalDelta());
        int peak = 0;
        for (int tick = 0; tick < DesktopPetControl.JUMP_TICKS; tick++) {
            peak = Math.max(peak, control.jumpLift());
            control.tickJump();
        }
        assertFalse(control.isJumping());
        assertEquals(0, control.jumpLift());
        assertTrue(peak >= DesktopPetControl.JUMP_LIFT - 2);
        assertEquals(DesktopPetAnimation.STEP, control.horizontalDelta());
    }

    @Test
    public void escExitsControl() {
        DesktopPetControl control = new DesktopPetControl();
        control.enter();
        control.applyKeyPressed(KeyEvent.VK_ESCAPE);
        assertFalse(control.isActive());
    }

    @Test
    public void clampedMoveStaysInsideScreenHorizontallyAndJumpVertically() {
        Rectangle screen = new Rectangle(0, 0, 400, 300);
        Rectangle[] screens = {screen};
        int w = DesktopPetWindow.WIDTH;
        int h = DesktopPetWindow.HEIGHT;
        Point edge = DesktopPetControl.clampedMove(
                screen.x + screen.width - w, 200, DesktopPetAnimation.STEP, 0, w, h, screens);
        assertEquals(screen.x + screen.width - w, edge.x);
        Point blocked = DesktopPetControl.clampedMove(
                screen.x + screen.width - w, 200, DesktopPetAnimation.STEP, 0, w, h, screens);
        assertEquals(screen.x + screen.width - w, blocked.x);
        Point jumpTop = DesktopPetControl.clampedMove(
                100, screen.y, 0, DesktopPetControl.JUMP_LIFT, w, h, screens);
        assertEquals(screen.y, jumpTop.y);
        Point jumpFloor = DesktopPetControl.clampedMove(
                100, screen.y + screen.height - h, 0, 0, w, h, screens);
        assertEquals(screen.y + screen.height - h, jumpFloor.y);
    }

    @Test
    public void animationResumesAutonomyAfterPlayerControl() {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        animation.forceWalk(1);
        animation.setPlayerControl(true);
        for (int i = 0; i < DesktopPetAnimation.WALK_TICKS + 5; i++) {
            animation.tick();
        }
        assertEquals(DesktopPetAnimation.Mode.WALK, animation.mode());
        animation.setPlayerControl(false);
        assertEquals(DesktopPetAnimation.Mode.IDLE, animation.mode());
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS + DesktopPetAnimation.WALK_TICKS; i++) {
            animation.tick();
        }
        assertTrue(animation.mode() == DesktopPetAnimation.Mode.IDLE
                || animation.mode() == DesktopPetAnimation.Mode.WALK);
    }

    @Test
    public void controlWindowFocusAndKeys() throws Exception {
        org.junit.Assume.assumeFalse(
                "headless JVM cannot show a pet", java.awt.GraphicsEnvironment.isHeadless());
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
            org.junit.Assert.assertNotNull(frames);
            DesktopPetWindow pet = new DesktopPetWindow(new DesktopPetSettings(new TestPreferences()));
            try {
                pet.useFrames(frames);
                pet.showPet();
                pet.setLocation(120, 160);
                assertFalse(pet.getFocusableWindowState());
                pet.enterControlMode();
                assertTrue(pet.controlModeActive());
                assertTrue(pet.getFocusableWindowState());
                assertTrue(pet.stage().isFocusable());
                int baseY = pet.getY();
                pet.control().applyKeyPressed(KeyEvent.VK_D);
                pet.tickOnce();
                assertTrue(pet.getX() > 120);
                assertEquals(DesktopPetAnimation.Mode.WALK, pet.animation().mode());
                assertTrue(pet.animation().facingRight());
                pet.control().applyKeyReleased(KeyEvent.VK_D);
                pet.tickOnce();
                pet.control().applyKeyPressed(KeyEvent.VK_SPACE);
                int jumpedY = pet.getY();
                for (int i = 0; i < DesktopPetControl.JUMP_TICKS / 2; i++) {
                    pet.tickOnce();
                }
                assertTrue(pet.getY() < jumpedY);
                for (int i = 0; i < DesktopPetControl.JUMP_TICKS; i++) {
                    pet.tickOnce();
                }
                assertEquals(baseY, pet.getY());
                pet.control().applyKeyPressed(KeyEvent.VK_ESCAPE);
                pet.exitControlMode();
                assertFalse(pet.controlModeActive());
                assertFalse(pet.getFocusableWindowState());
                assertFalse(pet.stage().isFocusable());
            } finally {
                pet.disposePet();
            }
        });
    }

    private static final class TestPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();

        TestPreferences() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            values.clear();
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return new TestPreferences();
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }
}