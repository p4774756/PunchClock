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
        assertTrue(opaque(fingers, 0.41f, 0.43f));
        assertTrue(opaque(fingers, 0.58f, 0.43f));
        assertTrue(!opaque(fingers, 0.49f, 0.40f));
        assertTrue(!opaque(fingers, 0.35f, 0.40f));
        assertTrue(!opaque(fingers, 0.66f, 0.40f));
    }

    @Test
    public void extendedLegKeepsTheCalfAndDropsTheFoldedLeg() {
        HomeCharacterArt art = HomeCharacterArt.load();
        BufferedImage leg = art.extendedLeg;
        assertTrue(!opaque(leg, 0.30f, 0.75f));
        assertTrue(opaque(leg, 0.76f, 0.79f));
        assertTrue(opaque(leg, 0.78f, 0.82f));
        assertTrue(opaque(leg, 0.90f, 0.90f));
        BufferedImage body = art.bust;
        int w = leg.getWidth();
        int h = leg.getHeight();
        int minLegEnd = Math.round(0.55f * w);
        for (int y = Math.round(0.78f * h); y < Math.round(0.94f * h); y++) {
            int right = -1;
            for (int x = w - 1; x >= 0; x--) {
                if (((leg.getRGB(x, y) >>> 24) & 255) > 16) {
                    right = x;
                    break;
                }
            }
            if (right < 0) {
                continue;
            }
            assertTrue(right > w / 2);
            int bodyRight = -1;
            int start = -1;
            for (int x = 0; x <= w; x++) {
                boolean opaque = x < w && ((body.getRGB(x, y) >>> 24) & 255) > 16;
                if (opaque && start < 0) {
                    start = x;
                }
                if (!opaque && start >= 0) {
                    int end = x - 1;
                    if (end >= minLegEnd) {
                        bodyRight = Math.max(bodyRight, end);
                    }
                    start = -1;
                }
            }
            assertTrue(bodyRight > 0);
            assertTrue(Math.abs(right - bodyRight) <= 2);
        }
    }

    private static boolean opaque(BufferedImage image, float x, float y) {
        int px = Math.round(x * image.getWidth());
        int py = Math.round(y * image.getHeight());
        return ((image.getRGB(px, py) >>> 24) & 255) > 16;
    }
}
