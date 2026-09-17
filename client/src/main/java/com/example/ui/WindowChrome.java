package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * 自訂標題列與邊緣縮放，讓桌面端可以調整視窗透明度。
 *
 * <p>Java {@code Window#setOpacity} 不能用在有系統標題列的視窗上，因此改由 Swing 自繪標題列。</p>
 */
public final class WindowChrome {

    private static final Color BAR_BG = new Color(241, 245, 249);
    private static final Color BAR_BORDER = new Color(203, 213, 225);
    private static final Color TITLE_FG = new Color(30, 41, 59);
    private static final Color MUTED_FG = new Color(100, 116, 139);
    private static final Color CLOSE_HOVER = new Color(239, 68, 68);
    private static final Color BUTTON_HOVER = new Color(226, 232, 240);
    private static final int GRIP = 6;

    private WindowChrome() {
    }

    /** 標題列上的透明度控制，供主視窗綁定設定持久化。 */
    public static final class Controls {
        public final JSlider transparencySlider;
        public final JLabel transparencyValueLabel;

        Controls(JSlider transparencySlider, JLabel transparencyValueLabel) {
            this.transparencySlider = transparencySlider;
            this.transparencyValueLabel = transparencyValueLabel;
        }
    }

    /**
     * 將 {@code frame} 設為無系統標題列，並在北側放入自訂標題列。
     * 必須在視窗可顯示（displayable）之前呼叫。
     */
    public static Controls install(JFrame frame, String title, Image icon, Font titleFont, Font controlFont) {
        if (frame.isDisplayable()) {
            throw new IllegalStateException("WindowChrome must be installed before the frame is displayable");
        }
        frame.setUndecorated(true);

        JSlider slider = new JSlider(WindowOpacity.MIN_TRANSPARENCY_PERCENT, WindowOpacity.MAX_TRANSPARENCY_PERCENT, 0);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setMajorTickSpacing(20);
        slider.setPreferredSize(new Dimension(140, 22));
        slider.setMinimumSize(new Dimension(100, 22));
        slider.setToolTipText("向右越透明，方便同時看到後面的畫面。最透明仍保留 40% 不透明度，以免點不到。");

        JLabel valueLabel = new JLabel(WindowOpacity.formatPercentLabel(0));
        valueLabel.setFont(controlFont);
        valueLabel.setForeground(MUTED_FG);
        valueLabel.setPreferredSize(new Dimension(56, 22));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel titleBar = createTitleBar(frame, title, icon, titleFont, controlFont, slider, valueLabel);
        ResizeSupport resize = new ResizeSupport(frame);

        WallpaperPanel outer = new WallpaperPanel();
        outer.setBackground(BAR_BG);
        applyWindowBorder(outer, false);
        outer.add(titleBar, BorderLayout.NORTH);
        resize.attach(outer);

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 6, 6, 6));
        outer.add(body, BorderLayout.CENTER);

        frame.setContentPane(outer);
        frame.addWindowStateListener(e -> {
            boolean maximized = isMaximized(frame);
            applyWindowBorder(outer, maximized);
            resize.setEnabled(!maximized);
            outer.revalidate();
        });

        frame.getRootPane().putClientProperty("punchclock.body", body);
        frame.getRootPane().putClientProperty("punchclock.outer", outer);
        return new Controls(slider, valueLabel);
    }

    /** 自訂 chrome 安裝後，把原本的主內容放到標題列下方。 */
    public static JPanel bodyOf(JFrame frame) {
        Object inner = frame.getRootPane().getClientProperty("punchclock.body");
        if (inner instanceof JPanel) {
            return (JPanel) inner;
        }
        throw new IllegalStateException("WindowChrome.install must run first");
    }

    /** 設定／清除視窗背景圖（cover 縮放）；{@code null} 還原預設底色。 */
    public static void setWallpaper(JFrame frame, Image wallpaper) {
        setWallpaper(frame, wallpaper, 0, com.example.service.WindowBackground.DEFAULT_OPACITY_PERCENT);
    }

    public static void setWallpaper(JFrame frame, Image wallpaper, int blurPercent, int opacityPercent) {
        if (frame == null) {
            return;
        }
        Object outer = frame.getRootPane().getClientProperty("punchclock.outer");
        if (outer instanceof WallpaperPanel) {
            ((WallpaperPanel) outer).setWallpaper(wallpaper, blurPercent, opacityPercent);
        }
    }

    public static void setWallpaperEffects(JFrame frame, int blurPercent, int opacityPercent) {
        if (frame == null) {
            return;
        }
        Object outer = frame.getRootPane().getClientProperty("punchclock.outer");
        if (outer instanceof WallpaperPanel) {
            ((WallpaperPanel) outer).setEffects(blurPercent, opacityPercent);
        }
    }

    private static final class WallpaperPanel extends JPanel {
        private Image source;
        private Image painted;
        private int blurPercent;
        private int opacityPercent = com.example.service.WindowBackground.DEFAULT_OPACITY_PERCENT;

        WallpaperPanel() {
            super(new BorderLayout());
            setOpaque(true);
        }

        void setWallpaper(Image image, int blurPercent, int opacityPercent) {
            this.source = image;
            this.blurPercent = com.example.service.WindowBackground.clampBlurPercent(blurPercent);
            this.opacityPercent = com.example.service.WindowBackground.clampOpacityPercent(opacityPercent);
            rebuildPainted();
            setOpaque(image == null);
            if (image == null) {
                setBackground(BAR_BG);
            }
            revalidate();
            repaint();
        }

        void setEffects(int blurPercent, int opacityPercent) {
            int nextBlur = com.example.service.WindowBackground.clampBlurPercent(blurPercent);
            int nextOpacity = com.example.service.WindowBackground.clampOpacityPercent(opacityPercent);
            boolean blurChanged = nextBlur != this.blurPercent;
            this.blurPercent = nextBlur;
            this.opacityPercent = nextOpacity;
            if (blurChanged) {
                rebuildPainted();
            }
            repaint();
        }

        private void rebuildPainted() {
            if (source == null) {
                painted = null;
                return;
            }
            BufferedImage base;
            if (source instanceof BufferedImage) {
                base = (BufferedImage) source;
            } else {
                int w = Math.max(1, source.getWidth(null));
                int h = Math.max(1, source.getHeight(null));
                base = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = base.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.drawImage(source, 0, 0, null);
                g.dispose();
            }
            painted = com.example.service.WindowBackground.applyEffects(base, blurPercent);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BAR_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (painted != null) {
                float alpha = opacityPercent / 100f;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                com.example.service.WindowBackground.paintCover(g2, painted, getWidth(), getHeight());
            }
            g2.dispose();
        }
    }

    static void applyWindowBorder(JComponent outer, boolean maximized) {
        if (maximized) {
            outer.setBorder(new LineBorder(BAR_BORDER, 1));
        } else {
            outer.setBorder(new CompoundBorder(
                    new LineBorder(BAR_BORDER, 1),
                    new EmptyBorder(GRIP - 1, GRIP - 1, GRIP - 1, GRIP - 1)));
        }
    }

    private static JPanel createTitleBar(
            JFrame frame,
            String title,
            Image icon,
            Font titleFont,
            Font controlFont,
            JSlider slider,
            JLabel valueLabel) {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(BAR_BG);
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BAR_BORDER),
                new EmptyBorder(4, 8, 4, 4)));
        bar.setPreferredSize(new Dimension(720, 36));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        if (icon != null) {
            JLabel iconLabel = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.drawImage(icon, 0, 2, 18, 18, this);
                }
            };
            iconLabel.setPreferredSize(new Dimension(18, 22));
            left.add(iconLabel);
        }
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(TITLE_FG);
        left.add(titleLabel);

        JLabel transparencyLabel = new JLabel("透明度");
        transparencyLabel.setFont(controlFont);
        transparencyLabel.setForeground(MUTED_FG);
        transparencyLabel.setToolTipText(slider.getToolTipText());

        JPanel center = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        center.setOpaque(false);
        center.add(transparencyLabel);
        center.add(slider);
        center.add(valueLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        JButton min = chromeButton("—", "最小化", controlFont, false);
        JButton max = chromeButton("□", "最大化", controlFont, false);
        JButton close = chromeButton("×", "關閉", controlFont, true);
        min.addActionListener(e -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
        max.addActionListener(e -> toggleMaximize(frame));
        close.addActionListener(e -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        buttons.add(min);
        buttons.add(max);
        buttons.add(close);

        bar.add(left, BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(buttons, BorderLayout.EAST);

        DragSupport drag = new DragSupport(frame);
        MouseAdapter dragAdapter = drag.adapter();
        bar.addMouseListener(dragAdapter);
        bar.addMouseMotionListener(dragAdapter);
        titleLabel.addMouseListener(dragAdapter);
        titleLabel.addMouseMotionListener(dragAdapter);
        left.addMouseListener(dragAdapter);
        left.addMouseMotionListener(dragAdapter);

        return bar;
    }

    private static JButton chromeButton(String text, String tooltip, Font font, boolean close) {
        JButton button = new JButton(text);
        button.setFont(font);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBackground(BAR_BG);
        button.setForeground(TITLE_FG);
        button.setPreferredSize(new Dimension(40, 26));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (close) {
                    button.setBackground(CLOSE_HOVER);
                    button.setForeground(Color.WHITE);
                } else {
                    button.setBackground(BUTTON_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(BAR_BG);
                button.setForeground(TITLE_FG);
            }
        });
        return button;
    }

    static void toggleMaximize(JFrame frame) {
        if (isMaximized(frame)) {
            frame.setExtendedState(Frame.NORMAL);
        } else {
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    static boolean isMaximized(JFrame frame) {
        return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    private static final class DragSupport {
        private final JFrame frame;
        private Point pressOnScreen;
        private Rectangle startBounds;
        private boolean dragging;

        DragSupport(JFrame frame) {
            this.frame = frame;
        }

        MouseAdapter adapter() {
            return new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    if (e.getClickCount() == 2) {
                        toggleMaximize(frame);
                        dragging = false;
                        return;
                    }
                    pressOnScreen = e.getLocationOnScreen();
                    startBounds = frame.getBounds();
                    dragging = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging || pressOnScreen == null || startBounds == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    int dx = now.x - pressOnScreen.x;
                    int dy = now.y - pressOnScreen.y;
                    if (isMaximized(frame)) {
                        if (Math.abs(dx) < 4 && Math.abs(dy) < 4) {
                            return;
                        }
                        frame.setExtendedState(Frame.NORMAL);
                        Rectangle restored = frame.getBounds();
                        int x = now.x - restored.width / 2;
                        frame.setLocation(Math.max(0, x), now.y - 12);
                        pressOnScreen = now;
                        startBounds = frame.getBounds();
                        return;
                    }
                    frame.setLocation(startBounds.x + dx, startBounds.y + dy);
                }
            };
        }
    }

    private static final class ResizeSupport {
        private static final int N = 1, S = 2, W = 4, E = 8;
        private final JFrame frame;
        private boolean enabled = true;
        private int dir;
        private Point pressOnScreen;
        private Rectangle startBounds;

        ResizeSupport(JFrame frame) {
            this.frame = frame;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
            if (!enabled) {
                dir = 0;
                frame.setCursor(Cursor.getDefaultCursor());
            }
        }

        void attach(JComponent outer) {
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int next = enabled ? directionAt(outer, e) : 0;
                    outer.setCursor(cursorFor(next));
                    frame.setCursor(cursorFor(next));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (dir == 0) {
                        outer.setCursor(Cursor.getDefaultCursor());
                        frame.setCursor(Cursor.getDefaultCursor());
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!enabled || e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    dir = directionAt(outer, e);
                    if (dir == 0) {
                        return;
                    }
                    pressOnScreen = e.getLocationOnScreen();
                    startBounds = frame.getBounds();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dir = 0;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!enabled || dir == 0 || pressOnScreen == null || startBounds == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    int dx = now.x - pressOnScreen.x;
                    int dy = now.y - pressOnScreen.y;
                    Rectangle next = new Rectangle(startBounds);
                    if ((dir & N) != 0) {
                        next.y += dy;
                        next.height -= dy;
                    }
                    if ((dir & S) != 0) {
                        next.height += dy;
                    }
                    if ((dir & W) != 0) {
                        next.x += dx;
                        next.width -= dx;
                    }
                    if ((dir & E) != 0) {
                        next.width += dx;
                    }
                    Dimension min = frame.getMinimumSize();
                    if (next.width < min.width) {
                        if ((dir & W) != 0) {
                            next.x = startBounds.x + startBounds.width - min.width;
                        }
                        next.width = min.width;
                    }
                    if (next.height < min.height) {
                        if ((dir & N) != 0) {
                            next.y = startBounds.y + startBounds.height - min.height;
                        }
                        next.height = min.height;
                    }
                    frame.setBounds(next);
                }
            };
            outer.addMouseListener(adapter);
            outer.addMouseMotionListener(adapter);
        }

        private static int directionAt(JComponent outer, MouseEvent e) {
            Insets insets = outer.getInsets();
            int x = e.getX();
            int y = e.getY();
            int w = outer.getWidth();
            int h = outer.getHeight();
            int gripX = Math.max(insets.left, GRIP);
            int gripY = Math.max(insets.top, GRIP);
            int dir = 0;
            if (y < gripY) {
                dir |= N;
            } else if (y >= h - Math.max(insets.bottom, GRIP)) {
                dir |= S;
            }
            if (x < gripX) {
                dir |= W;
            } else if (x >= w - Math.max(insets.right, GRIP)) {
                dir |= E;
            }
            return dir;
        }

        private static Cursor cursorFor(int dir) {
            switch (dir) {
                case N:
                    return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case S:
                    return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case W:
                    return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case E:
                    return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case N | W:
                    return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case N | E:
                    return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case S | W:
                    return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                case S | E:
                    return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                default:
                    return Cursor.getDefaultCursor();
            }
        }
    }
}
