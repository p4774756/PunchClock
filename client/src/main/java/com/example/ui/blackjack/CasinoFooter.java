package com.example.ui.blackjack;

import com.example.ui.CandyTheme;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 頁尾列：左邊模式標籤與說明（或傳送狀態），右邊小按鈕。
 */
public final class CasinoFooter extends JPanel implements CasinoTheme.Scalable {

    private final JPanel buttons;
    private float scale = 1f;
    private boolean lunch;
    private String chip = "普通對戰";
    private String message = "";
    private Color messageColor = CasinoTheme.TEXT;

    public CasinoFooter(JComponent... actions) {
        super(null);
        setOpaque(false);
        buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        for (JComponent action : actions) {
            buttons.add(action);
        }
        add(buttons);
    }

    public void setMode(boolean lunchMode, String chipText) {
        lunch = lunchMode;
        chip = chipText == null ? "" : chipText;
        repaint();
    }

    public void setMessage(String text, Color color) {
        message = text == null ? "" : text;
        messageColor = color == null ? CasinoTheme.TEXT : color;
        setToolTipText(message.isEmpty() ? null : message);
        repaint();
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        ((FlowLayout) buttons.getLayout()).setHgap(CasinoTheme.px(value, 12));
        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        Dimension pref = buttons.getPreferredSize();
        int pad = CasinoTheme.px(scale, 14);
        int w = Math.min(pref.width, getWidth() - pad * 2);
        buttons.setBounds(getWidth() - pad - w, (getHeight() - pref.height) / 2, w, pref.height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        int w = getWidth();
        int h = getHeight();
        double arc = 18 * scale;
        if (!CasinoPaint.slice9(g, CasinoAssets.FOOTER_BAR, 0, 0, w, h, 20, 20, 20, 20, h / 66.0)) {
            RoundRectangle2D.Double bar = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, arc, arc);
            g.setPaint(new GradientPaint(0, 0, CasinoTheme.WINE, 0, h, CasinoTheme.PLUM_DARK));
            g.fill(bar);
            CasinoTheme.goldFrame(g, bar, Math.max(2f, 2.6f * scale));
        }

        double pad = 14 * scale;
        Font chipFont = CasinoTheme.chinese(true, scale, 19);
        FontMetrics cm = g.getFontMetrics(chipFont);
        double iconSize = lunch ? 30 * scale : 0;
        double chipH = 44 * scale;
        double chipW = cm.stringWidth(chip) + iconSize + (lunch ? 8 * scale : 0) + 30 * scale;
        double cx = pad;
        double cy = (h - chipH) / 2;
        boolean chipArt = CasinoPaint.slice3(g, lunch ? CasinoAssets.CHIP_PINK : CasinoAssets.CHIP_LAVENDER,
                cx, cy, chipW, chipH, 22);
        if (!chipArt) {
            RoundRectangle2D.Double pill = new RoundRectangle2D.Double(cx, cy, chipW, chipH, chipH, chipH);
            g.setPaint(lunch
                    ? new GradientPaint(0, (float) cy, new Color(0xFF, 0xD0, 0xE4), 0, (float) (cy + chipH), new Color(0xFF, 0x9C, 0xC6))
                    : new GradientPaint(0, (float) cy, new Color(0xE4, 0xD8, 0xFF), 0, (float) (cy + chipH), new Color(0xB4, 0x9C, 0xF0)));
            g.fill(pill);
            g.setColor(new Color(255, 255, 255, 170));
            g.setStroke(new java.awt.BasicStroke(Math.max(1f, 1.6f * scale)));
            g.draw(pill);
        }
        double tx = cx + 15 * scale;
        if (lunch) {
            CasinoIcons.BENTO.paint(g, (int) Math.round(tx), (int) Math.round(cy + (chipH - iconSize) / 2),
                    (int) Math.round(iconSize), null);
            tx += iconSize + 8 * scale;
        }
        g.setFont(chipFont);
        float chipBase = (float) (cy + chipH * 0.44 + cm.getAscent() / 2.0 - cm.getDescent() / 2.0);
        if (chipArt) {
            g.setColor(new Color(0x4A, 0x0A, 0x24, 160));
            g.drawString(chip, (float) tx, chipBase + Math.max(1f, scale));
            g.setColor(Color.WHITE);
        } else {
            g.setColor(new Color(0x5A, 0x12, 0x30));
        }
        g.drawString(chip, (float) tx, chipBase);

        double textX = cx + chipW + 16 * scale;
        int room = (int) (buttons.getX() - 12 * scale - textX);
        if (room > 20 && !message.isEmpty()) {
            Font font = CasinoTheme.chinese(false, scale, 18);
            FontMetrics fm = g.getFontMetrics(font);
            g.setFont(font);
            g.setColor(messageColor);
            g.drawString(CandyTheme.fit(message, fm, room), (float) textX,
                    (float) (h / 2.0 + fm.getAscent() / 2.0 - fm.getDescent() / 2.0));
        }
        g.dispose();
    }
}
