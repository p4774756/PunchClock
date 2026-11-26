package com.example.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** 聊天視窗標題列左上角：16px 像素風小白貓（粉紅臉頰）。 */
public final class PeerChatTitleIcon {

    private static final int SIZE = 16;
    private static final Color FUR = Color.WHITE;
    private static final Color OUTLINE = new Color(40, 40, 40);
    private static final Color CHEEK = new Color(0xFF, 0x9A, 0xB8);
    private static final Color EYE = Color.BLACK;

    private PeerChatTitleIcon() {
    }

    public static Image create() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(FUR);
            g.fillRect(3, 5, 10, 9);
            g.fillRect(2, 7, 2, 3);
            g.fillRect(12, 7, 2, 3);
            g.fillRect(5, 4, 2, 2);
            g.fillRect(9, 4, 2, 2);
            g.setColor(OUTLINE);
            g.drawRect(3, 5, 9, 8);
            g.drawRect(2, 7, 1, 2);
            g.drawRect(13, 7, 1, 2);
            g.drawRect(5, 4, 1, 1);
            g.drawRect(10, 4, 1, 1);
            g.setColor(CHEEK);
            g.fillRect(4, 10, 2, 1);
            g.fillRect(10, 10, 2, 1);
            g.setColor(EYE);
            g.fillRect(6, 8, 1, 1);
            g.fillRect(9, 8, 1, 1);
            g.setColor(OUTLINE);
            g.drawLine(8, 10, 8, 11);
        } finally {
            g.dispose();
        }
        return image;
    }
}