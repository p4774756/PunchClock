package com.example.ui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AnimeCastTest {

    @Test
    public void heroSheetLoadsWithFiveEqualFrames() {
        AnimeCast cast = AnimeCast.load();
        assertNotNull(cast);
        assertEquals(617, cast.frameWidth());
        assertEquals(860, cast.frameHeight());
        assertEquals(3085, cast.frameWidth() * AnimeCast.FRAMES);
    }
}