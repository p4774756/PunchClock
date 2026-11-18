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
        // 手指內部（768×1024 換算比例）
        assertTrue(opaque(fingers, 365f / 768f, 430f / 1024f));
        assertTrue(opaque(fingers, 350f / 768f, 420f / 1024f));
        assertTrue(opaque(fingers, 425f / 768f, 420f / 1024f));
        assertTrue(opaque(fingers, 405f / 768f, 440f / 1024f));
        // 頭髮
        assertTrue(!opaque(fingers, 432f / 768f, 435f / 1024f));
        assertTrue(!opaque(fingers, 338f / 768f, 445f / 1024f));
        // 多邊形外
        assertTrue(!opaque(fingers, 330f / 768f, 420f / 1024f));
        assertTrue(!opaque(fingers, 450f / 768f, 420f / 1024f));
        assertTrue(!opaque(fingers, 395f / 768f, 460f / 1024f));
        // 比心空隙（兩指之間）
        assertTrue(!opaque(fingers, 395f / 768f, 405f / 1024f));
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