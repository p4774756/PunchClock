package com.example.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 桌面端 Win95 復古元件共用色與邊框半徑（0＝直角斜角）。
 */
public final class CandyTheme {

    public static final Color NIGHT = ClassicBevel.FACE;
    public static final Color PANEL = ClassicBevel.FACE;
    public static final Color FIELD = Color.WHITE;
    public static final Color FIELD_TOP = Color.WHITE;
    public static final Color HEADER = ClassicBevel.FACE;
    public static final Color HEADER_HOT = new Color(0xD4, 0xD4, 0xD4);
    public static final Color EDGE = ClassicBevel.LO;
    public static final Color EDGE_HOT = ClassicBevel.DARK;
    public static final Color PINK = new Color(0, 0, 128);
    public static final Color PINK_DEEP = new Color(0, 0, 104);
    public static final Color PINK_HI = new Color(0, 0, 160);
    public static final Color LAVENDER = new Color(0, 0, 128);
    public static final Color INK = Color.BLACK;
    public static final Color PINK_INK = Color.WHITE;
    public static final Color MUTED = new Color(64, 64, 64);
    public static final Color DISABLED = ClassicBevel.LO;
    public static final Color ROW = Color.WHITE;
    public static final Color ROW_ALT = new Color(248, 248, 248);
    public static final Color ROW_HOT = new Color(0, 0, 128);
    public static final Color ROW_SELECT = new Color(0, 0, 128);
    public static final Color ROW_LATEST = new Color(248, 248, 248);

    public static final int RADIUS_FIELD = 0;
    public static final int RADIUS_CHECK = 0;
    public static final int RADIUS_PANEL = 0;
    public static final int RADIUS_SCROLL = 0;
    public static final int ANIM_MS = 80;
    public static final int SCROLL_WIDTH = 16;

    private static final String MOTION = "candy.motion";
    private static final String WATCH = "candy.watch";

    private CandyTheme() {
    }

    public static Color mix(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        float u = 1f - t;
        return new Color(
                Math.round(from.getRed() * u + to.getRed() * t),
                Math.round(from.getGreen() * u + to.getGreen() * t),
                Math.round(from.getBlue() * u + to.getBlue() * t));
    }

    public static Color fade(Color color) {
        return mix(color, DISABLED, 0.55f);
    }

    public static String fit(String text, FontMetrics metrics, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String dots = "…";
        int dotsWidth = metrics.stringWidth(dots);
        if (width <= dotsWidth) {
            return dots;
        }
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) + dotsWidth > width) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + dots;
    }

    public static void watch(JComponent host) {
        watch(host, host);
    }

    public static void watch(JComponent host, Component part) {
        if (part instanceof JComponent
                && Boolean.TRUE.equals(((JComponent) part).getClientProperty(WATCH))) {
            return;
        }
        if (part instanceof JComponent) {
            ((JComponent) part).putClientProperty(WATCH, Boolean.TRUE);
        }
        Motion motion = motion(host);
        part.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                motion.aim(1f);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point point = javax.swing.SwingUtilities.convertPoint(part, e.getPoint(), host);
                if (!host.contains(point)) {
                    motion.aim(0f);
                }
            }
        });
    }

    public static float hover(JComponent component) {
        Object motion = component.getClientProperty(MOTION);
        return motion instanceof Motion ? ((Motion) motion).value() : 0f;
    }

    private static Motion motion(JComponent host) {
        Object existing = host.getClientProperty(MOTION);
        if (existing instanceof Motion) {
            return (Motion) existing;
        }
        Motion created = new Motion(host);
        host.putClientProperty(MOTION, created);
        return created;
    }

    static final class Motion {
        private final JComponent host;
        private float shown;
        private float goal;
        private float from;
        private long started;
        private Timer timer;

        Motion(JComponent host) {
            this.host = host;
        }

        void aim(float next) {
            if (Math.abs(goal - next) < 0.001f && Math.abs(shown - next) < 0.001f) {
                return;
            }
            goal = next;
            from = shown;
            started = System.currentTimeMillis();
            if (timer == null) {
                timer = new Timer(16, event -> tick());
                timer.setRepeats(true);
            }
            timer.start();
        }

        float value() {
            return shown;
        }

        private void tick() {
            float t = Math.min(1f, (System.currentTimeMillis() - started) / (float) ANIM_MS);
            float eased = 1f - (1f - t) * (1f - t);
            shown = from + (goal - from) * eased;
            host.repaint();
            if (t >= 1f && timer != null) {
                timer.stop();
            }
        }
    }
}