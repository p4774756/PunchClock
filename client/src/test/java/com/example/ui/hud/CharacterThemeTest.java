package com.example.ui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CharacterThemeTest {

    @Test
    public void unknownThemeFallsBackToSweater() {
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId(null));
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId(""));
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId("nope"));
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId(" orbital "));
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromLabel("軌道觀景窗・粉髮女駕駛"));
    }

    @Test
    public void sweaterKeepsHomePortraitOverflowZero() {
        HomePortrait portrait = new HomePortrait(null);
        assertEquals(0, portrait.overflow(1280, 920, false).top);
        assertEquals(0, portrait.overflow(1280, 920, false).right);
        assertNull(portrait.panelOverflow(1100, 760));
        portrait.setTheme(CharacterTheme.SWEATER);
        assertNull(portrait.panelOverflow(1100, 760));
        assertEquals(0, portrait.overflow(1280, 920, false).right);
    }
}