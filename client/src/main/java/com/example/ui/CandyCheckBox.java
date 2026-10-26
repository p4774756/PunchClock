package com.example.ui;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;

/**
 * 圓角核取方塊。選取、鍵盤與事件仍走 {@link JCheckBox}。
 */
public class CandyCheckBox extends JCheckBox {

    public static final String MARK = "candy.check";
    private static final int BOX = 16;

    public CandyCheckBox(String text, boolean selected) {
        super(text, selected);
        setOpaque(false);
        setRolloverEnabled(true);
        setFocusPainted(false);
        putClientProperty(MARK, Boolean.TRUE);
        CandyTheme.watch(this);
    }

    @Override
    public void updateUI() {
        setUI(new CheckUI());
    }

    static final class CheckUI extends BasicCheckBoxUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
            ((AbstractButton) c).setRolloverEnabled(true);
            ((AbstractButton) c).setFocusPainted(false);
            CandyTheme.watch((JComponent) c);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            paint(g, c);
        }

        @Override
        public void paint(Graphics graphics, JComponent c) {
            AbstractButton button = (AbstractButton) c;
            Graphics2D g = CandyDraw.quality((Graphics2D) graphics);
            boolean enabled = button.isEnabled();
            boolean selected = button.getModel().isSelected();
            boolean pressed = enabled && button.getModel().isPressed();
            float hover = enabled ? CandyTheme.hover(button) : 0f;
            int boxY = Math.max(2, (c.getHeight() - BOX) / 2);
            int boxX = 3;
            if (hover > 0f) {
                g.setColor(new java.awt.Color(255, 192, 222, Math.round(80 * hover)));
                g.fillRoundRect(boxX - 3, boxY - 3, BOX + 6, BOX + 6, 10, 10);
            }
            if (selected) {
                java.awt.Color top = enabled ? CandyTheme.PINK_HI : CandyTheme.fade(CandyTheme.PINK_HI);
                java.awt.Color bottom = enabled ? CandyTheme.PINK_DEEP : CandyTheme.fade(CandyTheme.PINK_DEEP);
                if (pressed) {
                    top = CandyTheme.mix(top, CandyTheme.PINK_DEEP, 0.35f);
                    bottom = CandyTheme.mix(bottom, CandyTheme.NIGHT, 0.25f);
                }
                g.setPaint(new GradientPaint(boxX, boxY, top, boxX, boxY + BOX, bottom));
                g.fillRoundRect(boxX, boxY, BOX, BOX, CandyTheme.RADIUS_CHECK, CandyTheme.RADIUS_CHECK);
                g.setColor(new java.awt.Color(255, 255, 255, enabled ? 70 : 30));
                g.fillRoundRect(boxX + 2, boxY + 1, BOX - 4, BOX / 3, CandyTheme.RADIUS_CHECK, CandyTheme.RADIUS_CHECK);
                CandyDraw.check(g, boxX, boxY, BOX, enabled ? CandyTheme.INK : CandyTheme.MUTED);
            } else {
                java.awt.Color fill = enabled ? CandyTheme.FIELD : CandyTheme.fade(CandyTheme.FIELD);
                if (pressed) {
                    fill = CandyTheme.mix(fill, CandyTheme.NIGHT, 0.2f);
                }
                g.setColor(fill);
                g.fillRoundRect(boxX, boxY, BOX, BOX, CandyTheme.RADIUS_CHECK, CandyTheme.RADIUS_CHECK);
                java.awt.Color edge = enabled
                        ? CandyTheme.mix(CandyTheme.EDGE, CandyTheme.EDGE_HOT, hover)
                        : CandyTheme.fade(CandyTheme.EDGE);
                g.setColor(edge);
                g.drawRoundRect(boxX, boxY, BOX, BOX, CandyTheme.RADIUS_CHECK, CandyTheme.RADIUS_CHECK);
            }
            if (enabled && button.isFocusOwner()) {
                g.setColor(CandyTheme.PINK);
                g.drawRoundRect(boxX - 2, boxY - 2, BOX + 4, BOX + 4, 8, 8);
            }
            String text = button.getText() == null ? "" : button.getText();
            g.setFont(button.getFont());
            FontMetrics metrics = g.getFontMetrics();
            int textX = boxX + BOX + 8;
            int textY = (c.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(enabled ? CandyTheme.INK : CandyTheme.MUTED);
            g.drawString(text, textX, textY);
            g.dispose();
        }

        @Override
        public java.awt.Dimension getPreferredSize(JComponent c) {
            AbstractButton button = (AbstractButton) c;
            FontMetrics metrics = c.getFontMetrics(c.getFont());
            String text = button.getText() == null ? "" : button.getText();
            int textWidth = metrics.stringWidth(text);
            return new java.awt.Dimension(3 + BOX + 8 + textWidth + 6, Math.max(24, metrics.getHeight() + 8));
        }
    }
}
