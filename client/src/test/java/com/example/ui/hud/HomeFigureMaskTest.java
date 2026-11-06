package com.example.ui.hud;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertTrue;

public class HomeFigureMaskTest {

    @Test
    public void fingersKeepBothHandsAndDropTheSweaterBetweenThem() {
        HomeCharacterArt art = HomeCharacterArt.load();
        assertTrue(art != null);
        BufferedImage fingers = art.fingers;
        assertTrue(opaque(fingers, 0.33f, 0.35f));
        assertTrue(opaque(fingers, 0.52f, 0.35f));
        assertTrue(!opaque(fingers, 0.43f, 0.36f));
        assertTrue(!opaque(fingers, 0.18f, 0.35f));
        assertTrue(!opaque(fingers, 0.64f, 0.35f));
    }

    @Test
    public void extendedLegKeepsTheCalfAndDropsTheFoldedLeg() {
        HomeCharacterArt art = HomeCharacterArt.load();
        BufferedImage leg = art.extendedLeg;
        assertTrue(!opaque(leg, 0.30f, 0.75f));
        assertTrue(opaque(leg, 0.70f, 0.74f));
        assertTrue(opaque(leg, 0.78f, 0.82f));
        assertTrue(opaque(leg, 0.90f, 0.90f));
        int w = leg.getWidth();
        int h = leg.getHeight();
        int previous = -1;
        for (int y = Math.round(0.74f * h); y < Math.round(0.94f * h); y++) {
            int right = -1;
            for (int x = w - 1; x >= 0; x--) {
                if (((leg.getRGB(x, y) >>> 24) & 255) > 16) {
                    right = x;
                    break;
                }
            }
            assertTrue(right > w / 2);
            if (previous >= 0) {
                assertTrue(Math.abs(right - previous) < 12);
            }
            previous = right;
        }
    }

    private static boolean opaque(BufferedImage image, float x, float y) {
        int px = Math.round(x * image.getWidth());
        int py = Math.round(y * image.getHeight());
        return ((image.getRGB(px, py) >>> 24) & 255) > 16;
    }
}
