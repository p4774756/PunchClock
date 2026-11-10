package com.example.ui.blackjack;

import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 賭場風立體糖果鈕：金色外框、漸層本體、上緣高光、底部厚度。滑過會浮起、按下會壓下去。
 * 是真的 {@link JButton}，鍵盤、焦點、停用都照 Swing 行為。
 */
public final class CasinoButton extends JButton implements CasinoTheme.Scalable {

    public enum Tone {
        GREEN(new Color(0x5F, 0xD0, 0x7E), new Color(0x1E, 0x8A, 0x45), new Color(0x0F, 0x55, 0x2A)),
        RED(new Color(0xFF, 0x6E, 0x80), new Color(0xC4, 0x1E, 0x3A), new Color(0x7A, 0x0E, 0x22)),
        PURPLE(new Color(0xC6, 0xA6, 0xFF), new Color(0x86, 0x58, 0xE0), new Color(0x4E, 0x2E, 0x96)),
        PINK(new Color(0xFF, 0x9E, 0xC8), new Color(0xE8, 0x4E, 0x8E), new Color(0x93, 0x22, 0x56));

        final Color top;
        final Color bottom;
        final Color lip;

        Tone(Color top, Color bottom, Color lip) {
            this.top = top;
            this.bottom = bottom;
            this.lip = lip;
        }
    }

    public enum Kind {
        /** 牌桌下方的大鈕（要牌、停牌、接受、婉拒） */
        LARGE(250, 70, 26, 13, 30),
        /** 頁尾的小鈕 */
        SMALL(140, 46, 18, 0, 20);

        final int width;
        final int height;
        final int font;
        final int subFont;
        final int icon;

        Kind(int width, int height, int font, int subFont, int icon) {
            this.width = width;
            this.height = height;
            this.font = font;
            this.subFont = subFont;
            this.icon = icon;
        }
    }

    private static final int ANIM_MS = 130;

    private final Tone tone;
    private final Kind kind;
    private final CasinoIcons icon;
    private String subLabel;
    private float scale = 1f;
    private float lift;
    private float liftFrom;
    private float liftTo;
    private long liftStart;
    private final Timer timer;

    public CasinoButton(String text, String subLabel, CasinoIcons icon, Tone tone, Kind kind) {
        super(text);
        this.subLabel = subLabel;
        this.icon = icon;
        this.tone = tone;
        this.kind = kind;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        timer = new Timer(15, e -> step());
        getModel().addChangeListener(e -> follow());
    }

    public void setSubLabel(String value) {
        subLabel = value;
        revalidate();
        repaint();
    }

    @Override
    public void setUiScale(float value) {
        scale = value;
        revalidate();
        repaint();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        Font font = CasinoTheme.chinese(true, scale, kind.font);
        FontMetrics metrics = getFontMetrics(font);
        int textW = metrics.stringWidth(getText() == null ? "" : getText());
        int iconW = icon == null ? 0 : CasinoTheme.px(scale, kind.icon + 10);
        int pad = CasinoTheme.px(scale, kind == Kind.LARGE ? 36 : 18);
        int width = Math.max(CasinoTheme.px(scale, kind.width), textW + iconW + pad * 2);
        return new Dimension(width, CasinoTheme.px(scale, kind.height));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private void follow() {
        float next = 0f;
        if (isEnabled() && getModel().isPressed() && getModel().isArmed()) {
            next = 1f;
        } else if (isEnabled() && getModel().isRollover()) {
            next = -1f;
        }
        if (next == liftTo) {
            return;
        }
        liftFrom = lift;
        liftTo = next;
        liftStart = System.currentTimeMillis();
        timer.start();
    }

    private void step() {
        float t = Math.min(1f, (System.currentTimeMillis() - liftStart) / (float) ANIM_MS);
        float eased = 1f - (1f - t) * (1f - t);
        lift = liftFrom + (liftTo - liftFrom) * eased;
        repaint();
        if (t >= 1f) {
            timer.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D art = CasinoTheme.quality((Graphics2D) graphics);
        boolean painted = paintAsset(art);
        art.dispose();
        if (!painted) {
            paintJava2D(graphics);
        }
    }

    /** 有按鈕底圖時：三段式拉伸，文字與狀態疊在按鈕面上。素材已含金邊、高光與底部陰影。 */
    private boolean paintAsset(Graphics2D g) {
        String path = CasinoAssets.button(kind == Kind.LARGE, tone.name().toLowerCase(java.util.Locale.ROOT));
        if (CasinoAssets.get(path) == null) {
            return false;
        }
        boolean enabled = isEnabled();
        int w = getWidth();
        int h = getHeight();
        float press = Math.max(0f, lift) * CasinoTheme.px(scale, 3);
        float faceTop = kind == Kind.LARGE ? 0.086f : 0.13f;
        float faceBottom = kind == Kind.LARGE ? 0.814f : 0.72f;
        if (!enabled) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        }
        CasinoPaint.slice3(g, path, 0, press, w, h, kind == Kind.LARGE ? 35 : 23);
        float inset = h * 0.08f;
        RoundRectangle2D.Float face = new RoundRectangle2D.Float(inset, h * faceTop + press,
                w - inset * 2, h * (faceBottom - faceTop), h * 0.6f, h * 0.6f);
        if (!enabled) {
            g.setColor(new Color(70, 60, 70, 110));
            g.fill(face);
        } else if (lift > 0) {
            g.setColor(new Color(0, 0, 0, Math.round(40 * lift)));
            g.fill(face);
        } else if (lift < 0) {
            g.setColor(new Color(255, 255, 255, Math.round(36 * -lift)));
            g.fill(face);
        }
        paintLabel(g, face);
        if (enabled && isFocusOwner()) {
            g.setColor(new Color(255, 255, 255, 210));
            g.setStroke(new BasicStroke(Math.max(1.5f, 2f * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{4f, 4f}, 0f));
            g.draw(new RoundRectangle2D.Float(face.x + 4, face.y + 3, face.width - 8, face.height - 6,
                    face.height - 6, face.height - 6));
        }
        return true;
    }

    private void paintJava2D(Graphics graphics) {
        Graphics2D g = CasinoTheme.quality((Graphics2D) graphics);
        boolean enabled = isEnabled();
        int w = getWidth();
        int h = getHeight();
        float rim = Math.max(2f, 3f * scale);
        int depth = CasinoTheme.px(scale, kind == Kind.LARGE ? 6 : 4);
        int press = Math.round(lift * CasinoTheme.px(scale, 3));
        float shadowGap = depth + 2;
        float bodyH = h - shadowGap - 2;
        float y = 1 + Math.max(0, press) + Math.min(0, press);
        float arc = bodyH;

        if (!enabled) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        }
        g.setColor(new Color(10, 0, 8, 90));
        g.fill(new RoundRectangle2D.Float(3, y + depth + 4, w - 6, bodyH, arc, arc));

        RoundRectangle2D.Float outer = new RoundRectangle2D.Float(1, y, w - 2, bodyH + depth - Math.max(0, press), arc, arc);
        g.setPaint(CasinoTheme.gold(outer.y, outer.y + outer.height));
        g.fill(outer);
        g.setColor(CasinoTheme.GOLD_DARK);
        g.setStroke(new BasicStroke(1f));
        g.draw(outer);

        float inset = rim + 1;
        RoundRectangle2D.Float lipShape = new RoundRectangle2D.Float(inset, y + inset,
                w - inset * 2, bodyH - inset * 2 + depth - Math.max(0, press), arc - inset * 2, arc - inset * 2);
        g.setColor(tone.lip);
        g.fill(lipShape);

        Color top = tone.top;
        Color bottom = tone.bottom;
        if (enabled && lift < 0) {
            top = brighten(top, 0.12f * -lift);
            bottom = brighten(bottom, 0.1f * -lift);
        } else if (!enabled) {
            top = gray(top);
            bottom = gray(bottom);
        }
        RoundRectangle2D.Float face = new RoundRectangle2D.Float(inset, y + inset,
                w - inset * 2, bodyH - inset * 2, arc - inset * 2, arc - inset * 2);
        g.setPaint(new GradientPaint(0, face.y, top, 0, face.y + face.height, bottom));
        g.fill(face);

        Graphics2D gloss = (Graphics2D) g.create();
        gloss.clip(face);
        gloss.setColor(new Color(255, 255, 255, enabled ? 95 : 40));
        float glossH = face.height * 0.42f;
        gloss.fill(new RoundRectangle2D.Float(face.x + face.height * 0.25f, face.y + 2,
                face.width - face.height * 0.5f, glossH, glossH, glossH));
        gloss.dispose();

        paintLabel(g, face);

        if (enabled && isFocusOwner()) {
            g.setColor(new Color(255, 255, 255, 200));
            g.setStroke(new BasicStroke(Math.max(1.5f, 2f * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{4f, 4f}, 0f));
            g.draw(new RoundRectangle2D.Float(face.x + 4, face.y + 4, face.width - 8, face.height - 8,
                    face.height - 8, face.height - 8));
        }
        g.dispose();
    }

    private void paintLabel(Graphics2D g, RoundRectangle2D.Float face) {
        String text = getText() == null ? "" : getText();
        Font font = CasinoTheme.chinese(true, scale, kind.font);
        boolean hasSub = subLabel != null && !subLabel.isEmpty() && kind.subFont > 0;
        Font subFont = CasinoTheme.latin(scale, kind.subFont);
        FontMetrics fm = g.getFontMetrics(font);
        FontMetrics sm = g.getFontMetrics(subFont);
        int iconSize = icon == null ? 0 : CasinoTheme.px(scale, kind.icon);
        int gap = icon == null ? 0 : CasinoTheme.px(scale, 10);
        int textW = Math.max(fm.stringWidth(text), hasSub ? sm.stringWidth(subLabel) : 0);
        int block = iconSize + gap + textW;
        float left = face.x + (face.width - block) / 2f;
        float cy = face.y + face.height / 2f;
        if (icon != null) {
            icon.paint(g, Math.round(left), Math.round(cy - iconSize / 2f), iconSize, Color.WHITE);
        }
        float textX = left + iconSize + gap;
        float textBlock = fm.getAscent() + (hasSub ? sm.getAscent() : 0);
        float baseline = cy - textBlock / 2f + fm.getAscent() - fm.getDescent() / 3f;
        float centerX = textX + textW / 2f;
        drawShadowed(g, text, font, centerX - fm.stringWidth(text) / 2f, baseline);
        if (hasSub) {
            drawShadowed(g, subLabel, subFont, centerX - sm.stringWidth(subLabel) / 2f,
                    baseline + sm.getAscent() + 1);
        }
    }

    private void drawShadowed(Graphics2D g, String text, Font font, float x, float baseline) {
        g.setFont(font);
        g.setColor(new Color(tone.lip.getRed(), tone.lip.getGreen(), tone.lip.getBlue(), 220));
        g.drawString(text, x + 1, baseline + Math.max(1, scale * 1.5f));
        g.setColor(Color.WHITE);
        g.drawString(text, x, baseline);
    }

    private static Color brighten(Color c, float amount) {
        return new Color(
                Math.min(255, Math.round(c.getRed() + (255 - c.getRed()) * amount)),
                Math.min(255, Math.round(c.getGreen() + (255 - c.getGreen()) * amount)),
                Math.min(255, Math.round(c.getBlue() + (255 - c.getBlue()) * amount)));
    }

    private static Color gray(Color c) {
        int avg = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
        return new Color((c.getRed() + avg * 2) / 3, (c.getGreen() + avg * 2) / 3, (c.getBlue() + avg * 2) / 3);
    }
}
