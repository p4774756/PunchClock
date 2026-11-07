package com.example.ui.hud;

import org.junit.Test;

import java.awt.Insets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CharacterThemeTest {

    @Test
    public void unknownThemeFallsBackToSweater() {
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId(null));
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId(""));
        assertEquals(CharacterTheme.SWEATER, CharacterTheme.fromId("nope"));
        assertEquals(CharacterTheme.ORBITAL, CharacterTheme.fromId(" orbital "));
        assertEquals(CharacterTheme.ORBITAL, CharacterTheme.fromLabel("軌道觀景窗・粉髮女駕駛"));
    }

    @Test
    public void sweaterKeepsOverflowAndOrbitalPreviewDoesNot() {
        HomePortrait portrait = new HomePortrait(null);
        assertTrue(portrait.overflow(1280, 920, false).top >= 96);
        assertNull(portrait.panelOverflow(1100, 760));

        portrait.setTheme(CharacterTheme.ORBITAL);
        assertEquals(new Insets(0, 0, 0, 0), portrait.overflow(1280, 920, false));
        assertEquals(new Insets(0, 0, 0, 0), portrait.panelOverflow(1100, 760));

        portrait.setTheme(CharacterTheme.SWEATER);
        assertNull(portrait.panelOverflow(1100, 760));
        assertTrue(portrait.overflow(1280, 920, false).right > 0);
    }

    @Test
    public void previewIsInsideTheWindowUntilCutoutsArrive() {
        OrbitalAssets assets = OrbitalAssets.shared();
        assertNotNull(assets.stageImage());
        assertNotNull(assets.sidebarImage());
        assertFalse(assets.hasOverflow());
        assertNull(assets.foreground());
        assertEquals(OrbitalAssets.PREVIEW_ANCHOR_X, assets.stageAnchorX(), 0.001f);
    }
}
