package com.example.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacFramesTest {

    @Test
    public void headMayLeaveTheScreenUntilThePanelMeetsTheMenuBar() {
        assertEquals(400, MacFrames.lowestY(25, 140, 400));
        assertEquals(10, MacFrames.lowestY(25, 140, 10));
        assertEquals(-115, MacFrames.lowestY(25, 140, -200));
        assertEquals(25, MacFrames.lowestY(25, 0, -5));
        assertEquals(-100, MacFrames.lowestY(0, 100, -150));
    }
}
