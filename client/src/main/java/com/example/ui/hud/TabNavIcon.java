package com.example.ui.hud;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 底部分頁列填色向量圖示（24×24 viewBox，漸層／填色／描邊／clip）。
 */
final class TabNavIcon {

    static final int ICON_SIZE = 22;
    static final int ICON_GAP = 4;

    private static final double VIEW = 24.0;

    private static final String[] NAV_ICON_IDS = {
            "home", "calendar-clock", "folder-up", "notebook-text", "settings"
    };

    private static final Map<String, TabNavIcon> CACHE = new ConcurrentHashMap<>();

    private final List<DrawOp> ops;

    private TabNavIcon(List<DrawOp> ops) {
        this.ops = ops;
    }

    static String navIconId(int index) {
        if (index < 0 || index >= NAV_ICON_IDS.length) {
            return "";
        }
        return NAV_ICON_IDS[index];
    }

    static TabNavIcon load(String id) {
        return load(id, false);
    }

    static TabNavIcon loadActive(String id) {
        return load(id, true);
    }

    static TabNavIcon load(String id, boolean active) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String key = active ? id + "\0active" : id;
        return CACHE.computeIfAbsent(key, k -> read(id, active));
    }

    boolean ready() {
        return !ops.isEmpty();
    }

    int partCount() {
        return ops.size();
    }

    static void draw(Graphics2D g, String id, int x, int y, int size, boolean active) {
        TabNavIcon icon = load(id, active);
        if (icon == null || !icon.ready() || size < 8) {
            return;
        }
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object interp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        AffineTransform previous = g.getTransform();
        Composite composite = g.getComposite();
        Stroke stroke = g.getStroke();
        Paint paint = g.getPaint();
        g.translate(x, y);
        g.scale(size / VIEW, size / VIEW);
        for (DrawOp op : icon.ops) {
            op.paint(g);
        }
        g.setPaint(paint);
        g.setStroke(stroke);
        g.setComposite(composite);
        g.setTransform(previous);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        if (interp != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        }
    }

    private static TabNavIcon read(String id, boolean active) {
        String file = active ? id + "-active" : id;
        try (InputStream in = TabNavIcon.class.getResourceAsStream("/hud/nav/" + file + ".svg")) {
            if (in == null) {
                return new TabNavIcon(Collections.emptyList());
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new TabNavIcon(parseSvg(text));
        } catch (IOException ex) {
            return new TabNavIcon(Collections.emptyList());
        }
    }

    private interface DrawOp {
        void paint(Graphics2D g);
    }

    private static final class LinearGrad {
        final double x1;
        final double y1;
        final double x2;
        final double y2;
        final List<Stop> stops = new ArrayList<>();

        LinearGrad(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        GradientPaint paint() {
            if (stops.isEmpty()) {
                return new GradientPaint(0, 0, Color.GRAY, 0, (float) VIEW, Color.GRAY);
            }
            Stop first = stops.get(0);
            Stop last = stops.get(stops.size() - 1);
            return new GradientPaint(
                    (float) (x1 * VIEW), (float) (y1 * VIEW), first.color,
                    (float) (x2 * VIEW), (float) (y2 * VIEW), last.color);
        }
    }

    private static final class Stop {
        final Color color;

        Stop(Color color) {
            this.color = color;
        }
    }

    private static List<DrawOp> parseSvg(String text) {
        Map<String, LinearGrad> gradients = parseGradients(text);
        Map<String, Shape> clips = parseClipPaths(text);
        List<DrawOp> ops = new ArrayList<>();
        parseClippedGroups(text, gradients, clips, ops);
        parseTagLoop(text, "<path", ops, gradients, clips, TabNavIcon::pathOp);
        parseTagLoop(text, "<circle", ops, gradients, clips, TabNavIcon::circleOp);
        parseTagLoop(text, "<ellipse", ops, gradients, clips, TabNavIcon::ellipseOp);
        parseTagLoop(text, "<rect", ops, gradients, clips, null);
        return ops;
    }

    private interface TagOp {
        DrawOp from(String tag, Map<String, LinearGrad> gradients, Map<String, Shape> clips);
    }

    private static void parseTagLoop(
            String text,
            String open,
            List<DrawOp> ops,
            Map<String, LinearGrad> gradients,
            Map<String, Shape> clips,
            TagOp factory) {
        int index = 0;
        while ((index = text.indexOf(open, index)) >= 0) {
            if (isInsideDefs(text, index)) {
                index += open.length();
                continue;
            }
            if (isInsideClippedGroup(text, index)) {
                index += open.length();
                continue;
            }
            int end = text.indexOf('>', index);
            if (end < 0) {
                break;
            }
            String tag = text.substring(index, end + 1);
            index = end + 1;
            if (open.equals("<rect")) {
                DrawOp op = rectOp(tag, gradients, clips);
                if (op != null) {
                    ops.add(op);
                }
            } else if (factory != null) {
                DrawOp op = factory.from(tag, gradients, clips);
                if (op != null) {
                    ops.add(op);
                }
            }
        }
    }

    private static void parseClippedGroups(
            String text,
            Map<String, LinearGrad> gradients,
            Map<String, Shape> clips,
            List<DrawOp> ops) {
        int index = 0;
        while ((index = text.indexOf("<g clip-path", index)) >= 0) {
            if (isInsideDefs(text, index)) {
                index += 3;
                continue;
            }
            String clipId = urlId(attr(text, index, "clip-path"));
            int innerStart = text.indexOf('>', index) + 1;
            int innerEnd = text.indexOf("</g>", innerStart);
            if (innerEnd < 0) {
                break;
            }
            String inner = text.substring(innerStart, innerEnd);
            Shape clip = clipId == null ? null : clips.get(clipId);
            int ei = 0;
            while ((ei = inner.indexOf("<ellipse", ei)) >= 0) {
                int eEnd = inner.indexOf('>', ei);
                if (eEnd < 0) {
                    break;
                }
                DrawOp op = ellipseOp(inner.substring(ei, eEnd + 1), gradients, clips);
                if (op != null && clip != null) {
                    Shape clipShape = clip;
                    ops.add(g -> {
                        Shape save = g.getClip();
                        g.clip(clipShape);
                        op.paint(g);
                        g.setClip(save);
                    });
                } else if (op != null) {
                    ops.add(op);
                }
                ei = eEnd + 1;
            }
            index = innerEnd + 4;
        }
    }

    private static Map<String, LinearGrad> parseGradients(String text) {
        Map<String, LinearGrad> map = new HashMap<>();
        Pattern p = Pattern.compile(
                "<linearGradient\\s+id=\"([^\"]+)\"[^>]*x1=\"([^\"]+)\"\\s+y1=\"([^\"]+)\"\\s+x2=\"([^\"]+)\"\\s+y2=\"([^\"]+)\"[^>]*>",
                Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String id = m.group(1);
            LinearGrad grad = new LinearGrad(
                    parseCoord(m.group(2)), parseCoord(m.group(3)),
                    parseCoord(m.group(4)), parseCoord(m.group(5)));
            int blockEnd = text.indexOf("</linearGradient>", m.end());
            if (blockEnd < 0) {
                continue;
            }
            String block = text.substring(m.end(), blockEnd);
            int si = 0;
            while ((si = block.indexOf("<stop", si)) >= 0) {
                String stopTag = block.substring(si, block.indexOf('>', si) + 1);
                Color c = parseColor(attrString(stopTag, "stop-color"));
                if (c != null) {
                    grad.stops.add(new Stop(c));
                }
                si += 5;
            }
            map.put(id, grad);
        }
        return map;
    }

    private static Map<String, Shape> parseClipPaths(String text) {
        Map<String, Shape> map = new HashMap<>();
        int index = 0;
        while ((index = text.indexOf("<clipPath", index)) >= 0) {
            String id = attr(text, index, "id");
            int pathMark = text.indexOf("<path", index);
            int clipEnd = text.indexOf("</clipPath>", index);
            if (pathMark < 0 || pathMark > clipEnd) {
                index += 9;
                continue;
            }
            int dMark = text.indexOf("d=\"", pathMark);
            int dEnd = text.indexOf('"', dMark + 3);
            if (dMark >= 0 && dEnd > dMark && id != null) {
                Path2D path = KitIcon.parse(text.substring(dMark + 3, dEnd));
                if (path != null) {
                    map.put(id, path);
                }
            }
            index = clipEnd + 11;
        }
        return map;
    }

    private static DrawOp pathOp(String tag, Map<String, LinearGrad> gradients, Map<String, Shape> clips) {
        int dMark = tag.indexOf("d=\"");
        if (dMark < 0) {
            return null;
        }
        int dEnd = tag.indexOf('"', dMark + 3);
        Path2D path = KitIcon.parse(tag.substring(dMark + 3, dEnd));
        if (path == null) {
            return null;
        }
        return shapeOp(path, tag, gradients);
    }

    private static DrawOp circleOp(String tag, Map<String, LinearGrad> gradients, Map<String, Shape> clips) {
        double cx = attr(tag, "cx");
        double cy = attr(tag, "cy");
        double r = attr(tag, "r");
        if (r <= 0) {
            return null;
        }
        return shapeOp(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2), tag, gradients);
    }

    private static DrawOp ellipseOp(String tag, Map<String, LinearGrad> gradients, Map<String, Shape> clips) {
        double cx = attr(tag, "cx");
        double cy = attr(tag, "cy");
        double rx = attr(tag, "rx");
        double ry = attr(tag, "ry");
        if (rx <= 0 || ry <= 0) {
            return null;
        }
        return shapeOp(new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2), tag, gradients);
    }

    private static DrawOp rectOp(String tag, Map<String, LinearGrad> gradients, Map<String, Shape> clips) {
        double x = attr(tag, "x");
        double y = attr(tag, "y");
        double w = attr(tag, "width");
        double h = attr(tag, "height");
        if (w <= 0 || h <= 0) {
            return null;
        }
        double rx = attr(tag, "rx");
        Shape shape;
        if (rx > 0) {
            shape = new RoundRectangle2D.Double(x, y, w, h, rx * 2, rx * 2);
        } else {
            shape = new Rectangle2D.Double(x, y, w, h);
        }
        return shapeOp(shape, tag, gradients);
    }

    private static DrawOp shapeOp(Shape shape, String tag, Map<String, LinearGrad> gradients) {
        String fillAttr = attrString(tag, "fill");
        String strokeAttr = attrString(tag, "stroke");
        double fillOpacity = attr(tag, "fill-opacity", 1.0);
        float strokeWidth = (float) attr(tag, "stroke-width", 0);
        boolean hasFill = fillAttr != null && !"none".equalsIgnoreCase(fillAttr);
        boolean hasStroke = strokeAttr != null && !"none".equalsIgnoreCase(strokeAttr) && strokeWidth > 0;
        if (!hasFill && !hasStroke) {
            return null;
        }
        Paint fillPaint = hasFill ? resolvePaint(fillAttr, gradients) : null;
        Color strokeColor = hasStroke ? parseColor(strokeAttr) : null;
        int cap = strokeCap(tag);
        int join = strokeJoin(tag);
        return g -> {
            if (hasFill && fillPaint != null) {
                Composite save = g.getComposite();
                if (fillOpacity < 1.0) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fillOpacity));
                }
                g.setPaint(fillPaint);
                g.fill(shape);
                if (fillOpacity < 1.0) {
                    g.setComposite(save);
                }
            }
            if (hasStroke && strokeColor != null) {
                Stroke save = g.getStroke();
                g.setColor(strokeColor);
                g.setStroke(new BasicStroke(strokeWidth, cap, join));
                g.draw(shape);
                g.setStroke(save);
            }
        };
    }

    private static Paint resolvePaint(String value, Map<String, LinearGrad> gradients) {
        if (value == null || "none".equalsIgnoreCase(value)) {
            return null;
        }
        String gradId = urlId(value);
        if (gradId != null) {
            LinearGrad grad = gradients.get(gradId);
            if (grad != null) {
                return grad.paint();
            }
        }
        Color c = parseColor(value);
        return c;
    }

    private static String urlId(String value) {
        if (value == null) {
            return null;
        }
        int hash = value.indexOf('#');
        if (hash < 0) {
            return null;
        }
        String id = value.substring(hash + 1);
        int paren = id.indexOf(')');
        if (paren >= 0) {
            id = id.substring(0, paren);
        }
        return id.isEmpty() ? null : id;
    }

    private static Color parseColor(String value) {
        if (value == null || value.isEmpty() || "none".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.charAt(0) == '#') {
            String hex = value.substring(1);
            if (hex.length() == 6) {
                return new Color(
                        Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16));
            }
        }
        if (value.equalsIgnoreCase("#FFFFFF") || value.equalsIgnoreCase("white")) {
            return Color.WHITE;
        }
        return null;
    }

    private static double parseCoord(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            if (value.endsWith("%")) {
                return Double.parseDouble(value.substring(0, value.length() - 1)) / 100.0;
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int strokeCap(String tag) {
        String v = attrString(tag, "stroke-linecap");
        if ("square".equals(v)) {
            return BasicStroke.CAP_SQUARE;
        }
        if ("butt".equals(v)) {
            return BasicStroke.CAP_BUTT;
        }
        return BasicStroke.CAP_ROUND;
    }

    private static int strokeJoin(String tag) {
        String v = attrString(tag, "stroke-linejoin");
        if ("miter".equals(v)) {
            return BasicStroke.JOIN_MITER;
        }
        if ("bevel".equals(v)) {
            return BasicStroke.JOIN_BEVEL;
        }
        return BasicStroke.JOIN_ROUND;
    }

    private static boolean isInsideDefs(String text, int index) {
        int defsStart = text.lastIndexOf("<defs", index);
        if (defsStart < 0) {
            return false;
        }
        int defsEnd = text.indexOf("</defs>", defsStart);
        return defsEnd < 0 || index < defsEnd;
    }

    private static boolean isInsideClippedGroup(String text, int index) {
        int gStart = text.lastIndexOf("<g clip-path", index);
        if (gStart < 0) {
            return false;
        }
        int gEnd = text.indexOf("</g>", gStart);
        return gEnd < 0 || index <= gEnd;
    }

    private static String attr(String text, int from, String name) {
        int mark = text.indexOf(name + "=\"", from);
        if (mark < 0 || mark > from + 200) {
            return null;
        }
        int end = text.indexOf('"', mark + name.length() + 2);
        if (end < 0) {
            return null;
        }
        return text.substring(mark + name.length() + 2, end);
    }

    private static String attrString(String tag, String name) {
        String quoted = name + "=\"";
        int mark = tag.indexOf(quoted);
        if (mark >= 0) {
            int end = tag.indexOf('"', mark + quoted.length());
            if (end > mark) {
                return tag.substring(mark + quoted.length(), end);
            }
        }
        return null;
    }

    private static double attr(String tag, String name) {
        String s = attrString(tag, name);
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double attr(String tag, String name, double fallback) {
        String s = attrString(tag, name);
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}