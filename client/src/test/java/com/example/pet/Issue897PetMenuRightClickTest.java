package com.example.pet;

import org.junit.Test;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 萌寵狀態看板只由右鍵／popup trigger 打開，左鍵留給抓取與拖曳。 */
public class Issue897PetMenuRightClickTest {

    @Test
    public void leftClickIsNotAMenuGestureAndMayStartCarry() {
        JPanel source = new JPanel();
        MouseEvent press = event(source, MouseEvent.MOUSE_PRESSED, 0, false, MouseEvent.BUTTON1);
        MouseEvent release = event(source, MouseEvent.MOUSE_RELEASED, 0, false, MouseEvent.BUTTON1);
        assertFalse(DesktopPetWindow.isPetMenuGesture(press));
        assertFalse(DesktopPetWindow.isPetMenuGesture(release));
        assertTrue(DesktopPetWindow.shouldStartCarry(press));
        assertFalse(DesktopPetWindow.shouldStartCarry(release));
    }

    @Test
    public void rightClickAndPopupTriggerOpenTheMenuAndDoNotStartCarry() {
        JPanel source = new JPanel();
        MouseEvent winRelease = event(
                source, MouseEvent.MOUSE_RELEASED, 0, true, MouseEvent.BUTTON3);
        MouseEvent macPress = event(
                source, MouseEvent.MOUSE_PRESSED, InputEvent.CTRL_DOWN_MASK, true, MouseEvent.BUTTON1);
        MouseEvent rightPress = event(
                source, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON3_DOWN_MASK, false, MouseEvent.BUTTON3);
        assertTrue(DesktopPetWindow.isPetMenuGesture(winRelease));
        assertTrue(DesktopPetWindow.isPetMenuGesture(macPress));
        assertTrue(DesktopPetWindow.isPetMenuGesture(rightPress));
        assertFalse(DesktopPetWindow.shouldStartCarry(winRelease));
        assertFalse(DesktopPetWindow.shouldStartCarry(macPress));
        assertFalse(DesktopPetWindow.shouldStartCarry(rightPress));
    }

    private static MouseEvent event(
            JPanel source, int id, int modifiers, boolean popupTrigger, int button) {
        return new MouseEvent(
                source, id, System.currentTimeMillis(), modifiers,
                8, 8, 8, 8, 1, popupTrigger, button);
    }
}
