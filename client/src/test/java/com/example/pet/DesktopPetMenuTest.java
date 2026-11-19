package com.example.pet;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesktopPetMenuTest {

    @Test
    public void toggleAndEscapeDismissMenuAndIdleWindowStaysUnfocusable() throws Exception {
        Assume.assumeFalse("headless JVM cannot show a pet", GraphicsEnvironment.isHeadless());
        DesktopPetTest.MemoryPreferences prefs = new DesktopPetTest.MemoryPreferences();
        DesktopPetManager manager = new DesktopPetManager(new DesktopPetSettings(prefs));
        DesktopPetWindow[] holder = new DesktopPetWindow[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(manager.setEnabled(true));
                holder[0] = manager.window();
                assertFalse(holder[0].getFocusableWindowState());
                holder[0].showPetMenuAt(16, 16);
            });
            SwingUtilities.invokeAndWait(() -> assertTrue("選單應打開", holder[0].petMenuVisible()));
            SwingUtilities.invokeAndWait(() -> holder[0].showPetMenuAt(16, 16));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse("再開一次應收起，不要疊新選單", holder[0].petMenuVisible());
                assertFalse(holder[0].getFocusableWindowState());
                holder[0].showPetMenuAt(16, 16);
            });
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(holder[0].petMenuVisible());
                holder[0].dispatchEvent(new KeyEvent(
                        holder[0], KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                        KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
            });
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(holder[0].petMenuVisible());
                assertFalse(holder[0].getFocusableWindowState());
            });
        } finally {
            SwingUtilities.invokeAndWait(manager::shutdown);
        }
    }

    @Test
    public void dismissingMenuDoesNotExitControlMode() throws Exception {
        Assume.assumeFalse("headless JVM cannot show a pet", GraphicsEnvironment.isHeadless());
        DesktopPetTest.MemoryPreferences prefs = new DesktopPetTest.MemoryPreferences();
        DesktopPetManager manager = new DesktopPetManager(new DesktopPetSettings(prefs));
        DesktopPetWindow[] holder = new DesktopPetWindow[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(manager.setEnabled(true));
                holder[0] = manager.window();
                holder[0].enterControlMode();
                assertTrue(holder[0].controlModeActive());
                assertTrue(holder[0].getFocusableWindowState());
                holder[0].showPetMenuAt(16, 16);
            });
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(holder[0].petMenuVisible());
                holder[0].dispatchEvent(new KeyEvent(
                        holder[0], KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                        KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
            });
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(holder[0].petMenuVisible());
                assertTrue(holder[0].controlModeActive());
                assertTrue(holder[0].getFocusableWindowState());
                holder[0].exitControlMode();
            });
        } finally {
            SwingUtilities.invokeAndWait(manager::shutdown);
        }
    }
}
