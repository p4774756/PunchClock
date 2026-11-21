package com.example.ui.hud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 底部分頁列 Lucide 線條圖示（24×24 viewBox，僅 path／circle／rect）。
 */
final class TabNavIcon {

    static final int ICON_SIZE = 20;
    static final int ICON_GAP = 4;
    static final String MINI_ICON_ID = "minimize-2";

    private static final String[] NAV_ICON_IDS = {
            "home", "calendar-clock", "folder-up", "notebook-text", "settings"
    };

    private static final Map<String, TabNavIcon> CACHE = new ConcurrentHashMap<>();

    private final List<Path2D> paths;

    private TabNavIcon(List<Path2D> paths) {
        this.paths = paths;
    }

    static String navIconId(int index) {
        if (index < 0 || index >= NAV_ICON_IDS.length) {
            return "";
        }
        return NAV_ICON_IDS[index];
    }

    static TabNavIcon load(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return CACHE.computeIfAbsent(id, TabNavIcon::read);
    }

    boolean ready() {
        return !paths.isEmpty();
    }

    int partCount() {
        return paths.size();
    }

    static void draw(Graphics2D g, String id, int x, int y, int size, Color color) {
        TabNavIcon icon = load(id);
        if (icon == null || !icon.ready() || size < 8) {
            return;
        }
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform previous = g.getTransform();
        Stroke stroke = g.getStroke();
        float line = (float) (2.0 * 24.0 / size);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Path2D path : icon.paths) {
            g.draw(path);
        }
        g.setStroke(stroke);
        g.setTransform(previous);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private static TabNavIcon read(String id) {
        try (InputStream in = TabNavIcon.class.getResourceAsStream("/hud/nav/" + id + ".svg")) {
            if (in == null) {
                return new TabNavIcon(Collections.emptyList());
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new TabNavIcon(parseSvg(text));
        } catch (IOException ex) {
            return new TabNavIcon(Collections.emptyList());
        }
    }

    private static List<Path2D> parseSvg(String text) {
        List<Path2D> paths = new ArrayList<>();
        int index = 0;
        while ((index = text.indexOf("<path", index)) >= 0) {
            int dMark = text.indexOf("d=\"", index);
            if (dMark < 0) {
                break;
            }
            int end = text.indexOf('"', dMark + 3);
            if (end < 0) {
                break;
            }
            Path2D path = KitIcon.parse(text.substring(dMark + 3, end));
            if (path != null) {
                paths.add(path);
            }
            index = end;
        }
        index = 0;
        while ((index = text.indexOf("<circle", index)) >= 0) {
            int end = text.indexOf('>', index);
            if (end < 0) {
                break;
            }
            String tag = text.substring(index, end + 1);
            double cx = attr(tag, "cx");
            double cy = attr(tag, "cy");
            double r = attr(tag, "r");
            if (r > 0) {
                Path2D ring = new Path2D.Float();
                ring.append(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2), false);
                paths.add(ring);
            }
            index = end;
        }
        index = 0;
        while ((index = text.indexOf("<rect", index)) >= 0) {
            int end = text.indexOf('>', index);
            if (end < 0) {
                break;
            }
            String tag = text.substring(index, end + 1);
            double x = attr(tag, "x");
            double y = attr(tag, "y");
            double w = attr(tag, "width");
            double h = attr(tag, "height");
            double rx = attr(tag, "rx");
            if (w > 0 && h > 0) {
                Path2D box = new Path2D.Float();
                if (rx > 0) {
                    box.append(new RoundRectangle2D.Double(x, y, w, h, rx * 2, rx * 2), false);
                } else {
                    box.moveTo(x, y);
                    box.lineTo(x + w, y);
                    box.lineTo(x + w, y + h);
                    box.lineTo(x, y + h);
                    box.closePath();
                }
                paths.add(box);
            }
            index = end;
        }
        return paths;
    }

    private static double attr(String tag, String name) {
        String quoted = name + "=\"";
        int mark = tag.indexOf(quoted);
        if (mark >= 0) {
            int end = tag.indexOf('"', mark + quoted.length());
            if (end > mark) {
                try {
                    return Double.parseDouble(tag.substring(mark + quoted.length(), end));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }
}