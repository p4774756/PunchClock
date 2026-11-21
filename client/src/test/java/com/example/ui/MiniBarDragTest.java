package com.example.ui;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiniBarDragTest {

    @Test
    public void clickVersusDragThreshold() {
        assertTrue(MiniBarDrag.isClick(0, 0));
        assertTrue(MiniBarDrag.isClick(4, -4));
        assertFalse(MiniBarDrag.isClick(5, 0));
        assertFalse(MiniBarDrag.isClick(0, 6));
        assertTrue(MiniBarDrag.isClick(new Point(10, 10), new Point(12, 13)));
        assertFalse(MiniBarDrag.isClick(new Point(0, 0), new Point(8, 0)));
    }

    @Test
    public void clampKeepsMiniBarPartiallyVisible() {
        Rectangle[] screens = {new Rectangle(0, 0, 800, 600)};
        Point clamped = MiniBarDrag.clampLocation(-200, -40, 300, 56, screens);
        assertTrue(clamped.x >= -300 + MiniBarDrag.DRAG_THRESHOLD_PX);
        assertTrue(clamped.y >= -56 + MiniBarDrag.DRAG_THRESHOLD_PX);
        assertTrue(clamped.x <= 800 - MiniBarDrag.DRAG_THRESHOLD_PX);
        assertTrue(clamped.y <= 600 - MiniBarDrag.DRAG_THRESHOLD_PX);
    }
}