package com.example.ui.hud;

import org.junit.Test;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeCharacterLayoutTest {

    private static final Dimension BODY = new Dimension(768, 1024);

    @Test
    public void forearmsRestOnTheTaskCard() {
        Rectangle canvas = new Rectangle(0, 0, 1280, 920);
        Rectangle panel = new Rectangle(0, 0, 1280, 920);
        Rectangle stage = new Rectangle(panel.x + 460, panel.y + 36, panel.width - 480, panel.height - 60);
        Rectangle controls = new Rectangle(panel.x + panel.width - 140, panel.y + 8, 132, 30);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvas, panel, 40, stage, controls, false, BODY, BODY);

        assertEquals(box.bust, box.sock);
        assertEquals(box.ledgeY(), box.card.y);
        assertTrue(box.fingersY() > box.card.y);
        assertTrue(box.fingersY() < box.card.y + box.card.height);
        int calf = box.cuffY() - (box.card.y + box.card.height);
        assertTrue(calf >= 16);
        assertTrue(calf < box.card.height / 2);
        assertTrue(box.toeX() <= panel.x + panel.width - 4);
        assertTrue(box.soleY() <= panel.y + panel.height - 4);
        assertTrue(box.card.x >= stage.x);
        assertTrue(box.textRight < box.card.x + box.card.width - 40);
        assertTrue(box.card.x + Math.round(box.bust.width * 0.02f) < box.toeX());
    }

    @Test
    public void movingThePanelMovesTheCharacterAndCardTogether() {
        Rectangle canvas = new Rectangle(0, 0, 1400, 1000);
        Rectangle panel = new Rectangle(20, 140, 1100, 760);
        Rectangle stage = new Rectangle(panel.x + 420, panel.y + 40, 640, 680);
        Rectangle controls = new Rectangle(980, 150, 132, 30);
        HomeCharacterLayout.Box first = HomeCharacterLayout.place(
                canvas, panel, 40, stage, controls, false, BODY, BODY);
        Rectangle shiftedPanel = new Rectangle(panel.x + 35, panel.y, panel.width, panel.height);
        Rectangle shiftedStage = new Rectangle(stage.x + 35, stage.y, stage.width, stage.height);
        Rectangle shiftedControls = new Rectangle(controls.x + 35, controls.y, controls.width, controls.height);
        HomeCharacterLayout.Box second = HomeCharacterLayout.place(
                canvas, shiftedPanel, 40, shiftedStage, shiftedControls, false, BODY, BODY);
        assertEquals(first.bust, first.sock);
        assertEquals(second.bust, second.sock);
        assertEquals(35, second.bust.x - first.bust.x);
        assertEquals(35, second.card.x - first.card.x);
        assertEquals(0, second.soleY() - first.soleY());
        assertEquals(0, second.card.y - first.card.y);
    }

    @Test
    public void maximizedPoseStaysInsideThePanel() {
        Rectangle canvas = new Rectangle(0, 0, 1440, 900);
        Rectangle panel = new Rectangle(0, 0, 1440, 900);
        Rectangle stage = new Rectangle(420, 48, 980, 820);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvas, panel, 40, stage, new Rectangle(1280, 4, 132, 30), true, BODY, BODY);
        assertTrue(box.tucked);
        assertEquals(box.bust, box.sock);
        assertTrue(panel.contains(box.bust));
        assertTrue(panel.contains(box.card));
        assertEquals(box.ledgeY(), box.card.y);
        assertTrue(box.toeX() <= panel.x + panel.width);
        assertTrue(box.cuffY() > box.card.y + box.card.height);
    }

    @Test
    public void overflowIsAlwaysZero() {
        assertEquals(0, HomeCharacterLayout.overflow(1280, 920, true, true).top);
        assertEquals(0, HomeCharacterLayout.overflow(1280, 920, false, false).right);
        Insets home = HomeCharacterLayout.overflow(1280, 920, false, true);
        assertEquals(0, home.top);
        assertEquals(0, home.right);
        assertEquals(0, home.left);
        assertEquals(0, home.bottom);
    }

    @Test
    public void restoredPoseFitsInsideThePanel() {
        Rectangle canvas = new Rectangle(0, 0, 1100, 760);
        Rectangle panel = new Rectangle(0, 0, 1100, 760);
        Rectangle stage = new Rectangle(panel.x + 200, panel.y + 36, panel.width - 220, panel.height - 60);
        Rectangle controls = new Rectangle(panel.x + panel.width - 140, panel.y + 8, 132, 30);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvas, panel, 40, stage, controls, false, BODY, BODY);
        assertTrue(panel.contains(box.bust));
        int hairTop = box.bust.y + Math.round(box.bust.height * HomeCharacterLayout.HAIR_Y);
        assertTrue(hairTop >= panel.y + 40);
        assertTrue(box.toeX() <= panel.x + panel.width - 4);
        assertTrue(box.soleY() <= panel.y + panel.height - 4);
    }
}
