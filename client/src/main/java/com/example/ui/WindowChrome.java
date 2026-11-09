package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.LayoutManager;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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

    private static final Color BAR_BG = new Color(42, 32, 72);
    private static final Color BAR_BORDER = new Color(98, 78, 140);
    private static final Color TITLE_FG = new Color(255, 250, 255);
    private static final Color MUTED_FG = new Color(214, 204, 232);
    private static final Color CLOSE_HOVER = new Color(239, 68, 68);
    private static final Color BUTTON_HOVER = new Color(82, 64, 124);
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
        MacFrames.install();
        frame.setUndecorated(true);

        JSlider slider = new JSlider(WindowOpacity.MIN_TRANSPARENCY_PERCENT, WindowOpacity.MAX_TRANSPARENCY_PERCENT, 0);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setMajorTickSpacing(20);
        // 不用系統 Aqua 滑桿：它的圓鈕會畫出元件底邊，被標題列裁成只剩一點。
        slider.setUI(new TitleBarSliderUI(slider));
        slider.setPreferredSize(new Dimension(140, TitleBarSliderUI.HEIGHT));
        slider.setMinimumSize(new Dimension(100, TitleBarSliderUI.HEIGHT));
        slider.setToolTipText("向右越透明，方便同時看到後面的畫面。最透明仍保留 40% 不透明度，以免點不到。");

        JLabel valueLabel = new JLabel(WindowOpacity.formatPercentLabel(0));
        valueLabel.setFont(controlFont);
        valueLabel.setForeground(MUTED_FG);
        valueLabel.setPreferredSize(new Dimension(56, 22));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel titleBar = createTitleBar(frame, title, icon, titleFont, controlFont, slider, valueLabel);
        titleBar.putClientProperty("punchclock.titleBar", Boolean.TRUE);
        ResizeSupport resize = new ResizeSupport(frame);

        WallpaperPanel stage = new WallpaperPanel();
        stage.setBackground(BAR_BG);
        applyWindowBorder(stage, false);
        stage.add(titleBar, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 6, 6, 6));
        stage.add(body, BorderLayout.CENTER);

        FrameCanvas canvas = new FrameCanvas(frame, stage);
        canvas.add(stage);
        resize.attach(canvas);
        resize.attach(stage);
        if (perPixelSupported()) {
            frame.setBackground(new Color(0, 0, 0, 0));
        }
        frame.setContentPane(canvas);
        JComponent root = frame.getRootPane();
        root.setOpaque(false);
        root.setBackground(new Color(0, 0, 0, 0));
        if (frame.getLayeredPane() instanceof JComponent) {
            JComponent layered = (JComponent) frame.getLayeredPane();
            layered.setOpaque(false);
            layered.setBackground(new Color(0, 0, 0, 0));
        }
        PortraitGlass glass = new PortraitGlass(canvas);
        frame.setGlassPane(glass);
        glass.setVisible(true);
        frame.addWindowStateListener(e -> {
            boolean maximized = isMaximized(frame);
            applyWindowBorder(stage, maximized);
            resize.setEnabled(!maximized);
            if (!maximized && (e.getOldState() & Frame.MAXIMIZED_BOTH) != 0) {
                Rectangle restore = restoreWindow(frame);
                if (restore != null) {
                    frame.setBounds(restore);
                }
            }
            canvas.revalidate();
            canvas.repaint();
        });

        root.putClientProperty("punchclock.body", body);
        root.putClientProperty("punchclock.outer", stage);
        root.putClientProperty("punchclock.canvas", canvas);
        return new Controls(slider, valueLabel);
    }

    /**
     * 首頁角色。襪腳畫在紫色面板後面，上半身畫在前面。
     * 還原視窗時在面板上方與右側留白，最大化時由實作把角色收進面板。
     */
    public interface FramePortrait {
        Insets overflow(int width, int height, boolean maximized);

        void paintBehind(Graphics2D g, JComponent canvas, Rectangle panel);

        void paintInFront(Graphics2D g, JComponent canvas, Rectangle panel);

        boolean opaqueAt(JComponent canvas, int x, int y);

        void pressed();

        /**
         * 由紫色面板尺寸得到留白。回傳 null 時沿用毛衣女孩的既有公式。
         */
        default Insets panelOverflow(int panelWidth, int panelHeight) {
            return null;
        }
    }

    public static void setFramePortrait(JFrame frame, FramePortrait portrait) {
        FrameCanvas canvas = canvasOf(frame);
        if (canvas == null) {
            return;
        }
        canvas.setPortrait(portrait);
        canvas.revalidate();
        canvas.repaint();
    }

    /**
     * 角色留白不跟著換頁收掉，避免透明視窗改大小時先閃一幀空白。
     * 仍以紫色面板左上角的螢幕座標為錨重算外層視窗，面板才不會自己挪位。
     */
    public static void relayout(JFrame frame) {
        FrameCanvas canvas = canvasOf(frame);
        if (canvas == null) {
            return;
        }
        if (!frame.isShowing() || isMaximized(frame)) {
            if (isMaximized(frame)) {
                rememberPageChangeWhileMaximized(frame);
            }
            canvas.revalidate();
            canvas.repaint();
            return;
        }
        Rectangle panel = panelOnScreen(canvas);
        if (panel == null || panel.width < 80 || panel.height < 80) {
            canvas.revalidate();
            canvas.repaint();
            return;
        }
        Insets overflow = overflowAround(canvas, panel.width, panel.height);
        Rectangle next = keepTitleVisible(
                windowForPanel(panel, overflow, frame.getInsets()),
                overflow,
                frame.getInsets(),
                titleBarHeight(canvas),
                workArea(frame));
        Dimension original = rememberedMinimum(frame);
        frame.setMinimumSize(new Dimension(
                Math.min(original.width, next.width),
                Math.min(original.height, next.height)));
        if (!next.equals(frame.getBounds())) {
            frame.setBounds(next);
        }
        if (frame.getWidth() >= original.width && frame.getHeight() >= original.height) {
            frame.setMinimumSize(original);
        }
        rememberRestored(frame, panel, frame.getBounds());
        canvas.revalidate();
        canvas.repaint();
    }

    public static Rectangle stageBounds(JFrame frame) {
        FrameCanvas canvas = canvasOf(frame);
        if (canvas == null) {
            return new Rectangle();
        }
        return canvas.stageBounds();
    }

    public static boolean isMaximizedFrame(JComponent canvas) {
        if (canvas instanceof FrameCanvas) {
            return isMaximized(((FrameCanvas) canvas).frame);
        }
        return false;
    }

    public static Rectangle chromeControls(JComponent canvas) {
        JComponent buttons = findMarked(canvas, "punchclock.chromeControls");
        if (buttons == null || buttons.getParent() == null) {
            return new Rectangle();
        }
        return SwingUtilities.convertRectangle(buttons.getParent(), buttons.getBounds(), canvas);
    }

    public static int titleBarHeight(JComponent canvas) {
        JComponent bar = findMarked(canvas, "punchclock.titleBar");
        if (bar == null) {
            return TitleBarSliderUI.HEIGHT + 9;
        }
        return Math.max(1, bar.getHeight());
    }

    private static FrameCanvas canvasOf(JFrame frame) {
        if (frame == null) {
            return null;
        }
        Object canvas = frame.getRootPane().getClientProperty("punchclock.canvas");
        if (canvas instanceof FrameCanvas) {
            return (FrameCanvas) canvas;
        }
        return null;
    }

    private static JComponent findMarked(Component root, String key) {
        if (!(root instanceof JComponent)) {
            return null;
        }
        JComponent component = (JComponent) root;
        if (Boolean.TRUE.equals(component.getClientProperty(key))) {
            return component;
        }
        for (Component child : component.getComponents()) {
            JComponent found = findMarked(child, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean perPixelSupported() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (RuntimeException ex) {
            return false;
        }
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
        JPanel bar = new JPanel();
        bar.setBackground(BAR_BG);
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BAR_BORDER),
                new EmptyBorder(4, 8, 4, 4)));
        int barHeight = TitleBarSliderUI.HEIGHT + 4 + 4 + 1;
        bar.setPreferredSize(new Dimension(720, barHeight));

        JComponent iconLabel = null;
        if (icon != null) {
            iconLabel = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.drawImage(icon, 0, 0, getWidth(), getHeight(), this);
                }
            };
            iconLabel.setPreferredSize(new Dimension(18, 18));
        }
        TitleName titleLabel = new TitleName(title, titleFont);

        JLabel transparencyLabel = new JLabel("透明度");
        transparencyLabel.setFont(controlFont);
        transparencyLabel.setForeground(MUTED_FG);
        transparencyLabel.setToolTipText(slider.getToolTipText());

        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        center.setOpaque(false);
        center.add(transparencyLabel);
        center.add(slider);
        center.add(valueLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.putClientProperty("punchclock.chromeControls", Boolean.TRUE);
        // Mac Aqua L&F 會把按鈕文字擠成「...」；改成自繪圖示，不依賴字元。
        JButton min = chromeButton(ChromeGlyph.MINIMIZE, "最小化", false);
        JButton max = chromeButton(ChromeGlyph.MAXIMIZE, "最大化", false);
        JButton close = chromeButton(ChromeGlyph.CLOSE, "關閉", true);
        min.addActionListener(e -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
        max.addActionListener(e -> toggleMaximize(frame));
        close.addActionListener(e -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        buttons.add(min);
        buttons.add(max);
        buttons.add(close);

        bar.setLayout(new TitleBarLayout(iconLabel, titleLabel, center, buttons, slider));
        if (iconLabel != null) {
            bar.add(iconLabel);
        }
        bar.add(titleLabel);
        bar.add(center);
        bar.add(buttons);

        DragSupport drag = new DragSupport(frame);
        MouseAdapter dragAdapter = drag.adapter();
        bar.addMouseListener(dragAdapter);
        bar.addMouseMotionListener(dragAdapter);
        titleLabel.addMouseListener(dragAdapter);
        titleLabel.addMouseMotionListener(dragAdapter);
        if (iconLabel != null) {
            iconLabel.addMouseListener(dragAdapter);
            iconLabel.addMouseMotionListener(dragAdapter);
        }

        return bar;
    }

    /** 主標題保留到版號；commit 與提交時間在放不下時改走 tooltip。 */
    static String mainTitle(String full) {
        String text = full == null ? "" : full.trim();
        int end = text.length();
        int dot = text.indexOf(" · ");
        int paren = text.indexOf('（');
        if (dot >= 0) {
            end = Math.min(end, dot);
        }
        if (paren >= 0) {
            end = Math.min(end, paren);
        }
        return text.substring(0, end).trim().replaceAll(" +", " ");
    }

    /**
     * 圖示和標題同一列，依字型行高垂直置中，不換行、不被父層裁掉。
     */
    private static final class TitleName extends JComponent {
        private final String full;
        private final String main;
        private String shown;

        TitleName(String fullTitle, Font font) {
            full = fullTitle == null ? "" : fullTitle.trim();
            main = mainTitle(full);
            shown = main;
            setFont(font);
            setForeground(TITLE_FG);
            setOpaque(false);
            setName("punchclock.titleName");
            setToolTipText(full.equals(main) ? null : full);
        }

        boolean fit(FontMetrics metrics, int available) {
            int fullW = full.isEmpty() ? 0 : metrics.stringWidth(full);
            String next = fullW <= available && fullW > 0 ? full : main;
            if (next.equals(shown)) {
                return false;
            }
            shown = next;
            setToolTipText(shown.equals(full) ? null : full);
            return true;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (shown.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setFont(getFont());
                g2.setColor(getForeground());
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics metrics = g2.getFontMetrics();
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(shown, 0, baseline);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 標題在左、視窗按鈕在右。透明度滑桿放在中段、角色左側。
     * 縮窄時先收起 commit，主標題、滑桿和按鈕維持完整可見。
     */
    private static final class TitleBarLayout implements LayoutManager {
        private final JComponent icon;
        private final TitleName title;
        private final JComponent sliderGroup;
        private final JComponent buttons;
        private final JSlider slider;

        TitleBarLayout(JComponent icon, TitleName title, JComponent sliderGroup, JComponent buttons, JSlider slider) {
            this.icon = icon;
            this.title = title;
            this.sliderGroup = sliderGroup;
            this.buttons = buttons;
            this.slider = slider;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(720, TitleBarSliderUI.HEIGHT + 13);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(360, TitleBarSliderUI.HEIGHT + 13);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int x0 = insets.left;
            int y0 = insets.top;
            int innerW = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int innerH = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            int buttonW = buttons.getPreferredSize().width;
            buttons.setBounds(x0 + innerW - buttonW, y0, buttonW, innerH);

            FontMetrics metrics = title.getFontMetrics(title.getFont());
            int mainW = title.main.isEmpty() ? 0 : metrics.stringWidth(title.main);
            int iconW = icon == null ? 0 : 18;
            int textGap = iconW == 0 ? 0 : 8;
            int reserved = iconW + textGap + mainW;

            int labelW = 0;
            int valueW = 0;
            for (Component child : sliderGroup.getComponents()) {
                if (child == slider) {
                    continue;
                }
                Dimension pref = child.getPreferredSize();
                if (labelW == 0) {
                    labelW = pref.width;
                } else {
                    valueW = pref.width;
                }
            }
            int gaps = 6 * 4;
            int rightLimit = x0 + innerW - buttonW - 8;
            int leftLimit = x0 + reserved + 8;
            int room = rightLimit - leftLimit - labelW - valueW - gaps;
            int track = Math.max(72, Math.min(140, room));
            if (slider.getPreferredSize().width != track) {
                slider.setPreferredSize(new Dimension(track, TitleBarSliderUI.HEIGHT));
            }
            int groupW = labelW + track + valueW + gaps;
            int characterLimit = x0 + Math.round(innerW * 0.56f);
            int left = x0 + Math.max(0, (innerW - groupW) / 2);
            if (left < leftLimit) {
                left = leftLimit;
            }
            if (characterLimit - groupW >= leftLimit && left + groupW > characterLimit) {
                left = characterLimit - groupW;
            }
            if (left + groupW > rightLimit) {
                left = Math.max(leftLimit, rightLimit - groupW);
            }
            int width = Math.max(40, Math.min(groupW, rightLimit - left));
            sliderGroup.setBounds(left, y0, width, innerH);

            int textRoom = Math.max(0, left - 8 - (x0 + iconW + textGap));
            title.fit(metrics, Math.max(textRoom, mainW));
            int shownW = title.shown.isEmpty() ? 0 : metrics.stringWidth(title.shown);
            int textW = title.shown.equals(title.main) || textRoom >= shownW ? shownW : Math.min(shownW, textRoom);
            if (icon != null) {
                int iconY = y0 + Math.max(0, (innerH - 18) / 2);
                icon.setBounds(x0, iconY, iconW, 18);
            }
            title.setBounds(x0 + iconW + textGap, y0, textW, innerH);
        }
    }

    private enum ChromeGlyph {
        MINIMIZE,
        MAXIMIZE,
        CLOSE
    }

    private static JButton chromeButton(ChromeGlyph glyph, String tooltip, boolean close) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(getForeground());
                    g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    switch (glyph) {
                        case MINIMIZE:
                            g2.drawLine(cx - 5, cy, cx + 5, cy);
                            break;
                        case MAXIMIZE:
                            g2.drawRect(cx - 5, cy - 5, 10, 10);
                            break;
                        case CLOSE:
                            g2.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                            g2.drawLine(cx + 4, cy - 4, cx - 4, cy + 4);
                            break;
                        default:
                            break;
                    }
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(40, 26);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        button.setText("");
        button.setIcon(null);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setBackground(BAR_BG);
        button.setForeground(TITLE_FG);
        button.setBorder(BorderFactory.createEmptyBorder());
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
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(BAR_BG);
                button.setForeground(TITLE_FG);
                button.repaint();
            }
        });
        return button;
    }

    static void toggleMaximize(JFrame frame) {
        if (isMaximized(frame)) {
            frame.setExtendedState(Frame.NORMAL);
        } else {
            FrameCanvas canvas = canvasOf(frame);
            Rectangle panel = panelOnScreen(canvas);
            if (panel != null) {
                rememberRestored(frame, panel, frame.getBounds());
            }
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    /**
     * 角色留白。軌道版若自訂了面板留白就用那一組，否則沿用毛衣女孩公式。
     * 沒有跨框時留白是 0，視窗縮回紫色面板。
     */
    static Insets overflowAround(FrameCanvas canvas, int panelWidth, int panelHeight) {
        if (canvas == null || !wantsHomeOverflow(canvas)) {
            return new Insets(0, 0, 0, 0);
        }
        Insets custom = canvas.portrait.panelOverflow(panelWidth, panelHeight);
        if (custom != null) {
            return custom;
        }
        return overflowForPanel(panelWidth, panelHeight, true);
    }

    /**
     * 由面板大小反推留白，使 {@link com.example.ui.hud.HomeCharacterLayout#overflow}
     * 用算出來的外層尺寸會得到同一組留白。
     */
    static Insets overflowForPanel(int panelWidth, int panelHeight, boolean home) {
        if (!home || panelWidth < 80 || panelHeight < 80) {
            return new Insets(0, 0, 0, 0);
        }
        int top = clampedTop(panelHeight);
        int right = clampedRight(panelWidth);
        Insets fitted = com.example.ui.hud.HomeCharacterLayout.overflow(
                panelWidth + right, panelHeight + top, false, true);
        if (fitted.top != top || fitted.right != right) {
            top = fitted.top;
            right = fitted.right;
            fitted = com.example.ui.hud.HomeCharacterLayout.overflow(
                    panelWidth + right, panelHeight + top, false, true);
        }
        return fitted;
    }

    private static int clampedTop(int panelHeight) {
        for (int top = 96; top <= 168; top++) {
            if ((panelHeight + top) * 18 / 100 == top) {
                return top;
            }
        }
        return (panelHeight + 168) * 18 / 100 >= 168 ? 168 : 96;
    }

    private static int clampedRight(int panelWidth) {
        float scale = com.example.ui.hud.HomeCharacterLayout.SOCK_VISIBLE
                / (1f + com.example.ui.hud.HomeCharacterLayout.SOCK_VISIBLE);
        for (int right = 48; right <= 110; right++) {
            if (Math.round((panelWidth + right) * scale) == right) {
                return right;
            }
        }
        int raw = Math.round((panelWidth + 110) * scale);
        return raw >= 110 ? 110 : 48;
    }

    static Rectangle windowForPanel(Rectangle panel, Insets overflow, Insets frameInsets) {
        Insets edge = frameInsets == null ? new Insets(0, 0, 0, 0) : frameInsets;
        Insets margin = overflow == null ? new Insets(0, 0, 0, 0) : overflow;
        return new Rectangle(
                panel.x - margin.left - edge.left,
                panel.y - margin.top - edge.top,
                panel.width + margin.left + margin.right + edge.left + edge.right,
                panel.height + margin.top + margin.bottom + edge.top + edge.bottom);
    }

    /** 只修正到標題列和控制鈕進入工作區，頭可以留在選單列外面。 */
    static Rectangle keepTitleVisible(
            Rectangle window, Insets overflow, Insets frameInsets, int titleBarHeight, Rectangle work) {
        Rectangle next = new Rectangle(window);
        Insets edge = frameInsets == null ? new Insets(0, 0, 0, 0) : frameInsets;
        Insets margin = overflow == null ? new Insets(0, 0, 0, 0) : overflow;
        int panelW = next.width - edge.left - edge.right - margin.left - margin.right;
        int titleLeft = next.x + edge.left + margin.left;
        int titleRight = titleLeft + panelW;
        if (panelW <= work.width) {
            if (titleRight > work.x + work.width) {
                next.x -= titleRight - (work.x + work.width);
            }
            titleLeft = next.x + edge.left + margin.left;
            if (titleLeft < work.x) {
                next.x += work.x - titleLeft;
            }
        } else if (titleLeft < work.x) {
            next.x += work.x - titleLeft;
        }
        int titleTop = next.y + edge.top + margin.top;
        int titleBottom = titleTop + Math.max(1, titleBarHeight);
        if (titleBottom > work.y + work.height) {
            next.y -= titleBottom - (work.y + work.height);
        }
        titleTop = next.y + edge.top + margin.top;
        if (titleTop < work.y) {
            next.y += work.y - titleTop;
        }
        return next;
    }

    static Rectangle workArea(JFrame frame) {
        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                screen.x + insets.left,
                screen.y + insets.top,
                Math.max(1, screen.width - insets.left - insets.right),
                Math.max(1, screen.height - insets.top - insets.bottom));
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
                        frame.setLocation(Math.max(0, x),
                                placeY(frame, now.y - 12, restored.width, restored.height));
                        pressOnScreen = now;
                        startBounds = frame.getBounds();
                        return;
                    }
                    frame.setLocation(startBounds.x + dx,
                            placeY(frame, startBounds.y + dy, startBounds.width, startBounds.height));
                }
            };
        }
    }

    /**
     * 頭可以移出螢幕上緣，紫色面板的上緣停在選單列。
     */
    private static int placeY(JFrame frame, int requestedY, int width, int height) {
        return MacFrames.lowestY(screenTop(frame), overflowTop(frame, width, height), requestedY);
    }

    private static int screenTop(JFrame frame) {
        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return gc.getBounds().y + insets.top;
    }

    private static int overflowTop(JFrame frame, int width, int height) {
        FrameCanvas canvas = canvasOf(frame);
        if (canvas == null || canvas.portrait == null || isMaximized(frame)) {
            return 0;
        }
        return Math.max(0, canvas.portrait.overflow(width, height, false).top);
    }

    private static boolean wantsHomeOverflow(FrameCanvas canvas) {
        return canvas.portrait != null && canvas.portrait.overflow(800, 800, false).top > 0;
    }

    private static Rectangle panelOnScreen(FrameCanvas canvas) {
        if (canvas == null || !canvas.stage.isShowing() || canvas.stage.getWidth() <= 0) {
            return null;
        }
        try {
            return new Rectangle(canvas.stage.getLocationOnScreen(), canvas.stage.getSize());
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    private static Dimension rememberedMinimum(JFrame frame) {
        Object saved = frame.getRootPane().getClientProperty("punchclock.minimum");
        if (saved instanceof Dimension) {
            return new Dimension((Dimension) saved);
        }
        Dimension current = new Dimension(frame.getMinimumSize());
        frame.getRootPane().putClientProperty("punchclock.minimum", current);
        return new Dimension(current);
    }

    private static void rememberRestored(JFrame frame, Rectangle panel, Rectangle window) {
        if (frame.getRootPane() == null || panel == null || window == null) {
            return;
        }
        frame.getRootPane().putClientProperty("punchclock.restorePanel", new Rectangle(panel));
        frame.getRootPane().putClientProperty("punchclock.restoreWindow", new Rectangle(window));
    }

    private static Rectangle restoreWindow(JFrame frame) {
        if (frame.getRootPane() == null) {
            return null;
        }
        Object value = frame.getRootPane().getClientProperty("punchclock.restoreWindow");
        return value instanceof Rectangle ? new Rectangle((Rectangle) value) : null;
    }

    /** 最大化時不能搬動外層視窗，只更新還原後要用的那一組 bounds。 */
    private static void rememberPageChangeWhileMaximized(JFrame frame) {
        if (frame.getRootPane() == null) {
            return;
        }
        Object saved = frame.getRootPane().getClientProperty("punchclock.restorePanel");
        if (!(saved instanceof Rectangle)) {
            return;
        }
        Rectangle panel = (Rectangle) saved;
        FrameCanvas canvas = canvasOf(frame);
        Insets overflow = overflowAround(canvas, panel.width, panel.height);
        int titleH = canvas == null ? TitleBarSliderUI.HEIGHT + 9 : titleBarHeight(canvas);
        Rectangle next = keepTitleVisible(
                windowForPanel(panel, overflow, frame.getInsets()),
                overflow,
                frame.getInsets(),
                titleH,
                workArea(frame));
        frame.getRootPane().putClientProperty("punchclock.restoreWindow", next);
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
                    if ((dir & N) != 0) {
                        int y = placeY(frame, next.y, next.width, next.height);
                        if (y != next.y) {
                            next.height -= y - next.y;
                            next.y = y;
                        }
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

    /**
     * 標題列用的細滑桿。圓鈕完全畫在元件裡面，不會被標題列底邊裁掉。
     */
    private static final class TitleBarSliderUI extends BasicSliderUI {
        static final int HEIGHT = 26;
        private static final int THUMB = 16;

        TitleBarSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(THUMB, THUMB);
        }

        @Override
        public void paintFocus(Graphics g) {
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = thumbRect.y + thumbRect.height / 2;
                int trackH = 4;
                int y = cy - trackH / 2;
                int x = trackRect.x;
                int w = Math.max(0, trackRect.width);
                g2.setColor(new Color(98, 78, 140));
                g2.fillRoundRect(x, y, w, trackH, trackH, trackH);
                int filled = thumbRect.x + thumbRect.width / 2 - x;
                if (filled > 0 && w > 0) {
                    g2.setColor(new Color(255, 122, 168));
                    g2.fillRoundRect(x, y, Math.min(filled, w), trackH, trackH, trackH);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(thumbRect.width, thumbRect.height) - 1;
                int x = thumbRect.x;
                int y = thumbRect.y;
                g2.setColor(Color.WHITE);
                g2.fillOval(x, y, size, size);
                g2.setColor(new Color(255, 122, 168));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(x, y, size, size);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 透明底。紫色面板放在留白之內，角色可以畫到面板外面，視窗一移動就跟著走。
     */
    private static final class FrameCanvas extends JPanel {
        private final JFrame frame;
        private final WallpaperPanel stage;
        private FramePortrait portrait;

        FrameCanvas(JFrame frame, WallpaperPanel stage) {
            super(null);
            this.frame = frame;
            this.stage = stage;
            setOpaque(false);
        }

        void setPortrait(FramePortrait portrait) {
            this.portrait = portrait;
        }

        Rectangle stageBounds() {
            return stage.getBounds();
        }

        @Override
        public void doLayout() {
            boolean maximized = isMaximized(frame);
            Insets overflow = portrait == null
                    ? new Insets(0, 0, 0, 0)
                    : portrait.overflow(getWidth(), getHeight(), maximized);
            int x = Math.max(0, overflow.left);
            int y = Math.max(0, overflow.top);
            int w = Math.max(0, getWidth() - overflow.left - overflow.right);
            int h = Math.max(0, getHeight() - overflow.top - overflow.bottom);
            stage.setBounds(x, y, w, h);
            stage.doLayout();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D behind = (Graphics2D) g.create();
            try {
                behind.setComposite(AlphaComposite.Src);
                behind.setColor(new Color(0, 0, 0, 0));
                // 只清髒區，避免局部重繪時整窗透明重填造成閃爍。
                java.awt.Rectangle clip = behind.getClipBounds();
                if (clip != null) {
                    behind.fillRect(clip.x, clip.y, clip.width, clip.height);
                } else {
                    behind.fillRect(0, 0, getWidth(), getHeight());
                }
                behind.setComposite(AlphaComposite.SrcOver);
                if (portrait != null) {
                    portrait.paintBehind(behind, this, stage.getBounds());
                }
            } finally {
                behind.dispose();
            }
            paintChildren(g);
            if (portrait != null) {
                Graphics2D front = (Graphics2D) g.create();
                try {
                    portrait.paintInFront(front, this, stage.getBounds());
                } finally {
                    front.dispose();
                }
            }
            paintBorder(g);
        }
    }

    /**
     * 只有角色不透明的像素吃掉點擊。標題列、視窗按鈕和縮放邊都穿透。
     */
    private static final class PortraitGlass extends JComponent {
        private final FrameCanvas canvas;

        PortraitGlass(FrameCanvas canvas) {
            this.canvas = canvas;
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || canvas.portrait == null) {
                        return;
                    }
                    canvas.portrait.pressed();
                }
            });
        }

        @Override
        public boolean contains(int x, int y) {
            FramePortrait portrait = canvas.portrait;
            if (portrait == null) {
                return false;
            }
            if (x < GRIP || y < GRIP || x >= getWidth() - GRIP || y >= getHeight() - GRIP) {
                return false;
            }
            Point onCanvas = SwingUtilities.convertPoint(this, x, y, canvas);
            if (!portrait.opaqueAt(canvas, onCanvas.x, onCanvas.y)) {
                return false;
            }
            Component under = SwingUtilities.getDeepestComponentAt(canvas, onCanvas.x, onCanvas.y);
            return under == null || under == canvas;
        }

    }
}
