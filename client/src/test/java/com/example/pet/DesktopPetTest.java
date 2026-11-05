package com.example.pet;

import com.example.pet.DesktopPetAnimation.Mode;
import com.example.ui.CandySwitch;
import com.example.ui.PanelFactory;
import com.example.ui.UiFonts;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DesktopPetTest {

    @Test
    public void settingsRoundTripThroughPreferences() {
        MemoryPreferences prefs = new MemoryPreferences();
        DesktopPetSettings settings = new DesktopPetSettings(prefs);
        assertFalse(settings.isEnabled());
        assertFalse(settings.hasPosition());

        settings.setEnabled(true);
        settings.savePosition(-20, 480);
        DesktopPetSettings again = new DesktopPetSettings(prefs);
        assertTrue(again.isEnabled());
        assertTrue(again.hasPosition());
        assertEquals(-20, again.x());
        assertEquals(480, again.y());
        assertEquals("true", prefs.get(DesktopPetSettings.KEY_ENABLED, null));
        assertTrue(again.notifyMessage());
        assertTrue(again.notifyFile());
        assertTrue(again.notifyTask());
        assertFalse(again.notifySound());
        again.setNotifyMessage(false);
        again.setNotifyFile(false);
        again.setNotifyTask(false);
        again.setNotifySound(true);
        DesktopPetSettings restored = new DesktopPetSettings(prefs);
        assertFalse(restored.notifyMessage());
        assertFalse(restored.notifyFile());
        assertFalse(restored.notifyTask());
        assertTrue(restored.notifySound());
        assertEquals("false", prefs.get(DesktopPetSettings.KEY_NOTIFY_MESSAGE, null));
        assertEquals("false", prefs.get(DesktopPetSettings.KEY_NOTIFY_FILE, null));
        assertEquals("false", prefs.get(DesktopPetSettings.KEY_NOTIFY_TASK, null));
        assertEquals("true", prefs.get(DesktopPetSettings.KEY_NOTIFY_SOUND, null));

        again.setEnabled(false);
        assertFalse(new DesktopPetSettings(prefs).isEnabled());
    }

    @Test
    public void animationIdlesThenWalksBlinksAndReacts() {
        DesktopPetAnimation animation = new DesktopPetAnimation();
        assertEquals(Mode.IDLE, animation.mode());
        assertEquals(0, animation.stepX());
        assertNull(animation.bubble());

        for (int i = 0; i < 21; i++) {
            animation.tick();
        }
        assertEquals(2, animation.column());
        assertEquals(0, animation.row());

        DesktopPetAnimation walker = new DesktopPetAnimation();
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            walker.tick();
        }
        assertEquals(Mode.WALK, walker.mode());
        assertEquals(DesktopPetAnimation.STEP, walker.stepX());
        assertTrue(walker.facingRight());

        for (int i = 0; i < DesktopPetAnimation.WALK_TICKS; i++) {
            walker.tick();
        }
        assertEquals(Mode.IDLE, walker.mode());
        assertEquals(0, walker.stepX());

        walker.poke();
        assertEquals(Mode.REACT, walker.mode());
        assertEquals("嗨", walker.bubble());
        assertEquals(0, walker.stepX());
        assertEquals(1, walker.row());
        for (int i = 0; i < DesktopPetAnimation.REACT_TICKS; i++) {
            walker.tick();
        }
        assertEquals(Mode.IDLE, walker.mode());
        assertEquals("嗨", walker.bubble());
        for (int i = 0; i < DesktopPetAnimation.BUBBLE_TICKS - DesktopPetAnimation.REACT_TICKS; i++) {
            walker.tick();
        }
        assertNull(walker.bubble());

        walker.poke();
        assertEquals("我在這", walker.bubble());
        walker.turnAround();
        walker.forceWalk(-1);
        assertFalse(walker.facingRight());
        assertEquals(-DesktopPetAnimation.STEP, walker.stepX());

        walker.setCarried(true);
        assertEquals(0, walker.stepX());
        assertEquals(3, walker.column());
        walker.poke();
        assertEquals("我在這", walker.bubble());
        walker.tick();
        assertEquals(0, walker.stepX());
        walker.setCarried(false);
        assertEquals(Mode.IDLE, walker.mode());
        assertEquals(0, walker.stepX());
    }

    @Test
    public void placementStaysOnAScreenAndBouncesAtTheEdge() {
        Rectangle screen = new Rectangle(0, 0, 1440, 900);
        Point spot = DesktopPetWindow.defaultSpot(screen, 220, 210);
        assertEquals(1440 - 220 - DesktopPetWindow.MARGIN, spot.x);
        assertEquals(900 - 210 - DesktopPetWindow.MARGIN, spot.y);

        Point tiny = DesktopPetWindow.defaultSpot(new Rectangle(10, 20, 100, 80), 220, 210);
        assertEquals(10, tiny.x);
        assertEquals(20, tiny.y);

        Rectangle[] screens = {
                new Rectangle(0, 0, 800, 600),
                new Rectangle(800, 0, 800, 600)
        };
        Point inside = DesktopPetWindow.clampToScreens(100, 80, 220, 210, screens);
        assertEquals(100, inside.x);
        assertEquals(80, inside.y);
        Point second = DesktopPetWindow.clampToScreens(1500, 100, 220, 210, screens);
        assertEquals(1600 - 220, second.x);
        Point lost = DesktopPetWindow.clampToScreens(-5000, -4000, 220, 210, screens);
        assertEquals(0, lost.x);
        assertEquals(0, lost.y);
        Point untouched = DesktopPetWindow.clampToScreens(3, 4, 10, 10, new Rectangle[0]);
        assertEquals(3, untouched.x);

        DesktopPetWindow.HorizontalStep forward = DesktopPetWindow.stepHorizontal(0, 2, 0, 100);
        assertEquals(2, forward.x);
        assertFalse(forward.bounce);
        DesktopPetWindow.HorizontalStep hit = DesktopPetWindow.stepHorizontal(0, -2, 0, 100);
        assertEquals(0, hit.x);
        assertTrue(hit.bounce);
        DesktopPetWindow.HorizontalStep returning = DesktopPetWindow.stepHorizontal(-10, 2, 0, 100);
        assertEquals(0, returning.x);
        assertFalse(returning.bounce);
    }

    @Test
    public void settingsPageHasDesktopPetSwitch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            JTabbedPane tabs = PanelFactory.createServerConfigBody(
                    refs,
                    UiFonts.chinesePlain(13),
                    UiFonts.chineseBold(13),
                    UiFonts.latinPlain(13));
            assertTrue(refs.desktopPetSwitch instanceof CandySwitch);
            assertFalse(refs.desktopPetSwitch.isSelected());
            assertEquals("桌面寵物", refs.desktopPetSwitch.getAccessibleContext().getAccessibleName());
            assertTrue(refs.desktopPetSwitch.getToolTipText().contains("最小化"));
            assertTrue(tabs.getToolTipTextAt(1).contains("桌面寵物"));
            assertTrue(refs.desktopPetNotifyMessage.isSelected());
            assertTrue(refs.desktopPetNotifyFile.isSelected());
            assertTrue(refs.desktopPetNotifyTask.isSelected());
            assertFalse(refs.desktopPetNotifySound.isSelected());
            assertEquals("新訊息提醒", refs.desktopPetNotifyMessage.getAccessibleContext().getAccessibleName());
            assertEquals("檔案傳輸提醒", refs.desktopPetNotifyFile.getAccessibleContext().getAccessibleName());
            assertEquals("任務與排程提醒", refs.desktopPetNotifyTask.getAccessibleContext().getAccessibleName());
            assertEquals("提示音", refs.desktopPetNotifySound.getAccessibleContext().getAccessibleName());
        });
    }

    @Test
    public void disableAndShutdownDoNotRequireAWindow() {
        MemoryPreferences prefs = new MemoryPreferences();
        DesktopPetSettings settings = new DesktopPetSettings(prefs);
        DesktopPetManager manager = new DesktopPetManager(settings);
        assertTrue(manager.setEnabled(false));
        assertFalse(manager.savedEnabled());
        assertNull(manager.window());

        settings.setEnabled(true);
        manager.shutdown();
        assertTrue(settings.isEnabled());
        assertFalse(manager.setEnabled(false));
        assertTrue(settings.isEnabled());
        assertNull(manager.window());
    }

    @Test
    public void miluBreathThenBlinkThenBackToBreath() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new Random(2));
        assertTrue(animation.ticksUntilBlink() >= DesktopPetAnimation.BLINK_MIN_TICKS);
        assertTrue(animation.ticksUntilBlink() <= DesktopPetAnimation.BLINK_MAX_TICKS);
        assertEquals(0, animation.miluBreathIndex());
        for (int frame = 1; frame <= 3; frame++) {
            for (int i = 0; i < DesktopPetAnimation.BREATH_HOLD_TICKS; i++) {
                animation.tick();
            }
            assertEquals(frame, animation.miluBreathIndex());
            assertFalse(animation.miluBlinking());
        }
        for (int i = 0; i < DesktopPetAnimation.BREATH_HOLD_TICKS; i++) {
            animation.tick();
        }
        assertEquals(0, animation.miluBreathIndex());

        DesktopPetAnimation blink = new DesktopPetAnimation(new Random(2));
        blink.armBlinkNow();
        blink.tick();
        assertEquals(0, blink.miluBlinkStep());
        assertBlinkStep(blink, 1);
        assertBlinkStep(blink, 2);
        assertBlinkStep(blink, 3);
        for (int i = 0; i < DesktopPetAnimation.BLINK_HOLD_TICKS; i++) {
            blink.tick();
        }
        assertFalse(blink.miluBlinking());
        assertEquals(0, blink.miluBreathIndex());

        blink.poke();
        assertEquals("嗨", blink.bubble());
        assertFalse(blink.miluBlinking());
        blink.forceWalk(-1);
        assertEquals(-DesktopPetAnimation.STEP, blink.stepX());
        assertEquals(0, blink.miluBreathIndex());
        blink.setCarried(true);
        blink.tick();
        assertEquals(0, blink.stepX());
        assertEquals(0, blink.miluBreathIndex());
        assertFalse(blink.miluBlinking());
    }

    private static void assertBlinkStep(DesktopPetAnimation animation, int step) {
        for (int i = 0; i < DesktopPetAnimation.BLINK_HOLD_TICKS; i++) {
            animation.tick();
        }
        assertTrue(animation.miluBlinking());
        assertEquals(step, animation.miluBlinkStep());
    }

    @Test
    public void miluIdleFramesAreTransparentAndAligned() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        BufferedImage[] all = {
                frames.breath(0), frames.breath(1), frames.breath(2), frames.breath(3),
                frames.blink1(), frames.blink2()
        };
        int[] first = null;
        for (BufferedImage image : all) {
            assertEquals(DesktopPetSpriteLoader.SOURCE, image.getWidth());
            assertEquals(DesktopPetSpriteLoader.SOURCE, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            int[] box = DesktopPetSpriteLoader.contentBounds(image);
            assertEquals(320, box[3] - box[1] + 1);
            if (first == null) {
                first = box;
            }
            assertTrue(Math.abs(box[1] - first[1]) <= 2);
            assertEquals(first[3], box[3]);
            assertTrue(Math.abs(box[0] - first[0]) <= 4);
            assertTrue(Math.abs(box[2] - first[2]) <= 4);
        }
        BufferedImage scaled = new BufferedImage(
                DesktopPetSpriteLoader.DISPLAY, DesktopPetSpriteLoader.DISPLAY, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        DesktopPetSpriteLoader.draw(graphics, frames.breath(0), 0, 0, DesktopPetSpriteLoader.DISPLAY);
        graphics.dispose();
        assertEquals(0, scaled.getRGB(0, 0) >>> 24);
        int[] scaledBox = DesktopPetSpriteLoader.contentBounds(scaled);
        int height = scaledBox[3] - scaledBox[1] + 1;
        assertTrue(height >= 150 && height <= 170);

        BufferedImage opaque = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        assertFalse(DesktopPetSpriteLoader.acceptable(opaque));
        BufferedImage small = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        assertFalse(DesktopPetSpriteLoader.acceptable(small));
    }

    @Test
    public void actionClipsKeepCleanFramesAndFallBackToIdle() throws Exception {
        assertTrue(DesktopPetSpriteLoader.usable(
                DesktopPetSpriteLoader.read("/pet/milu/idle/breath_01.png")));
        String[] clean = {
                "/pet/milu/walk/left_01.png",
                "/pet/milu/walk/left_02.png",
                "/pet/milu/walk/left_03.png",
                "/pet/milu/walk/left_04.png",
                "/pet/milu/walk/right_01.png",
                "/pet/milu/walk/right_02.png",
                "/pet/milu/walk/right_03.png",
                "/pet/milu/walk/right_04.png",
                "/pet/milu/wave/01.png",
                "/pet/milu/wave/02.png",
                "/pet/milu/wave/03.png",
                "/pet/milu/wave/04.png",
                "/pet/milu/sit/01.png",
                "/pet/milu/sit/02.png",
                "/pet/milu/sit/blink_01.png",
                "/pet/milu/sleep/01.png",
                "/pet/milu/sleep/02.png"
        };
        String[] rejected = {
                "/pet/milu/drag/01.png",
                "/pet/milu/drag/02.png"
        };
        for (String path : clean) {
            assertTrue(path, DesktopPetSpriteLoader.usable(DesktopPetSpriteLoader.read(path)));
        }
        for (String path : rejected) {
            assertFalse(path, DesktopPetSpriteLoader.usable(DesktopPetSpriteLoader.read(path)));
        }

        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        assertEquals(4, frames.walkLeftCount());
        assertEquals(4, frames.walkRightCount());
        assertEquals(4, frames.waveCount());
        assertEquals(3, frames.sitCount());
        assertEquals(2, frames.sleepCount());
        assertEquals(0, frames.dragCount());
        assertNotSame(frames.walkLeft(0), frames.walkRight(0));

        DesktopPetAnimation walker = new DesktopPetAnimation(new Random(1));
        walker.forceWalk(-1);
        assertSame(frames.walkLeft(0), frames.imageFor(walker));
        for (int i = 0; i < DesktopPetAnimation.ACTION_HOLD_TICKS; i++) {
            walker.tick();
        }
        assertSame(frames.walkLeft(1), frames.imageFor(walker));
        walker.forceWalk(1);
        assertSame(frames.walkRight(0), frames.imageFor(walker));
        walker.poke();
        assertEquals(Mode.REACT, walker.mode());
        assertSame(frames.wave(0), frames.imageFor(walker));

        walker.setCarried(true);
        assertTrue(isMiluIdle(frames, frames.imageFor(walker)));
        walker.setCarried(false);
        assertEquals(Mode.IDLE, walker.mode());
        assertTrue(isMiluIdle(frames, frames.imageFor(walker)));

        DesktopPetAnimation resting = new DesktopPetAnimation(new ZeroRandom());
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            resting.tick();
        }
        assertEquals(Mode.WALK, resting.mode());
        for (int i = 0; i < DesktopPetAnimation.WALK_TICKS; i++) {
            resting.tick();
        }
        assertEquals(Mode.IDLE, resting.mode());
        for (int i = 0; i < DesktopPetAnimation.IDLE_TICKS; i++) {
            resting.tick();
        }
        assertEquals(Mode.SIT, resting.mode());
        assertEquals(0, resting.stepX());
        assertSame(frames.sit(0), frames.imageFor(resting));
        for (int i = 0; i < DesktopPetAnimation.SIT_TICKS; i++) {
            resting.tick();
        }
        assertEquals(Mode.SLEEP, resting.mode());
        assertSame(frames.sleep(0), frames.imageFor(resting));
        resting.poke();
        assertEquals(Mode.REACT, resting.mode());
        assertSame(frames.wave(0), frames.imageFor(resting));
    }

    @Test
    public void anchorsKeepTheBodyOnOneGroundPoint() {
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        int leftMin = Integer.MAX_VALUE;
        int leftMax = Integer.MIN_VALUE;
        int sitShift = 0;
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            assertEquals(sprite.name, DesktopPetSpriteLoader.ANCHOR_X, sprite.placedX());
            assertEquals(sprite.name, DesktopPetSpriteLoader.ANCHOR_Y, sprite.placedY());
            if (sprite.name.startsWith("idle/")) {
                assertTrue(sprite.name, Math.abs(sprite.offsetX) <= 6);
                assertEquals(sprite.name, 0, sprite.offsetY);
            }
            if (sprite.name.startsWith("walk/left_")) {
                leftMin = Math.min(leftMin, sprite.offsetX);
                leftMax = Math.max(leftMax, sprite.offsetX);
            }
            if (sprite.name.startsWith("sit/")) {
                assertTrue(sprite.name, sprite.offsetY >= 30);
                sitShift++;
            }
            if (sprite.name.startsWith("sleep/")) {
                assertTrue(sprite.name, Math.abs(sprite.offsetX) <= 24);
                assertTrue(sprite.name, sprite.offsetY >= 30);
            }
        }
        assertTrue(leftMax - leftMin >= 20);
        assertEquals(3, sitShift);

        DesktopPetAnimation idle = new DesktopPetAnimation(new Random(2));
        int home = frames.spriteFor(idle).placedX();
        for (int i = 0; i < 600; i++) {
            idle.tick();
            if (idle.mode() != Mode.IDLE) {
                break;
            }
            assertEquals(home, frames.spriteFor(idle).placedX());
            assertEquals(0, idle.stepX());
        }

        DesktopPetAnimation walker = new DesktopPetAnimation(new Random(1));
        walker.forceWalk(-1);
        int steps = 0;
        for (int i = 0; i < 4 * 10 * DesktopPetAnimation.ACTION_HOLD_TICKS; i++) {
            assertEquals(DesktopPetSpriteLoader.ANCHOR_X, frames.spriteFor(walker).placedX());
            steps += walker.stepX();
            walker.tick();
        }
        assertTrue(steps < 0);
        walker.forceWalk(1);
        steps = 0;
        for (int i = 0; i < 4 * 10 * DesktopPetAnimation.ACTION_HOLD_TICKS; i++) {
            assertEquals(DesktopPetSpriteLoader.ANCHOR_X, frames.spriteFor(walker).placedX());
            steps += walker.stepX();
            walker.tick();
        }
        assertTrue(steps > 0);
    }

    @Test
    public void headlessEnableRemembersTheChoiceWithoutAWindow() {
        Assume.assumeTrue("this assertion is the headless fallback", GraphicsEnvironment.isHeadless());
        MemoryPreferences prefs = new MemoryPreferences();
        DesktopPetManager manager = new DesktopPetManager(new DesktopPetSettings(prefs));
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertFalse(manager.setEnabled(true));
        assertTrue(prefs.getBoolean(DesktopPetSettings.KEY_ENABLED, false));
        assertNull(manager.window());
        assertFalse(manager.isAnimating());
        assertFalse(manager.setEnabled(true));
        assertNull(manager.window());
    }

    @Test
    public void oneWindowSurvivesToggleMinimizeAndPointer() throws Exception {
        Assume.assumeFalse("headless JVM cannot show a pet", GraphicsEnvironment.isHeadless());
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        SwingUtilities.invokeAndWait(() -> {
            MemoryPreferences prefs = new MemoryPreferences();
            DesktopPetSettings settings = new DesktopPetSettings(prefs);
            DesktopPetManager manager = new DesktopPetManager(settings);
            JFrame main = new JFrame("punchclock-pet-test");
            try {
                main.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                main.setAutoRequestFocus(false);
                main.setSize(240, 160);
                main.setLocation(40, 40);
                main.setVisible(true);

                assertTrue(manager.setEnabled(true));
                DesktopPetWindow pet = manager.window();
                assertNotNull(pet);
                pet.useFrames(frames);
                assertTrue(pet.usingMilu());
                assertSame(frames.breath(0), pet.currentImage());
                for (int i = 0; i < DesktopPetAnimation.BREATH_HOLD_TICKS; i++) {
                    pet.tickOnce();
                }
                assertSame(frames.breath(1), pet.currentImage());
                assertMiluPaintIsTransparent(pet);
                assertNull(pet.getOwner());
                assertTrue(pet.isVisible());
                assertTrue(manager.isAnimating());
                assertFalse(pet.getFocusableWindowState());
                assertEquals(java.awt.Window.Type.UTILITY, pet.getType());

                assertTrue(manager.setEnabled(true));
                assertSame(pet, manager.window());
                int listeners = pet.interactionListenerCount();

                Rectangle area = pet.usableBounds();
                int x = area.x + Math.max(0, (area.width - pet.getWidth()) / 2);
                int y = area.y + Math.max(0, (area.height - pet.getHeight()) / 2);
                pet.setLocation(x, y);
                pet.animation().forceWalk(1);
                pet.tickOnce();
                assertEquals(x + DesktopPetAnimation.STEP, pet.getX());

                pet.setLocation(area.x, y);
                pet.animation().forceWalk(-1);
                pet.tickOnce();
                assertEquals(area.x, pet.getX());
                assertTrue(pet.animation().facingRight());
                assertSame(frames.walkRight(0), pet.currentImage());

                Point origin = pet.getLocationOnScreen();
                pet.stage().dispatchEvent(mouse(
                        pet.stage(), MouseEvent.MOUSE_PRESSED, 20, 20, origin.x + 20, origin.y + 20,
                        0, 1, MouseEvent.BUTTON1));
                pet.stage().dispatchEvent(mouse(
                        pet.stage(), MouseEvent.MOUSE_DRAGGED, 48, 20, origin.x + 48, origin.y + 20,
                        MouseEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON));
                pet.stage().dispatchEvent(mouse(
                        pet.stage(), MouseEvent.MOUSE_RELEASED, 48, 20, origin.x + 48, origin.y + 20,
                        0, 1, MouseEvent.BUTTON1));
                assertNull(pet.animation().bubble());
                assertTrue(pet.getX() > area.x);
                assertEquals(Mode.IDLE, pet.animation().mode());
                assertTrue(isMiluIdle(frames, pet.currentImage()));

                int parked = pet.getX();
                pet.stage().dispatchEvent(mouse(
                        pet.stage(), MouseEvent.MOUSE_PRESSED, 16, 16, pet.getLocationOnScreen().x + 16,
                        pet.getLocationOnScreen().y + 16, 0, 1, MouseEvent.BUTTON1));
                pet.stage().dispatchEvent(mouse(
                        pet.stage(), MouseEvent.MOUSE_RELEASED, 16, 16, pet.getLocationOnScreen().x + 16,
                        pet.getLocationOnScreen().y + 16, 0, 1, MouseEvent.BUTTON1));
                assertEquals("嗨", pet.animation().bubble());
                assertTrue(pet.usingMilu());
                assertSame(frames.wave(0), pet.currentImage());
                pet.tickOnce();
                assertEquals(parked, pet.getX());

                assertTrue(manager.setEnabled(false));
                assertFalse(pet.isVisible());
                assertFalse(manager.isAnimating());
                assertFalse(settings.isEnabled());
                pet.tickOnce();
                assertFalse(pet.isVisible());

                assertTrue(manager.setEnabled(true));
                assertSame(pet, manager.window());
                assertTrue(pet.isVisible());
                assertEquals(listeners, pet.interactionListenerCount());
                assertTrue(settings.hasPosition());

                main.setExtendedState(Frame.ICONIFIED);
                assertTrue(pet.isVisible());
                assertTrue(manager.isAnimating());

                manager.shutdown();
                assertFalse(pet.isDisplayable());
                assertTrue(settings.isEnabled());
                assertFalse(manager.setEnabled(false));
                assertTrue(settings.isEnabled());
                assertNull(manager.window());
            } finally {
                manager.shutdown();
                main.dispose();
            }
        });
    }

    private static boolean isMiluIdle(DesktopPetSpriteLoader.Frames frames, BufferedImage image) {
        if (image == frames.blink1() || image == frames.blink2()) {
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (image == frames.breath(i)) {
                return true;
            }
        }
        return false;
    }

    private static void assertMiluPaintIsTransparent(DesktopPetWindow pet) {
        BufferedImage shot = new BufferedImage(pet.getWidth(), pet.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = shot.createGraphics();
        pet.stage().paint(graphics);
        graphics.dispose();
        assertEquals(0, shot.getRGB(1, 1) >>> 24);
        int opaque = 0;
        int top = DesktopPetWindow.BUBBLE_SLOT;
        int bottom = Math.min(shot.getHeight(), top + DesktopPetSpriteLoader.DISPLAY);
        for (int y = top; y < bottom; y++) {
            for (int x = 0; x < shot.getWidth(); x++) {
                if (((shot.getRGB(x, y) >>> 24) & 0xff) > 16) {
                    opaque++;
                }
            }
        }
        assertTrue(opaque > 1000);
    }

    private static MouseEvent mouse(
            java.awt.Component source, int id, int x, int y, int xAbs, int yAbs,
            int modifiers, int clickCount, int button) {
        return new MouseEvent(
                source, id, System.currentTimeMillis(), modifiers,
                x, y, xAbs, yAbs, clickCount, false, button);
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }

    /** 測試用的記憶體 {@link java.util.prefs.Preferences}，不寫進使用者的偏好檔。 */
    @Test
    public void noticeUsesWaveAndLastsAboutFourSeconds() {
        DesktopPetAnimation animation = new DesktopPetAnimation(new Random(1));
        assertFalse(animation.showNotice(""));
        assertTrue(animation.showNotice(DesktopPetEvent.MESSAGE_RECEIVED.phrase()));
        assertEquals(Mode.REACT, animation.mode());
        assertEquals("有新訊息喔～！", animation.bubble());
        assertTrue(animation.isNotice());
        animation.setCarried(true);
        assertFalse(animation.showNotice(DesktopPetEvent.FILE_RECEIVED.phrase()));
        assertEquals("有新訊息喔～！", animation.bubble());
        animation.setCarried(false);
        assertTrue(animation.isNotice());

        DesktopPetAnimation held = new DesktopPetAnimation(new Random(1));
        held.showNotice(DesktopPetEvent.TASK_COMPLETED.phrase());
        for (int i = 0; i < DesktopPetAnimation.NOTICE_TICKS - 1; i++) {
            held.tick();
        }
        assertEquals("任務完成！", held.bubble());
        held.tick();
        assertNull(held.bubble());
        assertFalse(held.isNotice());
        assertEquals("檔案收到啦！", DesktopPetEvent.FILE_RECEIVED.phrase());
        assertEquals("成功送達～！", DesktopPetEvent.FILE_SENT.phrase());
        assertEquals("時間到囉～！", DesktopPetEvent.SCHEDULE_REMINDER.phrase());
    }

    @Test
    public void bridgeFiltersCooldownQueueAndDoesNotReplay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MemoryPreferences prefs = new MemoryPreferences();
            DesktopPetSettings settings = new DesktopPetSettings(prefs);
            settings.setEnabled(true);
            FakeSurface surface = new FakeSurface();
            DesktopPetEventBridge bridge = new DesktopPetEventBridge(settings, surface);

            bridge.offerAt(DesktopPetEvent.MESSAGE_RECEIVED, null, 1_000L);
            assertEquals("有新訊息喔～！", surface.phrase);
            assertEquals(0, surface.sounds);
            assertEquals(0, bridge.waitingCount());

            bridge.offerAt(DesktopPetEvent.MESSAGE_RECEIVED, null, 1_500L);
            assertEquals(1, surface.shows);

            surface.ready = false;
            bridge.noticeEndedAt(1_600L);
            bridge.offerAt(DesktopPetEvent.FILE_RECEIVED, null, 2_000L);
            bridge.offerAt(DesktopPetEvent.FILE_RECEIVED, null, 2_100L);
            bridge.offerAt(DesktopPetEvent.FILE_SENT, null, 2_200L);
            bridge.offerAt(DesktopPetEvent.TASK_COMPLETED, null, 2_300L);
            bridge.offerAt(DesktopPetEvent.SCHEDULE_REMINDER, null, 2_400L);
            assertEquals(DesktopPetEventBridge.MAX_WAITING, bridge.waitingCount());

            surface.ready = true;
            bridge.pumpAt(3_000L);
            assertEquals("檔案收到啦！", surface.phrase);
            bridge.noticeEndedAt(3_100L);
            assertEquals("成功送達～！", surface.phrase);
            bridge.noticeEndedAt(3_200L);
            assertEquals("任務完成！", surface.phrase);
            bridge.noticeEndedAt(3_300L);
            assertEquals(0, bridge.waitingCount());

            settings.setNotifyFile(false);
            int blocked = surface.shows;
            bridge.offerAt(DesktopPetEvent.FILE_SENT, null, 8_000L);
            assertEquals(blocked, surface.shows);
            settings.setNotifyFile(true);

            settings.setNotifySound(true);
            int sounds = surface.sounds;
            bridge.offerAt(DesktopPetEvent.SCHEDULE_REMINDER, null, 8_000L);
            assertEquals("時間到囉～！", surface.phrase);
            assertEquals(sounds + 1, surface.sounds);
            bridge.offerAt(DesktopPetEvent.SCHEDULE_REMINDER, null, 8_100L);
            assertEquals(sounds + 1, surface.sounds);

            surface.phrase = null;
            surface.enabled = false;
            bridge.discard();
            bridge.offerAt(DesktopPetEvent.MESSAGE_RECEIVED, null, 9_000L);
            assertNull(surface.phrase);
            assertEquals(0, bridge.waitingCount());
            surface.enabled = true;
            bridge.pumpAt(9_100L);
            assertNull(surface.phrase);
            bridge.offerAt(DesktopPetEvent.MESSAGE_RECEIVED, null, 9_200L);
            assertEquals("有新訊息喔～！", surface.phrase);
        });
    }

    private static final class FakeSurface implements DesktopPetEventBridge.Surface {
        private boolean enabled = true;
        private boolean ready = true;
        private String phrase;
        private int shows;
        private int sounds;

        @Override
        public boolean petEnabled() {
            return enabled;
        }

        @Override
        public boolean readyForNotice() {
            return enabled && ready;
        }

        @Override
        public boolean showNotice(String phrase, Runnable open) {
            this.phrase = phrase;
            shows++;
            if (open != null) {
                open.run();
            }
            return true;
        }

        @Override
        public void playSound() {
            sounds++;
        }
    }

    static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();

        MemoryPreferences() {
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
            return new MemoryPreferences();
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }

        @Override
        public boolean isUserNode() {
            return true;
        }
    }
}
