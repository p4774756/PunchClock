package com.example.ui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AnimeCastLoadTest {

    @Test
    public void heroSheetLoadsFiveFrames() {
        AnimeCast cast = AnimeCast.load();
        assertNotNull(cast);
        assertTrue(cast.frameWidth() > 32);
        assertTrue(cast.frameHeight() > 32);
        assertEquals(617, cast.frameWidth());
        assertEquals(860, cast.frameHeight());
        assertEquals(3085, cast.frameWidth() * AnimeCast.FRAMES);
    }
}
