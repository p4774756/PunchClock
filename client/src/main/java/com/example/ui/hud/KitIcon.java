package com.example.ui.hud;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 素材包裡的線條圖示。SVG 只有一條 path，畫的時候套上目前的顏色。
 */
final class KitIcon {

    private static final Map<String, Path2D> PATHS = new ConcurrentHashMap<>();

    private KitIcon() {
    }

    static void draw(Graphics2D g, String name, int x, int y, int size, Color color) {
        Path2D path = PATHS.computeIfAbsent(name, KitIcon::load);
        if (path == null || size < 8) {
            return;
        }
        AffineTransform previous = g.getTransform();
        Stroke stroke = g.getStroke();
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setStroke(stroke);
        g.setTransform(previous);
    }

    private static Path2D load(String name) {
        try (InputStream in = KitIcon.class.getResourceAsStream("/hud/icons/" + name + ".svg")) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int mark = text.indexOf("d=\"");
            if (mark < 0) {
                return null;
            }
            int end = text.indexOf('"', mark + 3);
            if (end < 0) {
                return null;
            }
            return parse(text.substring(mark + 3, end));
        } catch (IOException ex) {
            return null;
        }
    }

    static Path2D parse(String data) {
        List<String> tokens = tokens(data);
        Path2D path = new Path2D.Float();
        int index = 0;
        double cx = 0;
        double cy = 0;
        double sx = 0;
        double sy = 0;
        char command = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
                command = token.charAt(0);
                index++;
                if (command == 'Z' || command == 'z') {
                    path.closePath();
                    cx = sx;
                    cy = sy;
                    continue;
                }
            }
            if (command == 0 || index >= tokens.size()) {
                break;
            }
            switch (command) {
                case 'M':
                    cx = number(tokens, index++);
                    cy = number(tokens, index++);
                    path.moveTo(cx, cy);
                    sx = cx;
                    sy = cy;
                    command = 'L';
                    break;
                case 'm':
                    cx += number(tokens, index++);
                    cy += number(tokens, index++);
                    path.moveTo(cx, cy);
                    sx = cx;
                    sy = cy;
                    command = 'l';
                    break;
                case 'L':
                    cx = number(tokens, index++);
                    cy = number(tokens, index++);
                    path.lineTo(cx, cy);
                    break;
                case 'l':
                    cx += number(tokens, index++);
                    cy += number(tokens, index++);
                    path.lineTo(cx, cy);
                    break;
                case 'H':
                    cx = number(tokens, index++);
                    path.lineTo(cx, cy);
                    break;
                case 'h':
                    cx += number(tokens, index++);
                    path.lineTo(cx, cy);
                    break;
                case 'V':
                    cy = number(tokens, index++);
                    path.lineTo(cx, cy);
                    break;
                case 'v':
                    cy += number(tokens, index++);
                    path.lineTo(cx, cy);
                    break;
                case 'C':
                    path.curveTo(
                            number(tokens, index++), number(tokens, index++),
                            number(tokens, index++), number(tokens, index++),
                            number(tokens, index++), number(tokens, index++));
                    cx = path.getCurrentPoint().getX();
                    cy = path.getCurrentPoint().getY();
                    break;
                case 'c':
                    path.curveTo(
                            cx + number(tokens, index++), cy + number(tokens, index++),
                            cx + number(tokens, index++), cy + number(tokens, index++),
                            cx + number(tokens, index++), cy + number(tokens, index++));
                    cx = path.getCurrentPoint().getX();
                    cy = path.getCurrentPoint().getY();
                    break;
                case 'A':
                case 'a':
                    double rx = number(tokens, index++);
                    double ry = number(tokens, index++);
                    double angle = number(tokens, index++);
                    boolean large = number(tokens, index++) != 0;
                    boolean sweep = number(tokens, index++) != 0;
                    double x = number(tokens, index++);
                    double y = number(tokens, index++);
                    if (command == 'a') {
                        x += cx;
                        y += cy;
                    }
                    arcTo(path, cx, cy, rx, ry, angle, large, sweep, x, y);
                    cx = x;
                    cy = y;
                    break;
                default:
                    return path;
            }
        }
        return path;
    }

    private static void arcTo(
            Path2D path, double x0, double y0, double rx, double ry, double phiDeg,
            boolean large, boolean sweep, double x, double y) {
        if (Math.abs(x - x0) < 1e-6 && Math.abs(y - y0) < 1e-6) {
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx < 1e-6 || ry < 1e-6) {
            path.lineTo(x, y);
            return;
        }
        double phi = Math.toRadians(phiDeg % 360.0);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double dx = (x0 - x) / 2.0;
        double dy = (y0 - y) / 2.0;
        double x1 = cos * dx + sin * dy;
        double y1 = -sin * dx + cos * dy;
        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double lambda = x1 * x1 / rx2 + y1 * y1 / ry2;
        if (lambda > 1) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
            rx2 = rx * rx;
            ry2 = ry * ry;
        }
        double den = rx2 * y1 * y1 + ry2 * x1 * x1;
        double coef = den == 0 ? 0 : Math.sqrt(Math.max(0, (rx2 * ry2 - rx2 * y1 * y1 - ry2 * x1 * x1) / den));
        if (large == sweep) {
            coef = -coef;
        }
        double cxp = coef * rx * y1 / ry;
        double cyp = coef * -ry * x1 / rx;
        double cx = cos * cxp - sin * cyp + (x0 + x) / 2.0;
        double cy = sin * cxp + cos * cyp + (y0 + y) / 2.0;
        double theta = angle(1, 0, (x1 - cxp) / rx, (y1 - cyp) / ry);
        double delta = angle((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry);
        if (!sweep && delta > 0) {
            delta -= Math.PI * 2;
        } else if (sweep && delta < 0) {
            delta += Math.PI * 2;
        }
        int parts = Math.max(1, (int) Math.ceil(Math.abs(delta) / (Math.PI / 2.0)));
        double step = delta / parts;
        double handle = 4.0 / 3.0 * Math.tan(step / 4.0);
        for (int i = 0; i < parts; i++) {
            double a0 = theta + step * i;
            double a1 = a0 + step;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            double endX = i == parts - 1 ? x : placeX(cx, cos, sin, rx * c1, ry * s1);
            double endY = i == parts - 1 ? y : placeY(cy, cos, sin, rx * c1, ry * s1);
            path.curveTo(
                    placeX(cx, cos, sin, rx * (c0 - handle * s0), ry * (s0 + handle * c0)),
                    placeY(cy, cos, sin, rx * (c0 - handle * s0), ry * (s0 + handle * c0)),
                    placeX(cx, cos, sin, rx * (c1 + handle * s1), ry * (s1 - handle * c1)),
                    placeY(cy, cos, sin, rx * (c1 + handle * s1), ry * (s1 - handle * c1)),
                    endX,
                    endY);
        }
    }

    private static double placeX(double cx, double cos, double sin, double x, double y) {
        return cx + cos * x - sin * y;
    }

    private static double placeY(double cy, double cos, double sin, double x, double y) {
        return cy + sin * x + cos * y;
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double len = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        if (len == 0) {
            return 0;
        }
        double value = Math.acos(Math.max(-1, Math.min(1, (ux * vx + uy * vy) / len)));
        return ux * vy - uy * vx < 0 ? -value : value;
    }

    private static double number(List<String> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return 0;
        }
        try {
            return Double.parseDouble(tokens.get(index));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static List<String> tokens(String data) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < data.length()) {
            char c = data.charAt(index);
            if (Character.isWhitespace(c) || c == ',') {
                index++;
                continue;
            }
            if (Character.isLetter(c)) {
                tokens.add(String.valueOf(c));
                index++;
                continue;
            }
            int start = index;
            if (c == '+' || c == '-') {
                index++;
            }
            boolean dot = false;
            while (index < data.length()) {
                char n = data.charAt(index);
                if (n >= '0' && n <= '9') {
                    index++;
                    continue;
                }
                if (n == '.' && !dot) {
                    dot = true;
                    index++;
                    continue;
                }
                break;
            }
            if (index == start) {
                index++;
                continue;
            }
            tokens.add(data.substring(start, index));
        }
        return tokens;
    }
}
