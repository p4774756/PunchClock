package com.example.ui.blackjack;

import org.junit.Assume;
import org.junit.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void phaseCArtLoadsWithRetinaVariantAndTransparentMargin() {
        String[] paths = {CasinoAssets.TROPHY, CasinoAssets.WAITER_CAT, CasinoAssets.CLOCHE};
        for (String path : paths) {
            Image image = CasinoAssets.get(path);
            assertTrue(path, image instanceof BaseMultiResolutionImage);
            BufferedImage base = CasinoAssets.getBuffered(path);
            assertEquals(path, 0, base.getRGB(0, 0) >>> 24);
            assertEquals(path, 0, base.getRGB(base.getWidth() - 1, base.getHeight() - 1) >>> 24);
            Rectangle2D content = CasinoAssets.contentBounds(path);
            assertTrue(path + " " + content, content.getWidth() > 0.5 && content.getWidth() < 1.0);
            assertTrue(path + " " + content, content.getHeight() > 0.5 && content.getHeight() < 1.0);
        }
    }

    @Test
    public void assetFitKeepsAspectAndFitsContentIntoSlot() {
        BufferedImage canvas = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        assertTrue(CasinoPaint.assetFit(g, CasinoAssets.WAITER_CAT, 200, 300, 200, 150));
        g.dispose();
        int minX = 400, minY = 400, maxX = -1, maxY = -1;
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 400; x++) {
                if ((canvas.getRGB(x, y) >>> 24) >= 32) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        int h = maxY - minY + 1;
        int w = maxX - minX + 1;
        assertEquals(150, h, 3);
        assertEquals(300, maxY + 1, 3);
        Rectangle2D content = CasinoAssets.contentBounds(CasinoAssets.WAITER_CAT);
        BufferedImage base = CasinoAssets.getBuffered(CasinoAssets.WAITER_CAT);
        double aspect = content.getWidth() * base.getWidth() / (content.getHeight() * base.getHeight());
        assertEquals(aspect, (double) w / h, 0.05);
        assertFalse(CasinoPaint.assetFit(g, "deco/missing.png", 0, 0, 10, 10));
    }
}
