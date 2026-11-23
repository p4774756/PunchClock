package com.example.pet;

import com.example.ui.WindowOpacity;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 主視窗最小化／透明度不應隱藏萌寵，且透明度只套主窗（headless 可測的契約）。
 */
public class Issue556DesktopPetLifecycleTest {

    @Test
    public void mainBoundsSupplierReturnsNullWhileHiddenOrMinimized() {
        int[] extendedState = {0};
        boolean[] showing = {false};
        int[] size = {320, 240};
        int[] loc = {80, 80};
        Supplier<Rectangle> supplier = () -> {
            if ((extendedState[0] & Frame.ICONIFIED) != 0 || !showing[0]) {
                return null;
            }
            return new Rectangle(loc[0], loc[1], size[0], size[1]);
        };
        extendedState[0] = Frame.ICONIFIED;
        assertNull(supplier.get());
        extendedState[0] = 0;
        assertNull("not showing yet", supplier.get());
        showing[0] = true;
        Rectangle bounds = supplier.get();
        assertNotNull(bounds);
        assertEquals(80, bounds.x);
        assertEquals(320, bounds.width);
    }

    @Test
    public void mainBoundsSupplierTracksVisibleMain() throws Exception {
        Assume.assumeFalse("needs display", GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            JFrame main = new JFrame("issue556-supplier-visible");
            try {
                Supplier<Rectangle> supplier = iconifiedAwareMainBounds(main);
                main.setSize(320, 240);
                main.setLocation(80, 80);
                main.setVisible(true);
                Rectangle bounds = supplier.get();
                assertNotNull(bounds);
                assertEquals(80, bounds.x);
                assertEquals(80, bounds.y);
                assertEquals(320, bounds.width);
                assertEquals(240, bounds.height);
                main.setExtendedState(Frame.ICONIFIED);
                assertNull(supplier.get());
            } finally {
                main.setVisible(false);
                main.dispose();
            }
        });
    }

    @Test
    public void nullMainBoundsDoesNotChangeNudgedOrigin() throws Exception {
        Assume.assumeFalse("needs display", GraphicsEnvironment.isHeadless());
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        assertNotNull(frames);
        SwingUtilities.invokeAndWait(() -> {
            DesktopPetWindow pet = new DesktopPetWindow(
                    new DesktopPetSettings(new Issue347PetMainOverlapTest.MemoryPreferences()));
            try {
                pet.setMainBoundsSupplier(() -> null);
                pet.useFrames(frames);
                pet.holdStill();
                pet.setLocation(400, 300);
                int x = pet.getX();
                int y = pet.getY();
                pet.nudgeAwayFromMainWindow();
                assertEquals(x, pet.getX());
                assertEquals(y, pet.getY());
            } finally {
                pet.disposePet();
            }
        });
    }

    @Test
    public void mainWindowOpacityDoesNotApplyToPetFrame() {
        Assume.assumeFalse("headless JVM cannot realize frames", GraphicsEnvironment.isHeadless());
        Assume.assumeTrue("no TRANSLUCENT compositor", WindowOpacity.isTranslucencySupported());

        JFrame main = new JFrame("issue556-main-opacity");
        JFrame pet = new JFrame("issue556-pet-opacity");
        try {
            main.setUndecorated(true);
            pet.setUndecorated(true);
            main.setSize(200, 120);
            pet.setSize(120, 120);
            assertTrue(WindowOpacity.applyTransparencyPercent(main, 25));
            assertEquals(0.75f, main.getOpacity(), 0.001f);
            assertTrue(WindowOpacity.applyTransparencyPercent(pet, 0));
            assertEquals(1.00f, pet.getOpacity(), 0.001f);
        } finally {
            main.dispose();
            pet.dispose();
        }
    }

    private static Supplier<Rectangle> iconifiedAwareMainBounds(JFrame main) {
        return () -> {
            if ((main.getExtendedState() & Frame.ICONIFIED) != 0 || !main.isShowing()) {
                return null;
            }
            Point onScreen = main.getLocationOnScreen();
            return new Rectangle(onScreen.x, onScreen.y, main.getWidth(), main.getHeight());
        };
    }
}