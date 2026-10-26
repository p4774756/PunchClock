package com.example.ui;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

/**
 * 日系糖果按鈕。漸層、上緣高光和底部厚度都畫在元件自己的範圍裡，陰影不會被父層裁掉。
 * 主畫面的按鈕都用這個類別。顏色維持這一組，不要再改。
 */
public final class CandyButton extends JButton {

    public enum Variant {
        PRIMARY(new Color(0xFF, 0xB0, 0xD5), new Color(0xFF, 0x68, 0xAC), new Color(0xB8, 0x44, 0x82),
                new Color(0x3A, 0x18, 0x28)),
        SECONDARY(new Color(0xC9, 0xB4, 0xFF), new Color(0x97, 0x74, 0xE5), new Color(0x67, 0x50, 0x9C),
                new Color(0x2C, 0x21, 0x50)),
        DANGER(new Color(0xFF, 0xB5, 0xB7), new Color(0xF0, 0x78, 0x8F), new Color(0xA9, 0x50, 0x65),
                new Color(0x4A, 0x20, 0x30));

        final Color top;
        final Color bottom;
        final Color lip;
        final Color ink;

        Variant(Color top, Color bottom, Color lip, Color ink) {
            this.top = top;
            this.bottom = bottom;
            this.lip = lip;
            this.ink = ink;
        }
    }

    public enum Size {
        SMALL(3, 2, 10, 10),
        MEDIUM(4, 3, 14, 12),
        LARGE(5, 4, 18, 14);

        final int depth;
        final int padV;
        final int padH;
        final int arc;

        Size(int depth, int padV, int padH, int arc) {
            this.depth = depth;
            this.padV = padV;
            this.padH = padH;
            this.arc = arc;
        }
    }

    static final String MARK = "candy.button";

    private final Variant variant;
    private final Size buttonSize;

    public CandyButton(String text) {
        this(text, Variant.PRIMARY, Size.MEDIUM);
    }

    public CandyButton(String text, Variant variant) {
        this(text, variant, Size.MEDIUM);
    }

    public CandyButton(String text, Variant variant, Size buttonSize) {
        super(text == null ? "" : text);
        this.variant = variant == null ? Variant.PRIMARY : variant;
        this.buttonSize = buttonSize == null ? Size.MEDIUM : buttonSize;
        putClientProperty(MARK, Boolean.TRUE);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setForeground(this.variant.ink);
        setUI(new CandyButtonUI(this.variant, this.buttonSize));
    }

    public CandyButton onClick(ActionListener listener) {
        addActionListener(listener);
        return this;
    }

    public Variant variant() {
        return variant;
    }

    public Size buttonSize() {
        return buttonSize;
    }

    private static final class CandyButtonUI extends BasicButtonUI {
        private static final int TOP = 2;
        private static final int PRESS = 3;
        private static final int SHADOW = 3;
        private static final int ANIM_MS = 150;

        private final Variant variant;
        private final Size buttonSize;
        private ChangeListener modelListener;
        private Timer timer;
        private float offset;
        private float lip;
        private float fromOffset;
        private float fromLip;
        private float toOffset;
        private float toLip = -1;
        private long animStart;

        private CandyButtonUI(Variant variant, Size buttonSize) {
            this.variant = variant;
            this.buttonSize = buttonSize;
            this.lip = buttonSize.depth;
            this.toLip = buttonSize.depth;
        }

        private AbstractButton button;

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            button = (AbstractButton) c;
            button.setOpaque(false);
            button.setRolloverEnabled(true);
            modelListener = event -> follow(button);
            button.getModel().addChangeListener(modelListener);
        }

        @Override
        public void uninstallUI(JComponent c) {
            if (timer != null) {
                timer.stop();
            }
            if (c instanceof AbstractButton && modelListener != null) {
                ((AbstractButton) c).getModel().removeChangeListener(modelListener);
            }
            modelListener = null;
            button = null;
            super.uninstallUI(c);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            paint(g, c);
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            AbstractButton b = (AbstractButton) c;
            FontMetrics metrics = c.getFontMetrics(b.getFont());
            int text = metrics.stringWidth(b.getText() == null ? "" : b.getText());
            int icon = 0;
            if (b.getIcon() != null) {
                icon = b.getIcon().getIconWidth() + 6;
            }
            int width = text + icon + buttonSize.padH * 2 + SHADOW * 2;
            int face = metrics.getHeight() + buttonSize.padV * 2;
            int height = TOP + face + buttonSize.depth + PRESS + SHADOW;
            return new Dimension(Math.max(width, 72), height);
        }

        @Override
        public Dimension getMinimumSize(JComponent c) {
            return getPreferredSize(c);
        }

        @Override
        public Dimension getMaximumSize(JComponent c) {
            return getPreferredSize(c);
        }

        @Override
        public int getBaseline(JComponent c, int width, int height) {
            FontMetrics metrics = c.getFontMetrics(c.getFont());
            int face = faceHeight(c);
            return TOP + (face - metrics.getHeight()) / 2 + metrics.getAscent();
        }

        @Override
        public void paint(Graphics graphics, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            follow(b);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            boolean enabled = b.isEnabled();
            boolean hover = enabled && b.getModel().isRollover() && !b.getModel().isArmed();
            Color top = variant.top;
            Color bottom = variant.bottom;
            Color edge = variant.lip;
            Color ink = variant.ink;
            boolean pressed = enabled && b.getModel().isPressed() && b.getModel().isArmed();
            if (!enabled) {
                Color mute = new Color(132, 124, 150);
                top = mix(top, mute, 0.62f);
                bottom = mix(bottom, mute, 0.62f);
                edge = mix(edge, mute, 0.5f);
                ink = new Color(206, 198, 220);
            } else if (pressed) {
                top = mix(top, edge, 0.12f);
                bottom = mix(bottom, edge, 0.18f);
            } else if (hover) {
                top = mix(top, Color.WHITE, 0.2f);
                bottom = mix(bottom, Color.WHITE, 0.12f);
            }

            int x = SHADOW;
            int w = Math.max(12, c.getWidth() - SHADOW * 2);
            int face = faceHeight(c);
            int y = TOP + Math.round(offset);
            int lipPx = Math.max(1, Math.round(lip));
            int slab = face + lipPx;
            int arc = buttonSize.arc;

            float shadowAlpha = enabled ? (offset < 0 ? 0.22f : 0.4f) : 0.1f;
            g.setComposite(AlphaComposite.SrcOver.derive(shadowAlpha));
            g.setColor(new Color(16, 8, 28));
            g.fillRoundRect(x + 2, y + slab, Math.max(8, w - 4), SHADOW + 1, arc, arc);

            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(edge);
            g.fillRoundRect(x, y, w, slab, arc, arc);

            g.setPaint(new GradientPaint(0, y, top, 0, y + face, bottom));
            g.fillRoundRect(x, y, w, face, arc, arc);

            Graphics2D gloss = (Graphics2D) g.create();
            gloss.setClip(new RoundRectangle2D.Float(x, y, w, face, arc, arc));
            gloss.setComposite(AlphaComposite.SrcOver.derive(enabled ? 0.55f : 0.25f));
            gloss.setColor(Color.WHITE);
            int glossH = Math.max(4, face / 4);
            gloss.fillRoundRect(x + 4, y + 2, Math.max(4, w - 8), glossH, arc, arc);
            gloss.dispose();

            paintContent(g, b, x, y, w, face, ink);

            if (enabled && b.isFocusOwner()) {
                g.setStroke(new java.awt.BasicStroke(2f));
                g.setColor(new Color(255, 255, 255));
                g.drawRoundRect(Math.max(1, x - 2), Math.max(1, y - 2), w + 3, slab + 3, arc + 2, arc + 2);
            }
            g.dispose();
        }

        private void paintContent(Graphics2D g, AbstractButton b, int x, int y, int w, int face, Color ink) {
            String text = b.getText() == null ? "" : b.getText();
            Icon icon = b.isEnabled() || b.getDisabledIcon() == null ? b.getIcon() : b.getDisabledIcon();
            FontMetrics metrics = g.getFontMetrics(b.getFont());
            g.setFont(b.getFont());
            int textW = metrics.stringWidth(text);
            int iconW = icon == null ? 0 : icon.getIconWidth();
            int gap = icon == null || text.isEmpty() ? 0 : 6;
            int block = iconW + gap + textW;
            int left = x + Math.max(buttonSize.padH, (w - block) / 2);
            int textY = y + (face - metrics.getHeight()) / 2 + metrics.getAscent();
            if (icon != null) {
                int iconY = y + (face - icon.getIconHeight()) / 2;
                icon.paintIcon(b, g, left, iconY);
                left += iconW + gap;
            }
            g.setColor(ink);
            g.drawString(text, left, textY);
        }

        private int faceHeight(JComponent c) {
            FontMetrics metrics = c.getFontMetrics(c.getFont());
            return metrics.getHeight() + buttonSize.padV * 2;
        }

        private void follow(AbstractButton b) {
            float nextOffset = 0;
            float nextLip = buttonSize.depth;
            if (b.isEnabled() && b.getModel().isPressed() && b.getModel().isArmed()) {
                nextOffset = PRESS;
                nextLip = 1;
            } else if (b.isEnabled() && b.getModel().isRollover()) {
                nextOffset = -TOP;
            }
            if (nextOffset == toOffset && nextLip == toLip) {
                return;
            }
            fromOffset = offset;
            fromLip = lip;
            toOffset = nextOffset;
            toLip = nextLip;
            animStart = System.currentTimeMillis();
            if (timer == null) {
                timer = new Timer(16, e -> step());
            }
            timer.start();
        }

        private void step() {
            float t = Math.min(1f, (System.currentTimeMillis() - animStart) / (float) ANIM_MS);
            float eased = 1f - (1f - t) * (1f - t);
            offset = fromOffset + (toOffset - fromOffset) * eased;
            lip = fromLip + (toLip - fromLip) * eased;
            if (button != null) {
                button.repaint();
            }
            if (t >= 1f && timer != null) {
                timer.stop();
            }
        }

        private static Color mix(Color base, Color toward, float amount) {
            float keep = 1f - amount;
            return new Color(
                    channel(base.getRed() * keep + toward.getRed() * amount),
                    channel(base.getGreen() * keep + toward.getGreen() * amount),
                    channel(base.getBlue() * keep + toward.getBlue() * amount));
        }

        private static int channel(float value) {
            return Math.max(0, Math.min(255, Math.round(value)));
        }
    }
}
