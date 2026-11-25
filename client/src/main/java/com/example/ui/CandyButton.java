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
/** Win95 灰斜角按鈕；主畫面按鈕皆用此類。 */
public final class CandyButton extends JButton {

    public enum Variant {
        PRIMARY(ClassicBevel.FACE, ClassicBevel.FACE, ClassicBevel.DARK, Color.BLACK),
        SECONDARY(ClassicBevel.FACE, ClassicBevel.FACE, ClassicBevel.DARK, Color.BLACK),
        DANGER(ClassicBevel.FACE, ClassicBevel.FACE, ClassicBevel.DARK, Color.BLACK);

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
        /** 380px 小方塊裡跟其他控制項擠同一列時用，字還是原來的字級。 */
        COMPACT(2, 1, 4, 8),
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
            int width = text + icon + buttonSize.padH * 2 + 6;
            int face = metrics.getHeight() + buttonSize.padV * 2;
            int height = face + 8;
            return new Dimension(Math.max(width, 48), height);
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
            boolean enabled = b.isEnabled();
            Color ink = enabled ? variant.ink : CandyTheme.MUTED;
            boolean pressed = enabled && b.getModel().isPressed() && b.getModel().isArmed();
            int x = 2;
            int w = Math.max(12, c.getWidth() - 4);
            int face = faceHeight(c);
            int y = 2 + Math.round(offset);
            if (pressed) {
                ClassicBevel.fillSunken(g, x, y, w, face + 2, ClassicBevel.FACE);
            } else {
                ClassicBevel.fillRaised(g, x, y, w, face + 2, enabled ? ClassicBevel.FACE : CandyTheme.fade(ClassicBevel.FACE));
            }
            paintContent(g, b, x, y, w, face, ink);
            if (enabled && b.isFocusOwner()) {
                g.setColor(Color.BLACK);
                g.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
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
