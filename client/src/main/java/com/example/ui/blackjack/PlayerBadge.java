package com.example.ui.blackjack;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * 玩家資訊條：頭像、名字、點數與狀態標籤。輪到這位玩家時外框會呼吸發光。
 */
public final class PlayerBadge extends JComponent {

    private static final int AVATAR = 40;
    private static final Color[] FACES = {
            new Color(0xFF, 0xC7, 0xDD), new Color(0xC9, 0xE4, 0xFF), new Color(0xD8, 0xF5, 0xC8),
            new Color(0xFF, 0xE3, 0xB3), new Color(0xE3, 0xD3, 0xFF)};

    private final String role;
    private String name = "";
    private Image avatar;
    private int score = -1;
    private String tag;
    private Color tagColor = CandyTheme.LAVENDER;
    private boolean active;
    private Timer pulse;
    private long pulseStart;

    public PlayerBadge(String role) {
        this.role = role;
        setOpaque(false);
    }

    public void setPlayer(String name, Image avatar) {
        this.name = name == null ? "" : name;
        this.avatar = avatar;
        repaint();
    }

    /** 負數代表還沒有點數。 */
    public void setScore(int score) {
        this.score = score;
        repaint();
    }

    public void setTag(String tag, Color color) {
        this.tag = tag;
        this.tagColor = color == null ? CandyTheme.LAVENDER : color;
        repaint();
    }

    public void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        if (active) {
            pulseStart = System.currentTimeMillis();
            if (pulse == null) {
                pulse = new Timer(40, e -> repaint());
            }
            if (isDisplayable()) {
                pulse.start();
            }
        } else if (pulse != null) {
            pulse.stop();
        }
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (active && pulse != null) {
            pulse.start();
        }
    }

    @Override
    public void removeNotify() {
        if (pulse != null) {
            pulse.stop();
        }
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(360, AVATAR + 16);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(220, AVATAR + 12);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int h = getHeight() - 4;
        int pillW = Math.min(getWidth() - 4, 380);
        int x = (getWidth() - pillW) / 2;
        int y = 2;
        int arc = h;

        if (active) {
            float glow = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() - pulseStart) / 260.0);
            g.setColor(new Color(255, 230, 140, Math.round(60 + 90 * glow)));
            g.setStroke(new BasicStroke(4f));
            g.drawRoundRect(x + 1, y, pillW - 3, h - 1, arc, arc);
        }
        g.setColor(new Color(16, 40, 30, 150));
        g.fillRoundRect(x + 2, y + 1, pillW - 4, h - 2, arc, arc);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x + 2, y + 1, pillW - 5, h - 3, arc, arc);

        int ax = x + 8;
        int ay = y + (h - AVATAR) / 2;
        paintAvatar(g, ax, ay);

        int textX = ax + AVATAR + 10;
        Font nameFont = UiFonts.chineseBold(14);
        Font roleFont = UiFonts.chinesePlain(11);
        int right = x + pillW - 12;
        int scoreW = paintScore(g, right, y, h);
        int tagW = paintTag(g, right - scoreW - 8, y, h);
        int textRoom = right - scoreW - tagW - 16 - textX;

        g.setFont(nameFont);
        FontMetrics nm = g.getFontMetrics();
        g.setFont(roleFont);
        FontMetrics rm = g.getFontMetrics();
        int block = nm.getHeight() + rm.getHeight() - 4;
        int ny = y + (h - block) / 2 + nm.getAscent();
        g.setFont(nameFont);
        g.setColor(CandyTheme.INK);
        g.drawString(CandyTheme.fit(name, nm, Math.max(20, textRoom)), textX, ny);
        g.setFont(roleFont);
        g.setColor(active ? new Color(255, 230, 150) : CandyTheme.MUTED);
        g.drawString(active ? role + " · 行動中" : role, textX, ny + rm.getHeight() - 2);
        g.dispose();
    }

    private void paintAvatar(Graphics2D g, int x, int y) {
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(x - 2, y - 2, AVATAR + 4, AVATAR + 4));
        if (avatar != null) {
            Graphics2D clip = (Graphics2D) g.create();
            clip.clip(new Ellipse2D.Float(x, y, AVATAR, AVATAR));
            clip.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            clip.drawImage(avatar, x, y, AVATAR, AVATAR, null);
            clip.dispose();
            return;
        }
        Color face = FACES[Math.floorMod(name.hashCode(), FACES.length)];
        g.setColor(face);
        g.fill(new Ellipse2D.Float(x, y, AVATAR, AVATAR));
        int px = AVATAR / 10;
        g.setColor(new Color(0x3A, 0x28, 0x4A));
        g.fillRect(x + px * 3, y + px * 4, px, px * 2);
        g.fillRect(x + px * 6, y + px * 4, px, px * 2);
        g.fillRect(x + px * 4, y + px * 7, px * 2, px);
        g.setColor(new Color(0xFF, 0x8F, 0xB0));
        g.fillRect(x + px * 2 - 1, y + px * 6, px, px);
        g.fillRect(x + px * 7 + 1, y + px * 6, px, px);
    }

    private int paintScore(Graphics2D g, int right, int y, int h) {
        String text = score < 0 ? "–" : String.valueOf(score);
        Font font = UiFonts.latinBold(18);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int w = Math.max(44, metrics.stringWidth(text) + 22);
        int ch = h - 14;
        int cx = right - w;
        int cy = y + 7;
        Color fill;
        if (score > 21) {
            fill = new Color(0xF0, 0x78, 0x8F);
        } else if (score == 21) {
            fill = new Color(0xFF, 0xD2, 0x5E);
        } else {
            fill = new Color(0xFF, 0xF8, 0xFC);
        }
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(cx, cy + 2, w, ch, ch, ch);
        g.setColor(fill);
        g.fillRoundRect(cx, cy, w, ch, ch, ch);
        g.setColor(CardPainter.BLACK);
        g.drawString(text, cx + (w - metrics.stringWidth(text)) / 2,
                cy + (ch - metrics.getHeight()) / 2 + metrics.getAscent());
        return w;
    }

    private int paintTag(Graphics2D g, int right, int y, int h) {
        if (tag == null || tag.isEmpty()) {
            return 0;
        }
        Font font = UiFonts.chineseBold(12);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int w = metrics.stringWidth(tag) + 16;
        int th = metrics.getHeight() + 4;
        int tx = right - w;
        int ty = y + (h - th) / 2;
        g.setColor(tagColor);
        g.fillRoundRect(tx, ty, w, th, th, th);
        g.setColor(CardPainter.BLACK);
        g.drawString(tag, tx + 8, ty + 2 + metrics.getAscent());
        return w;
    }
}
