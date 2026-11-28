package com.example.pet;

import org.junit.Test;

import java.awt.Window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 操控模式視窗 type／焦點旗標切換；headless 可測，不建立可見視窗。
 */
public class DesktopPetControlFocusPolicyTest {

    @Test
    public void windowTypeConstantsMatchBackgroundPolicy() {
        if (DesktopPetWindow.keepsAppInBackground()) {
            assertEquals(Window.Type.POPUP, DesktopPetWindow.backgroundWindowType());
            assertEquals(Window.Type.NORMAL, DesktopPetWindow.controlModeWindowType());
        } else {
            assertEquals(Window.Type.UTILITY, DesktopPetWindow.backgroundWindowType());
            assertEquals(Window.Type.UTILITY, DesktopPetWindow.controlModeWindowType());
        }
    }

    @Test
    public void controlModeChromeTogglesFocusAndWindowType() {
        DesktopPetWindow.ControlModeChrome idle = DesktopPetWindow.controlModeChrome(false);
        assertEquals(DesktopPetWindow.backgroundWindowType(), idle.windowType);
        assertFalse(idle.focusableWindowState);
        assertFalse(idle.autoRequestFocus);
        assertFalse(idle.stageFocusable);

        DesktopPetWindow.ControlModeChrome active = DesktopPetWindow.controlModeChrome(true);
        assertEquals(DesktopPetWindow.controlModeWindowType(), active.windowType);
        assertTrue(active.focusableWindowState);
        assertEquals(DesktopPetWindow.keepsAppInBackground(), active.autoRequestFocus);
        assertTrue(active.stageFocusable);
    }
}