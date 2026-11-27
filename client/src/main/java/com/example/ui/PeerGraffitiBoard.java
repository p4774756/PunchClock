package com.example.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

/** Win98 風格迷你畫布（320×220）。 */
public final class PeerGraffitiBoard extends JComponent {

    public static final int CANVAS_W = 320;
    public static final int CANVAS_H = 220;

  static final Color[] PALETTE = {
            Color.BLACK,
            new Color(220, 40, 40),
            new Color(40, 80, 220),
            new Color(40, 160, 60),
            new Color(240, 200, 40),
            new Color(255, 150, 180),
    };

    static final int[] BRUSH_SIZES = {2, 6, 12};

    private static final class Stroke {
        final Color color;
        final float width;
        final List<Point> points = new ArrayList<>();

        Stroke(Color color, float width) {
            this.color = color;
            this.width = width;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private Color brushColor = PALETTE[0];
    private int brushSizeIndex = 1;
    private boolean eraser;

    public PeerGraffitiBoard() {
        setPreferredSize(new Dimension(CANVAS_W, CANVAS_H));
        setMinimumSize(new Dimension(CANVAS_W, CANVAS_H));
        setMaximumSize(new Dimension(CANVAS_W, CANVAS_H));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(ClassicBevel.sunkenBorder(0));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter painting = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled()) {
                    return;
                }
                current = new Stroke(eraser ? Color.WHITE : brushColor, currentBrushWidth());
                current.points.add(e.getPoint());
                strokes.add(current);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (current == null) {
                    return;
                }
                current.points.add(e.getPoint());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                current = null;
            }
        };
        addMouseListener(painting);
        addMouseMotionListener(painting);
    }

    public boolean hasInk() {
        return !strokes.isEmpty();
    }

    public void setBrushColor(Color color) {
        if (color != null) {
            eraser = false;
            brushColor = color;
        }
    }

    public void setBrushSizeIndex(int index) {
        if (index >= 0 && index < BRUSH_SIZES.length) {
            brushSizeIndex = index;
        }
    }

    public void setEraser(boolean on) {
        eraser = on;
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            repaint();
        }
    }

    public void clearCanvas() {
        strokes.clear();
        repaint();
    }

    public BufferedImage renderImage() {
        BufferedImage out = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, CANVAS_W, CANVAS_H);
            paintStrokes(g);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** 離屏預覽用：含 Win98 凹陷邊框。 */
    public BufferedImage renderFramedPreview() {
        int pad = 4;
        BufferedImage out = new BufferedImage(CANVAS_W + pad * 2, CANVAS_H + pad * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(ClassicBevel.FACE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.translate(pad, pad);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, CANVAS_W, CANVAS_H);
            ClassicBevel.paintSunkenEdges(g, -2, -2, CANVAS_W + 4, CANVAS_H + 4);
            paintStrokes(g);
        } finally {
            g.dispose();
        }
        return out;
    }

    private float currentBrushWidth() {
        return BRUSH_SIZES[brushSizeIndex];
    }

    private void paintStrokes(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Stroke stroke : strokes) {
            if (stroke.points.isEmpty()) {
                continue;
            }
            g.setColor(stroke.color);
            g.setStroke(new BasicStroke(stroke.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (stroke.points.size() == 1) {
                Point p = stroke.points.get(0);
                g.fillOval(p.x - 1, p.y - 1, 3, 3);
                continue;
            }
            for (int i = 1; i < stroke.points.size(); i++) {
                Point a = stroke.points.get(i - 1);
                Point b = stroke.points.get(i);
                g.drawLine(a.x, a.y, b.x, b.y);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            paintStrokes(g);
        } finally {
            g.dispose();
        }
    }
}