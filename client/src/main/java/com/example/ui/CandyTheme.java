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
 * 排程頁糖果元件共用的顏色、圓角與短動畫。按鈕本體的配色仍留在 {@link CandyButton}，避免已定稿的立體鈕被改掉。
 */
public final class CandyTheme {

    public static final Color NIGHT = new Color(0x24, 0x18, 0x3F);
    public static final Color PANEL = new Color(0x38, 0x29, 0x5F);
    public static final Color FIELD = new Color(0x2C, 0x20, 0x4C);
    public static final Color FIELD_TOP = new Color(0x3A, 0x2C, 0x62);
    public static final Color HEADER = new Color(0x46, 0x35, 0x6E);
    public static final Color HEADER_HOT = new Color(0x54, 0x40, 0x80);
    public static final Color EDGE = new Color(0x92, 0x75, 0xBD);
    public static final Color EDGE_HOT = new Color(0xB7, 0x9A, 0xEF);
    public static final Color PINK = new Color(0xFF, 0x78, 0xB5);
    public static final Color PINK_DEEP = new Color(0xE5, 0x58, 0x9C);
    public static final Color PINK_HI = new Color(0xFF, 0xC0, 0xDE);
    public static final Color LAVENDER = new Color(0xB7, 0x9A, 0xEF);
    public static final Color INK = new Color(0xF8, 0xF1, 0xFF);
    public static final Color PINK_INK = new Color(0x3A, 0x18, 0x28);
    public static final Color MUTED = new Color(0xC8, 0xB8, 0xE4);
    public static final Color DISABLED = new Color(0x84, 0x7C, 0x96);
    public static final Color ROW = new Color(0x32, 0x24, 0x58);
    public static final Color ROW_ALT = new Color(0x3C, 0x2C, 0x66);
    public static final Color ROW_HOT = new Color(0x4A, 0x38, 0x78);
    public static final Color ROW_SELECT = new Color(0x8A, 0x5A, 0x98);
    public static final Color ROW_LATEST = new Color(0x42, 0x30, 0x72);

    public static final int RADIUS_FIELD = 8;
    public static final int RADIUS_CHECK = 6;
    public static final int RADIUS_PANEL = 12;
    public static final int RADIUS_SCROLL = 8;
    public static final int ANIM_MS = 150;
    public static final int SCROLL_WIDTH = 12;

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

    /** 滑入滑出約 150ms，停在定點後不再重繪。 */
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
