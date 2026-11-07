package com.example.ui.hud;

import org.junit.Test;

import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

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
    public void sweaterKeepsOverflowAndOrbitalUsesFrameMargins() {
        HomePortrait portrait = new HomePortrait(null);
        assertTrue(portrait.overflow(1280, 920, false).top >= 96);
        assertNull(portrait.panelOverflow(1100, 760));

        portrait.setTheme(CharacterTheme.ORBITAL);
        if (OrbitalAssets.shared().hasOverflow()) {
            Insets panel = portrait.panelOverflow(1100, 760);
            assertNotNull(panel);
            assertTrue(panel.top >= 64);
            assertTrue(panel.right >= 48);
            // 外層 = 面板 + 留白時，兩邊算出的留白必須相同，換頁才不會改視窗大小。
            Insets overflow = portrait.overflow(1100 + panel.right, 760 + panel.top, false);
            assertEquals(panel.top, overflow.top);
            assertEquals(panel.right, overflow.right);
        } else {
            assertEquals(new Insets(0, 0, 0, 0), portrait.overflow(1280, 920, false));
            assertEquals(new Insets(0, 0, 0, 0), portrait.panelOverflow(1100, 760));
        }

        portrait.setTheme(CharacterTheme.SWEATER);
        assertNull(portrait.panelOverflow(1100, 760));
        assertTrue(portrait.overflow(1280, 920, false).right > 0);
    }

    @Test
    public void orbitalCutoutsPreferContainAndKeepAspect() {
        OrbitalAssets assets = OrbitalAssets.shared();
        assertNotNull(assets.stageImage());
        assertNotNull(assets.sidebarImage());
        assertTrue(assets.hasOverflow());
        assertNotNull(assets.foreground());
        assertEquals(OrbitalAssets.SCENE_ANCHOR_X, assets.stageAnchorX(), 0.001f);

        BufferedImage foreground = assets.foreground();
        Rectangle dest = new Rectangle(100, 40, 520, 640);
        Rectangle fitted = OrbitalAssets.fitTopRight(foreground, dest);
        assertTrue(fitted.height <= dest.height + 1);
        assertEquals(dest.y, fitted.y);
        // 手套（不透明右緣）貼齊右框；圖寬可略超出 dest。
        assertTrue(fitted.x + fitted.width >= dest.x + dest.width - 2);
        double aspect = foreground.getWidth() / (double) foreground.getHeight();
        double drawn = fitted.width / (double) fitted.height;
        assertEquals(aspect, drawn, 0.02);

        // 縮窄舞台時仍 contain，不為了填滿而放大裁切。
        Rectangle narrow = new Rectangle(100, 40, 280, 640);
        Rectangle narrowFit = OrbitalAssets.fitTopRight(foreground, narrow);
        assertTrue(narrowFit.height <= narrow.height + 1);
        assertFalse(narrowFit.width == narrow.width && narrowFit.height == narrow.height);
    }
}
