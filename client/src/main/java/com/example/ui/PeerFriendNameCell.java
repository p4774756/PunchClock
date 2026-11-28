package com.example.ui;

import javax.swing.Icon;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.border.EmptyBorder;

/**
 * 好友表第 0 欄：頭像、名稱與自訂狀態同一行；狀態過長時由 {@link PeerStatusMarquee} 水平捲動。
 */
final class PeerFriendNameCell extends JPanel {

    private static final int STATUS_GAP = 8;
    private static final int MARQUEE_TAIL_GAP = 24;
    private static final Color STATUS_MUTED = new Color(0x64, 0x64, 0x64);
    private static final Color STATUS_HIGH = new Color(0xE8, 0xE8, 0xF0);

    private final EmptyBorder cellBorder = new EmptyBorder(4, 4, 4, 2);

    private Icon icon;
    private String displayId = "";
    private String status = "";
    private boolean self;
    private boolean highContrast;
    private Font mainFont = UiFonts.chinesePlain(13);
    private Font boldFont = UiFonts.chineseBold(13);
    private int columnWidth;
    private int marqueeOffset;
    private boolean statusMarquee;

    PeerFriendNameCell() {
        setOpaque(true);
        setBorder(cellBorder);
        setLayout(null);
    }

    void configure(
            int colWidth,
            String displayId,
            String status,
            boolean self,
            Font mainFont,
            Font boldFont,
            Icon icon,
            boolean highContrast,
            int marqueeOffset) {
        this.columnWidth = Math.max(0, colWidth);
        this.displayId = displayId == null ? "" : displayId;
        this.status = status == null ? "" : status.trim();
        this.self = self;
        this.mainFont = mainFont;
        this.boldFont = boldFont;
        this.icon = icon;
        this.highContrast = highContrast;
        this.marqueeOffset = Math.max(0, marqueeOffset);
        this.statusMarquee = computeStatusMarquee();
    }

    String displayId() {
        return displayId;
    }

    String statusText() {
        return status;
    }

    boolean needsStatusMarquee() {
        return statusMarquee;
    }

    Icon avatarIcon() {
        return icon;
    }

    private boolean computeStatusMarquee() {
        if (status.isEmpty() || columnWidth <= 0) {
            return false;
        }
        int content = measureContentWidth();
        Insets in = cellBorder.getBorderInsets(this);
        int available = columnWidth - in.left - in.right;
        return content > available;
    }

    int measureContentWidth() {
        Insets in = cellBorder.getBorderInsets(this);
        int iconPart = icon == null ? 0 : icon.getIconWidth() + 4;
        Font nameFont = self ? boldFont : mainFont;
        FontMetrics nameFm = getFontMetrics(nameFont);
        int nameW = nameFm.stringWidth(displayId);
        int total = in.left + in.right + iconPart + nameW;
        if (!status.isEmpty()) {
            Font statusFont = statusFont();
            FontMetrics statusFm = getFontMetrics(statusFont);
            total += STATUS_GAP + statusFm.stringWidth(status);
        }
        return total;
    }

    @Override
    public Dimension getPreferredSize() {
        Insets in = cellBorder.getBorderInsets(this);
        int w = measureContentWidth();
        if (columnWidth > 0) {
            w = Math.min(w, columnWidth);
        }
        int rowH = Math.max(18, icon == null ? 18 : icon.getIconHeight()) + in.top + in.bottom;
        return new Dimension(Math.max(1, w), rowH);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Insets in = cellBorder.getBorderInsets(this);
            int innerH = getHeight() - in.top - in.bottom;
            int x = in.left;
            if (icon != null) {
                int iconY = in.top + (innerH - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, x, iconY);
                x += icon.getIconWidth() + 4;
            }
            Font nameFont = self ? boldFont : mainFont;
            g.setFont(nameFont);
            FontMetrics nameFm = g.getFontMetrics();
            Color nameColor = highContrast ? Color.WHITE : Color.BLACK;
            g.setColor(nameColor);
            int baseline = in.top + (innerH + nameFm.getAscent() - nameFm.getDescent()) / 2;
            g.drawString(displayId, x, baseline);
            if (status.isEmpty()) {
                return;
            }
            int nameW = nameFm.stringWidth(displayId);
            int statusX = x + nameW + STATUS_GAP;
            int right = getWidth() - in.right;
            int statusSlot = Math.max(0, right - statusX);
            if (statusSlot <= 0) {
                return;
            }
            Font statusFont = statusFont();
            g.setFont(statusFont);
            FontMetrics statusFm = g.getFontMetrics();
            g.setColor(highContrast ? STATUS_HIGH : STATUS_MUTED);
            Graphics2D clipG = (Graphics2D) g.create();
            try {
                clipG.setClip(statusX, in.top, statusSlot, innerH);
                int statusW = statusFm.stringWidth(status);
                int drawX = statusX;
                if (statusMarquee && statusW > statusSlot) {
                    int cycle = statusW + MARQUEE_TAIL_GAP;
                    int shift = marqueeOffset % cycle;
                    drawX = statusX - shift;
                    clipG.drawString(status, drawX, baseline);
                    clipG.drawString(status, drawX + cycle, baseline);
                } else {
                    clipG.drawString(status, drawX, baseline);
                }
            } finally {
                clipG.dispose();
            }
        } finally {
            g.dispose();
        }
    }

    private Font statusFont() {
        int size = Math.max(10, mainFont.getSize() - 1);
        return UiFonts.chinesePlain(size);
    }
}