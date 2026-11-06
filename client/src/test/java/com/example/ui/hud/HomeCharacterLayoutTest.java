package com.example.ui.hud;

import org.junit.Test;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeCharacterLayoutTest {

    private static final Dimension BODY = new Dimension(768, 1024);

    @Test
    public void restoredPoseFollowsThePanelEdges() {
        Insets overflow = HomeCharacterLayout.overflow(1280, 920, false, true);
        Rectangle canvas = new Rectangle(0, 0, 1280, 920);
        Rectangle panel = new Rectangle(0, overflow.top, 1280 - overflow.right, 920 - overflow.top);
        Rectangle controls = new Rectangle(panel.x + panel.width - 140, panel.y + 8, 132, 30);
        Rectangle reminder = new Rectangle(panel.x + 420, panel.y + panel.height - 110, 260, 74);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvas, panel, 40, reminder, controls, false, BODY, BODY);

        assertEquals(box.bust, box.sock);
        Point face = box.face();
        assertTrue(face.x > panel.x + panel.width / 2);
        assertTrue(face.x < controls.x);
        assertTrue(box.bust.y < panel.y);
        assertEquals(panel.y + 40, box.armTop(), 2);
        assertTrue(box.ledgeY() > box.armTop());
        assertTrue(box.fingersY() > box.ledgeY());
        assertTrue(box.fingersY() < box.soleY());
        int panelRight = panel.x + panel.width;
        int peek = box.toeX() - panelRight;
        assertTrue(peek > 16 && peek <= HomeCharacterLayout.SOCK_PEEK);
        int hip = box.bust.x + Math.round(box.bust.width * 0.78f);
        assertTrue(hip < panelRight);
        assertTrue(box.soleY() <= panel.y + panel.height);
        Rectangle ledge = HomeCharacterLayout.armLedge(box, panel);
        assertEquals(box.ledgeY(), ledge.y);
        assertTrue(ledge.width > 48);
        assertTrue(ledge.x + ledge.width <= panelRight);
    }

    @Test
    public void movingThePanelMovesTheCharacterByTheSameAmount() {
        Rectangle canvas = new Rectangle(0, 0, 1400, 1000);
        Rectangle panel = new Rectangle(20, 140, 1100, 760);
        Rectangle reminder = new Rectangle(500, 760, 240, 74);
        Rectangle controls = new Rectangle(980, 150, 132, 30);
        HomeCharacterLayout.Box first = HomeCharacterLayout.place(
                canvas, panel, 40, reminder, controls, false, BODY, BODY);
        Rectangle shiftedPanel = new Rectangle(panel.x + 35, panel.y, panel.width, panel.height);
        Rectangle shiftedReminder = new Rectangle(reminder.x + 35, reminder.y + 18, reminder.width, reminder.height);
        Rectangle shiftedControls = new Rectangle(controls.x + 35, controls.y, controls.width, controls.height);
        HomeCharacterLayout.Box second = HomeCharacterLayout.place(
                canvas, shiftedPanel, 40, shiftedReminder, shiftedControls, false, BODY, BODY);
        assertEquals(first.bust, first.sock);
        assertEquals(second.bust, second.sock);
        assertEquals(35, second.bust.x - first.bust.x);
        assertEquals(35, second.sock.x - first.sock.x);
        assertEquals(0, second.soleY() - first.soleY());
    }

    @Test
    public void maximizedPoseStaysInsideThePanel() {
        Rectangle canvas = new Rectangle(0, 0, 1440, 900);
        Rectangle panel = new Rectangle(0, 0, 1440, 900);
        Rectangle reminder = new Rectangle(760, 760, 280, 74);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvas, panel, 40, reminder, new Rectangle(1280, 4, 132, 30), true, BODY, BODY);
        assertTrue(box.tucked);
        assertEquals(box.bust, box.sock);
        assertTrue(panel.contains(box.bust));
        assertTrue(box.toeX() <= panel.x + panel.width);
        assertTrue(box.armTop() >= 40);
        assertTrue(box.fingersY() > box.ledgeY());
    }

    @Test
    public void otherPagesAndMaximizeDropTheOverflow() {
        assertEquals(0, HomeCharacterLayout.overflow(1280, 920, true, true).top);
        assertEquals(0, HomeCharacterLayout.overflow(1280, 920, false, false).right);
        Insets home = HomeCharacterLayout.overflow(1280, 920, false, true);
        assertTrue(home.top >= 96);
        assertTrue(home.right >= 48);
    }
}
