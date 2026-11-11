package com.example.ui.blackjack;

import org.junit.Assume;
import org.junit.Test;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CasinoArtTest {

    @Test
    public void titleFontDrawsTwentyOneInTraditionalChinese() {
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        Assume.assumeTrue("沒有裝繁中字型的環境略過",
                installed.contains("PingFang TC") || installed.contains("Microsoft JhengHei")
                        || installed.contains("微軟正黑體") || installed.contains("Noto Sans CJK TC"));
        Font font = CasinoTheme.titleChinese("21點", 1f, 62);
        assertEquals(-1, font.canDisplayUpTo("21點"));
        assertEquals(62, font.getSize());
    }

    @Test
    public void logoIsDrawnByJava2dNotImage() {
        assertNull(CasinoAssets.get("title/logo.png"));
    }

    @Test
    public void medallionRingCenterIsTransparent() {
        BufferedImage ring = CasinoAssets.getBuffered(CasinoAssets.MEDALLION_RING);
        assertNotNull(ring);
        double c = ring.getWidth() / 2.0;
        double inner = ring.getWidth() * 90 / 256.0;
        for (int y = 0; y < ring.getHeight(); y++) {
            for (int x = 0; x < ring.getWidth(); x++) {
                if (Math.hypot(x + 0.5 - c, y + 0.5 - c) < inner) {
                    assertEquals("(" + x + "," + y + ")", 0, ring.getRGB(x, y) >>> 24);
                }
            }
        }
        assertEquals(0.0, CasinoAssets.opaqueRadius(CasinoAssets.MEDALLION_RING), 0.0);
    }

    @Test
    public void defaultCharactersAreCircularCropsWithCleanCore() {
        String[] paths = {CasinoAssets.PLAYER_DEFAULT, "char/peer-default-1.png", "char/peer-default-2.png", "char/peer-default-3.png"};
        for (String path : paths) {
            double radius = CasinoAssets.opaqueRadius(path);
            assertTrue(path + " = " + radius, radius >= 0.9 && radius < 1.0);
        }
    }
}
