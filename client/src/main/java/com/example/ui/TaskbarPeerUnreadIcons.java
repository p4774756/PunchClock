package com.example.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Windows 等工作列：無 Dock 徽章時，於 App 圖示上疊紅色未讀數。 */
public final class TaskbarPeerUnreadIcons {

    private static final Color BADGE_RED = new Color(180, 40, 40);

    private TaskbarPeerUnreadIcons() {}

    /** 疊在圖示上的文字；超過 99 顯示 {@code 99+}。 */
    public static String badgeTextForComposedIcon(int unreadPeerCount) {
        int count = Math.max(0, unreadPeerCount);
        if (count <= 0) {
            return "";
        }
        if (count > 99) {
            return "99+";
        }
        return String.valueOf(count);
    }

    static boolean useComposedIconFallback(boolean iconBadgeTextSupported, boolean iconBadgeNumberSupported) {
        return !iconBadgeTextSupported && !iconBadgeNumberSupported;
    }

    public static List<Image> composeTaskbarIcons(List<Image> baseIcons, int unreadPeerCount) {
        String label = badgeTextForComposedIcon(unreadPeerCount);
        if (label.isEmpty()) {
            return baseIcons == null ? List.of() : baseIcons;
        }
        if (baseIcons == null || baseIcons.isEmpty()) {
            return List.of();
        }
        List<Image> out = new ArrayList<>(baseIcons.size());
        for (Image base : baseIcons) {
            out.add(paintBadgeOnIcon(base, label));
        }
        return Collections.unmodifiableList(out);
    }

    static BufferedImage paintBadgeOnIcon(Image base, String label) {
        BufferedImage canvas = copyToArgb(base);
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return canvas;
        }
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int fontSize = Math.max(7, Math.min(14, w / 4));
            g.setFont(UiFonts.latinBold(fontSize));
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(label);
            int padX = Math.max(2, w / 16);
            int padY = Math.max(1, h / 20);
            int badgeW = Math.max(w / 4, textW + padX * 2);
            int badgeH = Math.max(w / 4, fm.getAscent() + padY);
            int x = w - badgeW - Math.max(1, w / 16);
            int y = Math.max(1, h / 16);
            g.setColor(BADGE_RED);
            if (label.length() <= 1) {
                int d = Math.max(badgeW, badgeH);
                g.fillOval(x, y, d, d);
                g.setColor(Color.WHITE);
                int tx = x + (d - fm.stringWidth(label)) / 2;
                int ty = y + (d + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(label, tx, ty);
            } else {
                int arc = badgeH;
                g.fillRoundRect(x, y, badgeW, badgeH, arc, arc);
                g.setColor(Color.WHITE);
                int tx = x + (badgeW - textW) / 2;
                int ty = y + (badgeH + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(label, tx, ty);
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static BufferedImage copyToArgb(Image src) {
        int w = Math.max(1, src.getWidth(null));
        int h = Math.max(1, src.getHeight(null));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}