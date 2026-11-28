package com.example.ui;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * 糖果配色的滑動開關。選取狀態與鍵盤操作仍走 {@link JToggleButton}。
 */
public class CandySwitch extends JToggleButton {

    private static final int TRACK_W = 44;
    private static final int TRACK_H = 24;
    private static final int KNOB = 18;

    /** 開啟軌道（明亮粉紅，與白色圓點對比達 WCAG AA）。 */
    static final Color TRACK_ON_TOP = new Color(255, 108, 168);
    static final Color TRACK_ON_BOTTOM = new Color(200, 36, 102);
    /** 關閉軌道（淺灰，外框清楚；底部略深以襯出白點）。 */
    static final Color TRACK_OFF_TOP = new Color(210, 210, 218);
    static final Color TRACK_OFF_BOTTOM = new Color(124, 124, 136);
    static final Color TRACK_EDGE_OFF = new Color(56, 56, 68);
    static final Color TRACK_EDGE_ON = new Color(196, 48, 108);
    static final Color KNOB_FILL = Color.WHITE;
    static final Color KNOB_FILL_DISABLED = new Color(244, 244, 248);

    private float shown;
    private float target;
    private Timer glide;

    public CandySwitch() {
        setOpaque(false);
        setRolloverEnabled(true);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getModel().addChangeListener(event -> glideTo(isSelected() ? 1f : 0f));
    }

    @Override
    public void updateUI() {
        setUI(new SwitchUI());
    }

    @Override
    public void removeNotify() {
        if (glide != null) {
            glide.stop();
        }
        super.removeNotify();
    }

    private void glideTo(float next) {
        target = next;
        if (!isDisplayable()) {
            shown = next;
            if (glide != null) {
                glide.stop();
            }
            repaint();
            return;
        }
        if (Math.abs(shown - next) < 0.01f) {
            shown = next;
            if (glide != null) {
                glide.stop();
            }
            repaint();
            return;
        }
        if (glide == null) {
            glide = new Timer(16, event -> stepGlide());
            glide.setRepeats(true);
        }
        if (!glide.isRunning()) {
            glide.start();
        }
    }

    private void stepGlide() {
        float delta = target - shown;
        if (Math.abs(delta) < 0.05f) {
            shown = target;
            glide.stop();
        } else {
            shown += delta * 0.45f;
        }
        repaint();
    }

    float shownAmount() {
        return shown;
    }

    static final class SwitchUI extends BasicToggleButtonUI {
        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton button = (AbstractButton) c;
            button.setOpaque(false);
            button.setRolloverEnabled(true);
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            CandyTheme.watch(button);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            paint(g, c);
        }

        @Override
        public void paint(Graphics graphics, JComponent c) {
            CandySwitch sw = (CandySwitch) c;
            AbstractButton button = sw;
            Graphics2D g = CandyDraw.quality((Graphics2D) graphics);
            try {
                boolean enabled = button.isEnabled();
                float shown = sw.shownAmount();
                float hover = enabled ? CandyTheme.hover(button) : 0f;
                int trackX = 1;
                int trackY = Math.max(1, (c.getHeight() - TRACK_H) / 2);
                Color offTop = enabled ? TRACK_OFF_TOP : CandyTheme.mix(TRACK_OFF_TOP, CandyTheme.DISABLED, 0.35f);
                Color offBottom = enabled ? TRACK_OFF_BOTTOM : CandyTheme.mix(TRACK_OFF_BOTTOM, CandyTheme.DISABLED, 0.35f);
                Color onTop = enabled ? TRACK_ON_TOP : CandyTheme.mix(TRACK_ON_TOP, CandyTheme.DISABLED, 0.35f);
                Color onBottom = enabled ? TRACK_ON_BOTTOM : CandyTheme.mix(TRACK_ON_BOTTOM, CandyTheme.DISABLED, 0.35f);
                if (enabled && button.getModel().isPressed()) {
                    onTop = CandyTheme.mix(onTop, TRACK_ON_BOTTOM, 0.25f);
                    onBottom = CandyTheme.mix(onBottom, TRACK_ON_BOTTOM, 0.35f);
                }
                g.setPaint(new GradientPaint(
                        trackX, trackY, CandyTheme.mix(offTop, onTop, shown),
                        trackX, trackY + TRACK_H, CandyTheme.mix(offBottom, onBottom, shown)));
                g.fillRoundRect(trackX, trackY, TRACK_W, TRACK_H, TRACK_H, TRACK_H);
                Color edgeOff = enabled ? TRACK_EDGE_OFF : CandyTheme.mix(TRACK_EDGE_OFF, CandyTheme.DISABLED, 0.35f);
                Color edgeOn = enabled ? TRACK_EDGE_ON : CandyTheme.mix(TRACK_EDGE_ON, CandyTheme.DISABLED, 0.35f);
                Color edge = CandyTheme.mix(edgeOff, edgeOn, shown);
                if (enabled && hover > 0f) {
                    edge = CandyTheme.mix(edge, edgeOn, hover * 0.45f);
                }
                g.setColor(edge);
                g.drawRoundRect(trackX, trackY, TRACK_W, TRACK_H, TRACK_H, TRACK_H);
                int travel = TRACK_W - KNOB - 6;
                int knobX = trackX + 3 + Math.round(travel * shown);
                int knobY = trackY + (TRACK_H - KNOB) / 2;
                if (enabled && button.getModel().isPressed()) {
                    knobY += 1;
                }
                g.setColor(new Color(0, 0, 0, enabled ? 45 : 18));
                g.fillOval(knobX, knobY + 1, KNOB, KNOB);
                g.setColor(enabled ? KNOB_FILL : KNOB_FILL_DISABLED);
                g.fillOval(knobX, knobY, KNOB, KNOB);
                if (enabled && button.isFocusOwner()) {
                    g.setColor(TRACK_ON_TOP);
                    g.drawRoundRect(trackX - 2, trackY - 2, TRACK_W + 4, TRACK_H + 4, TRACK_H, TRACK_H);
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return new Dimension(TRACK_W + 4, TRACK_H + 6);
        }
    }
}
