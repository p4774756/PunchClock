package com.example.ui;

import org.junit.Test;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TaskbarPeerUnreadIconsTest {

    @Test
    public void composedBadgeText_coversZeroOneNineAndCapped() {
        assertEquals("", TaskbarPeerUnreadIcons.badgeTextForComposedIcon(0));
        assertEquals("1", TaskbarPeerUnreadIcons.badgeTextForComposedIcon(1));
        assertEquals("9", TaskbarPeerUnreadIcons.badgeTextForComposedIcon(9));
        assertEquals("99", TaskbarPeerUnreadIcons.badgeTextForComposedIcon(99));
        assertEquals("99+", TaskbarPeerUnreadIcons.badgeTextForComposedIcon(100));
        assertEquals("99+", TaskbarPeerUnreadIcons.badgeTextForComposedIcon(240));
    }

    @Test
    public void useComposedIconFallback_whenNoNativeBadgeFeatures() {
        assertTrue(TaskbarPeerUnreadIcons.useComposedIconFallback(false, false));
        assertFalse(TaskbarPeerUnreadIcons.useComposedIconFallback(true, false));
        assertFalse(TaskbarPeerUnreadIcons.useComposedIconFallback(false, true));
        assertFalse(TaskbarPeerUnreadIcons.useComposedIconFallback(true, true));
    }

    @Test
    public void compose_returnsBaseWhenNoUnread() {
        BufferedImage base = filledArgb(32, 32, new Color(40, 80, 120));
        List<Image> bases = List.of(base);
        assertSame(bases, TaskbarPeerUnreadIcons.composeTaskbarIcons(bases, 0));
    }

    @Test
    public void compose_addsRedBadgeCornerWhenUnread() {
        BufferedImage base = filledArgb(64, 64, new Color(40, 80, 120));
        List<Image> composed = TaskbarPeerUnreadIcons.composeTaskbarIcons(List.of(base), 3);
        assertEquals(1, composed.size());
        assertNotSame(base, composed.get(0));
        BufferedImage out = (BufferedImage) composed.get(0);
        assertTrue(hasBadgeRedNearCorner(out));
    }

    @Test
    public void compose_paintsNinetyNinePlusLabel() {
        BufferedImage painted = TaskbarPeerUnreadIcons.paintBadgeOnIcon(filledArgb(64, 64, Color.LIGHT_GRAY), "99+");
        assertTrue(hasBadgeRedNearCorner(painted));
    }

    private static BufferedImage filledArgb(int w, int h, Color fill) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, fill.getRGB());
            }
        }
        return img;
    }

    private static boolean hasBadgeRedNearCorner(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < Math.min(h, h / 2); y++) {
            for (int x = Math.max(0, w * 2 / 3); x < w; x++) {
                int rgb = img.getRGB(x, y);
                Color c = new Color(rgb, true);
                if (c.getRed() >= 150 && c.getGreen() <= 80 && c.getBlue() <= 80 && c.getAlpha() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}