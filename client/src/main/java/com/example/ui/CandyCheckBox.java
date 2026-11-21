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
    /** 同一列均分時由版面寫入欄寬，文字依這個寬度折行。 */
    public static final String WRAP_COL = "wrap.col";
    private static final int BOX = 14;
    private static final int BOX_X = 2;
    private static final int TEXT_GAP = 4;
    private static final int TAIL = 2;

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
            int boxX = BOX_X;
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
            int textX = boxX + BOX + TEXT_GAP;
            int textWidth = Math.max(8, c.getWidth() - textX - TAIL);
            g.setColor(enabled ? CandyTheme.INK : CandyTheme.MUTED);
            java.util.List<String> lines = wrapLines(text, metrics, textWidth);
            int block = lines.size() * metrics.getHeight();
            int textY = Math.max(metrics.getAscent(), (c.getHeight() - block) / 2 + metrics.getAscent());
            for (String line : lines) {
                g.drawString(line, textX, textY);
                textY += metrics.getHeight();
            }
            g.dispose();
        }

        @Override
        public java.awt.Dimension getPreferredSize(JComponent c) {
            AbstractButton button = (AbstractButton) c;
            FontMetrics metrics = c.getFontMetrics(c.getFont());
            String text = button.getText() == null ? "" : button.getText();
            int textWidth = metrics.stringWidth(text);
            int natural = BOX_X + BOX + TEXT_GAP + textWidth + TAIL;
            Object col = c.getClientProperty(WRAP_COL);
            if (col instanceof Integer && (Integer) col > 0) {
                int inner = Math.max(8, (Integer) col - (BOX_X + BOX + TEXT_GAP) - TAIL);
                int lines = wrapLines(text, metrics, inner).size();
                int height = Math.max(metrics.getHeight() + 8, lines * metrics.getHeight() + 6);
                return new java.awt.Dimension((Integer) col, height);
            }
            return new java.awt.Dimension(natural, Math.max(24, metrics.getHeight() + 8));
        }

        private static java.util.List<String> wrapLines(String text, FontMetrics metrics, int width) {
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            if (text == null || text.isEmpty()) {
                lines.add("");
                return lines;
            }
            if (metrics.stringWidth(text) <= width) {
                lines.add(text);
                return lines;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                line.append(ch);
                if (metrics.stringWidth(line.toString()) > width && line.length() > 1) {
                    line.deleteCharAt(line.length() - 1);
                    int space = line.lastIndexOf(" ");
                    if (space > 0) {
                        String rest = line.substring(space + 1);
                        line.setLength(space);
                        lines.add(line.toString());
                        line.setLength(0);
                        line.append(rest);
                    } else {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(ch);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        }
    }
}
