package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
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

import com.example.ui.hud.AnimeShell;
import com.example.ui.hud.HomePortrait;
import com.example.ui.hud.ShellDimensions;

/**
 * 自訂標題列與邊緣縮放，讓桌面端可以調整視窗透明度。
 *
 * <p>Java {@code Window#setOpacity} 不能用在有系統標題列的視窗上，因此改由 Swing 自繪標題列。</p>
 */
public final class WindowChrome {

    private static final Color BAR_BG = ClassicBevel.FACE;
    private static final Color BAR_BORDER = ClassicBevel.DARK;
    private static final Color TITLE_FG = Color.WHITE;
    private static final Color MUTED_FG = CandyTheme.MUTED;
    private static final String STATUS_MARK = "punchclock.statusLabel";
    static final String TITLE_MINI_ZONE = "punchclock.titleMiniZone";
    private static final String TITLE_COLLAPSE_GLYPH = "punchclock.titleCollapseGlyph";
    private static final String TITLE_COLLAPSED = "punchclock.titleCollapsed";
    private static final String MINI_TOGGLE_TOOLTIP = "點一下縮成迷你橫條";
    private static final int COLLAPSE_GLYPH_SIZE = 10;
    private static final int GRIP = 6;
    /** 標題列左側圖示槽高度（與 {@link TitleBarLayout} 共用）。 */
    static final int TITLE_BAR_ICON_HEIGHT = 18;

    private WindowChrome() {
    }

    /** 依固定高度等比例縮放標題列圖示，寬約 28px（趴姿橫圖）。 */
    static Dimension titleBarIconSize(Image icon) {
        if (icon == null) {
            return new Dimension(0, 0);
        }
        int iw = icon.getWidth(null);
        int ih = icon.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return new Dimension(TITLE_BAR_ICON_HEIGHT, TITLE_BAR_ICON_HEIGHT);
        }
        int w = TITLE_BAR_ICON_HEIGHT * iw / Math.max(1, ih);
        return new Dimension(w, TITLE_BAR_ICON_HEIGHT);
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

        JPanel titleBar = createTitleBar(frame, title, icon, titleFont, controlFont);
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

        JLabel statusLabel = new JLabel(" ");
        statusLabel.putClientProperty(STATUS_MARK, Boolean.TRUE);
        statusLabel.setFont(controlFont);
        statusLabel.setForeground(CandyTheme.INK);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(ClassicBevel.FACE);
        statusLabel.setBorder(ClassicBevel.sunkenBorder(2));
        statusLabel.setPreferredSize(new Dimension(320, 22));
        JPanel statusStrip = new JPanel(new BorderLayout());
        statusStrip.setOpaque(false);
        statusStrip.setBorder(new EmptyBorder(0, 4, 4, 4));
        statusStrip.add(statusLabel, BorderLayout.CENTER);
        stage.add(statusStrip, BorderLayout.SOUTH);

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
        root.putClientProperty(STATUS_MARK, statusLabel);
        return new Controls(null, null);
    }

    /** 子視窗（如聊天）可關閉底部狀態列；主視窗應維持可見。 */
    public static void setStatusStripVisible(JFrame frame, boolean visible) {
        if (frame == null || frame.getRootPane() == null) {
            return;
        }
        Object label = frame.getRootPane().getClientProperty(STATUS_MARK);
        if (!(label instanceof JLabel)) {
            return;
        }
        java.awt.Container strip = ((JLabel) label).getParent();
        if (strip != null) {
            strip.setVisible(visible);
            strip.getParent().revalidate();
            strip.getParent().repaint();
        }
    }

    /** 主視窗底部狀態列文字。 */
    public static void setStatusText(JFrame frame, String text) {
        if (frame == null || frame.getRootPane() == null) {
            return;
        }
        Object label = frame.getRootPane().getClientProperty(STATUS_MARK);
        if (label instanceof JLabel) {
            ((JLabel) label).setText(text == null ? " " : text);
        }
    }

    /** 標題列顯示文字（與 {@code frame.setTitle} 同步）。 */
    public static void setWindowTitle(JFrame frame, String title) {
        if (frame == null) {
            return;
        }
        frame.setTitle(title == null ? "" : title);
        if (frame.getRootPane() == null) {
            return;
        }
        TitleName found = findTitleName(frame.getRootPane());
        if (found != null) {
            found.reset(title);
        }
    }

    private static TitleName findTitleName(Container root) {
        JComponent bar = findMarked(root, "punchclock.titleBar");
        if (bar == null) {
            return null;
        }
        for (Component child : bar.getComponents()) {
            if (child instanceof TitleName) {
                return (TitleName) child;
            }
        }
        return null;
    }

    /** 設定「外觀」分頁用的視窗透明度滑桿（與標題列脫鉤）。 */
    public static JSlider createTransparencySlider(Font controlFont) {
        JSlider slider = new JSlider(WindowOpacity.MIN_TRANSPARENCY_PERCENT, WindowOpacity.MAX_TRANSPARENCY_PERCENT, 0);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPaintTicks(true);
        slider.setPaintLabels(false);
        slider.setMajorTickSpacing(20);
        slider.setUI(new TitleBarSliderUI(slider));
        slider.setPreferredSize(new Dimension(200, TitleBarSliderUI.HEIGHT));
        slider.setMinimumSize(new Dimension(140, TitleBarSliderUI.HEIGHT));
        slider.setToolTipText("向右越透明，方便同時看到後面的畫面。最透明仍保留 40% 不透明度，以免點不到。");
        return slider;
    }

    public static JLabel createTransparencyValueLabel(Font controlFont, int percent) {
        JLabel valueLabel = new JLabel(WindowOpacity.formatPercentLabel(percent));
        valueLabel.setFont(controlFont);
        valueLabel.setForeground(MUTED_FG);
        valueLabel.setPreferredSize(new Dimension(56, 22));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
        return valueLabel;
    }

    private static Runnable titleBarMiniToggle;

    /** 一般模式點圖示、主標題或折疊三角（位移小於門檻）切換迷你；由 App 註冊。 */
    public static void setTitleBarMiniToggle(JFrame frame, Runnable toggle) {
        titleBarMiniToggle = toggle;
        if (frame == null || frame.getRootPane() == null) {
            return;
        }
        JComponent bar = findMarked(frame.getRootPane(), "punchclock.titleBar");
        if (bar != null) {
            markMiniToggleZones(bar, toggle);
        }
    }

    private static void markMiniToggleZones(JComponent bar, Runnable toggle) {
        for (Component child : bar.getComponents()) {
            if (!(child instanceof JComponent)) {
                continue;
            }
            JComponent zone = (JComponent) child;
            if (Boolean.TRUE.equals(zone.getClientProperty(TITLE_MINI_ZONE))) {
                zone.putClientProperty("punchclock.titleMiniToggle", toggle);
            }
        }
    }

    public static void paintTitleCollapseGlyph(Graphics2D g, int x, int y, int size, boolean collapsed) {
        int[] xs;
        int[] ys;
        if (collapsed) {
            xs = new int[] {x, x, x + size};
            ys = new int[] {y, y + size, y + size / 2};
        } else {
            int h = size;
            xs = new int[] {x, x + size, x + size / 2};
            ys = new int[] {y, y, y + h};
        }
        g.setColor(TITLE_FG);
        g.fillPolygon(xs, ys, 3);
        g.setColor(BAR_BORDER);
        g.drawPolygon(xs, ys, 3);
    }

    /**
     * 迷你橫條拿掉標題列與外框留白，300×56 才是內容區，不會被標題列裁成一條縫。
     * 同時把最小尺寸改成迷你或小方塊，並寫進 relayout 記得的最小值。
     */
    public static void setMiniBar(JFrame frame, boolean mini) {
        if (frame == null || frame.getRootPane() == null) {
            return;
        }
        JRootPane root = frame.getRootPane();
        JComponent canvas = (JComponent) root.getClientProperty("punchclock.canvas");
        JComponent body = (JComponent) root.getClientProperty("punchclock.body");
        JComponent stage = (JComponent) root.getClientProperty("punchclock.outer");
        JComponent title = canvas == null ? null : findMarked(canvas, "punchclock.titleBar");
        if (title != null) {
            title.setVisible(!mini);
            JComponent glyph = findMarked(title, TITLE_COLLAPSE_GLYPH);
            if (glyph != null) {
                glyph.putClientProperty(TITLE_COLLAPSED, mini);
                glyph.repaint();
            }
        }
        if (body != null) {
            body.setBorder(mini ? new EmptyBorder(0, 0, 0, 0) : new EmptyBorder(0, 6, 6, 6));
        }
        if (stage != null) {
            if (mini) {
                stage.setBorder(new EmptyBorder(0, 0, 0, 0));
            } else {
                applyWindowBorder(stage, isMaximized(frame));
            }
        }
        Dimension minimum = mini
                ? new Dimension(ShellDimensions.MINI_WIDTH, ShellDimensions.MINI_HEIGHT)
                : new Dimension(ShellDimensions.MIN_WIDTH, ShellDimensions.MIN_HEIGHT);
        frame.setMinimumSize(minimum);
        root.putClientProperty("punchclock.minimum", new Dimension(minimum));
        if (canvas != null) {
            canvas.revalidate();
            canvas.repaint();
        }
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

    /**
     * headless 首頁預覽：不建立 {@link JFrame}，把標題區、殼層與角色畫進點陣圖。
     */
    public static BufferedImage renderHomePreview(int width, int height) {
        HomePreview preview = new HomePreview(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            preview.paintPreview(g);
        } finally {
            g.dispose();
        }
        return image;
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

    private static final class HomePreview extends JPanel {
        private final AnimeShell shell;
        private final HomePortrait portrait;
        private final int titleH;
        private final Rectangle panel;

        HomePreview(int width, int height) {
            super(null);
            setSize(width, height);
            titleH = TitleBarSliderUI.HEIGHT + 9;
            panel = new Rectangle(0, 0, width, height);
            JTabbedPane tabs = new JTabbedPane();
            shell = new AnimeShell(null, tabs);
            portrait = new HomePortrait(shell);
            int bodyTop = titleH + GRIP;
            int bodyH = Math.max(120, height - bodyTop - GRIP);
            shell.setBounds(GRIP, bodyTop, Math.max(120, width - GRIP * 2), bodyH);
            shell.doLayout();
        }

        void paintPreview(Graphics2D g) {
            g.setColor(BAR_BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(BAR_BORDER);
            g.drawLine(0, titleH, getWidth(), titleH);
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            portrait.paintBehind(g, this, panel);
            shell.paint(g);
            portrait.paintInFront(g, this, panel);
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
            Font controlFont) {
        JPanel bar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                ClassicBevel.fillTitleBar((Graphics2D) g, getWidth(), getHeight());
            }
        };
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(2, 4, 2, 2));
        int barHeight = 28;
        bar.setPreferredSize(new Dimension(720, barHeight));

        JComponent iconLabel = null;
        if (icon != null) {
            final Image paintIcon = icon;
            iconLabel = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2.drawImage(paintIcon, 0, 0, getWidth(), getHeight(), this);
                    } finally {
                        g2.dispose();
                    }
                }
            };
            iconLabel.setPreferredSize(titleBarIconSize(icon));
            iconLabel.setOpaque(false);
            iconLabel.putClientProperty(TITLE_MINI_ZONE, Boolean.TRUE);
            iconLabel.setToolTipText(MINI_TOGGLE_TOOLTIP);
        }
        TitleName titleLabel = new TitleName(title, titleFont);
        titleLabel.setForeground(TITLE_FG);
        titleLabel.putClientProperty(TITLE_MINI_ZONE, Boolean.TRUE);
        titleLabel.setToolTipText(MINI_TOGGLE_TOOLTIP);

        JComponent collapseGlyph = createCollapseGlyph();
        collapseGlyph.putClientProperty(TITLE_MINI_ZONE, Boolean.TRUE);
        collapseGlyph.setToolTipText(MINI_TOGGLE_TOOLTIP);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
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

        bar.setLayout(new TitleBarLayout(iconLabel, titleLabel, collapseGlyph, buttons));
        if (iconLabel != null) {
            bar.add(iconLabel);
        }
        bar.add(titleLabel);
        bar.add(collapseGlyph);
        bar.add(buttons);

        DragSupport drag = new DragSupport(frame, buttons);
        MouseAdapter dragOnly = drag.adapter(false);
        MouseAdapter dragToggle = drag.adapter(true);
        bar.addMouseListener(dragOnly);
        bar.addMouseMotionListener(dragOnly);
        titleLabel.addMouseListener(dragToggle);
        titleLabel.addMouseMotionListener(dragToggle);
        collapseGlyph.addMouseListener(dragToggle);
        collapseGlyph.addMouseMotionListener(dragToggle);
        if (iconLabel != null) {
            iconLabel.addMouseListener(dragToggle);
            iconLabel.addMouseMotionListener(dragToggle);
        }

        return bar;
    }

    private static JComponent createCollapseGlyph() {
        return new JComponent() {
            {
                setOpaque(false);
                setName("punchclock.titleCollapseGlyph");
                putClientProperty(TITLE_COLLAPSE_GLYPH, Boolean.TRUE);
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (getWidth() <= 0 || getHeight() <= 0) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    int size = COLLAPSE_GLYPH_SIZE;
                    int x = (getWidth() - size) / 2;
                    int y = (getHeight() - size) / 2;
                    boolean collapsed = Boolean.TRUE.equals(getClientProperty(TITLE_COLLAPSED));
                    paintTitleCollapseGlyph(g2, x, y, size, collapsed);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(COLLAPSE_GLYPH_SIZE + 6, TITLE_BAR_ICON_HEIGHT);
            }
        };
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
        private String full;
        private String main;
        private String shown;

        TitleName(String fullTitle, Font font) {
            reset(fullTitle);
            setFont(font);
            setOpaque(false);
            setName("punchclock.titleName");
        }

        void reset(String fullTitle) {
            full = fullTitle == null ? "" : fullTitle.trim();
            main = mainTitle(full);
            shown = main;
            refreshToolTip();
        }

        boolean fit(FontMetrics metrics, int available) {
            int fullW = full.isEmpty() ? 0 : metrics.stringWidth(full);
            String next = fullW <= available && fullW > 0 ? full : main;
            if (next.equals(shown)) {
                return false;
            }
            shown = next;
            refreshToolTip();
            return true;
        }

        private void refreshToolTip() {
            if (Boolean.TRUE.equals(getClientProperty(TITLE_MINI_ZONE))) {
                setToolTipText(MINI_TOGGLE_TOOLTIP);
                return;
            }
            setToolTipText(shown.equals(full) ? null : full);
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

    /** 標題在左、視窗按鈕在右。 */
    private static final class TitleBarLayout implements LayoutManager {
        private final JComponent icon;
        private final TitleName title;
        private final JComponent collapse;
        private final JComponent buttons;

        TitleBarLayout(JComponent icon, TitleName title, JComponent collapse, JComponent buttons) {
            this.icon = icon;
            this.title = title;
            this.collapse = collapse;
            this.buttons = buttons;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(720, 32);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(360, 32);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int x0 = insets.left;
            int y0 = insets.top;
            int innerW = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int innerH = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            Dimension buttonSize = buttons.getPreferredSize();
            int buttonW = buttonSize.width;
            int buttonH = buttonSize.height;
            int buttonY = y0 + Math.max(0, (innerH - buttonH) / 2);
            buttons.setBounds(x0 + innerW - buttonW, buttonY, buttonW, buttonH);

            FontMetrics metrics = title.getFontMetrics(title.getFont());
            int iconW = icon == null ? 0 : icon.getPreferredSize().width;
            int iconH = icon == null ? 0 : icon.getPreferredSize().height;
            int textGap = iconW == 0 ? 0 : 8;
            int collapseW = collapse.getPreferredSize().width;
            int textRoom = Math.max(40, innerW - buttonW - iconW - textGap - collapseW - 16);
            title.fit(metrics, textRoom);
            int shownW = title.shown.isEmpty() ? 0 : metrics.stringWidth(title.shown);
            int textW = Math.min(textRoom, Math.max(0, shownW));
            int x = x0;
            if (icon != null) {
                int iconY = y0 + Math.max(0, (innerH - iconH) / 2);
                icon.setBounds(x, iconY, iconW, iconH);
                x += iconW + textGap;
            }
            title.setBounds(x, y0, textW, innerH);
            x += textW + 2;
            collapse.setBounds(x, y0, collapseW, innerH);
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
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                if (Boolean.TRUE.equals(getClientProperty("chrome.armed"))) {
                    ClassicBevel.fillSunken(g2, 0, 0, getWidth(), getHeight(), ClassicBevel.FACE);
                } else {
                    ClassicBevel.fillRaised(g2, 0, 0, getWidth(), getHeight(), ClassicBevel.FACE);
                }
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1f));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                switch (glyph) {
                    case MINIMIZE:
                        g2.drawLine(cx - 4, cy + 2, cx + 4, cy + 2);
                        break;
                    case MAXIMIZE:
                        g2.drawRect(cx - 4, cy - 4, 8, 8);
                        break;
                    case CLOSE:
                        g2.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
                        g2.drawLine(cx + 3, cy - 3, cx - 3, cy + 3);
                        break;
                    default:
                        break;
                }
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(18, 16);
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
        button.setOpaque(false);
        button.setForeground(Color.BLACK);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setMargin(new Insets(0, 0, 0, 0));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                ((JButton) e.getComponent()).putClientProperty("chrome.armed", Boolean.TRUE);
                e.getComponent().repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                ((JButton) e.getComponent()).putClientProperty("chrome.armed", Boolean.FALSE);
                e.getComponent().repaint();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                e.getComponent().repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ((JButton) e.getComponent()).putClientProperty("chrome.armed", Boolean.FALSE);
                e.getComponent().repaint();
            }
        });
        JButton painted = button;
        painted.addChangeListener(e -> painted.repaint());
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
     * 角色留白。軌道版若自訂了面板留白就用那一組；毛衣女孩已無外擴透明邊。
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

    /** 首頁視窗與紫色面板同大，不再外擴透明邊。 */
    static Insets overflowForPanel(int panelWidth, int panelHeight, boolean home) {
        return new Insets(0, 0, 0, 0);
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
        private final JComponent chromeButtons;
        private Point pressOnScreen;
        private Rectangle startBounds;
        private boolean dragging;

        DragSupport(JFrame frame, JComponent chromeButtons) {
            this.frame = frame;
            this.chromeButtons = chromeButtons;
        }

        MouseAdapter adapter(boolean allowMiniToggle) {
            return new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1 || overChromeControls(e)) {
                        return;
                    }
                    dragging = false;
                    pressOnScreen = e.getLocationOnScreen();
                    startBounds = frame.getBounds();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1 || pressOnScreen == null) {
                        dragging = false;
                        return;
                    }
                    if (allowMiniToggle && !dragging && !overChromeControls(e) && isMiniToggleZone(e)) {
                        Point now = e.getLocationOnScreen();
                        if (MiniBarDrag.isClick(pressOnScreen, now)) {
                            Runnable toggle = titleBarMiniToggle;
                            if (toggle == null && e.getComponent() instanceof JComponent) {
                                Object prop = ((JComponent) e.getComponent()).getClientProperty("punchclock.titleMiniToggle");
                                if (prop instanceof Runnable) {
                                    toggle = (Runnable) prop;
                                }
                            }
                            if (toggle != null) {
                                toggle.run();
                            }
                        }
                    }
                    dragging = false;
                    pressOnScreen = null;
                    startBounds = null;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressOnScreen == null || startBounds == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    int dx = now.x - pressOnScreen.x;
                    int dy = now.y - pressOnScreen.y;
                    if (!dragging) {
                        if (MiniBarDrag.isClick(dx, dy)) {
                            return;
                        }
                        dragging = true;
                    }
                    if (isMaximized(frame)) {
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

                private boolean overChromeControls(MouseEvent e) {
                    if (chromeButtons == null || !chromeButtons.isShowing()) {
                        return false;
                    }
                    Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), chromeButtons);
                    return chromeButtons.contains(p);
                }

                private boolean isMiniToggleZone(MouseEvent e) {
                    Component source = e.getComponent();
                    if (!(source instanceof JComponent)) {
                        return false;
                    }
                    return Boolean.TRUE.equals(((JComponent) source).getClientProperty(TITLE_MINI_ZONE));
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
     * 無跨框留白時用不透明底；軌道版仍有留白時維持透明底。
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
            boolean margin = overflow.top > 0 || overflow.right > 0 || overflow.left > 0 || overflow.bottom > 0;
            setOpaque(!margin);
            stage.setBounds(x, y, w, h);
            stage.doLayout();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D behind = (Graphics2D) g.create();
            try {
                java.awt.Rectangle clip = behind.getClipBounds();
                if (isOpaque()) {
                    behind.setColor(BAR_BG);
                    if (clip != null) {
                        behind.fillRect(clip.x, clip.y, clip.width, clip.height);
                    } else {
                        behind.fillRect(0, 0, getWidth(), getHeight());
                    }
                } else {
                    behind.setComposite(AlphaComposite.Src);
                    behind.setColor(new Color(0, 0, 0, 0));
                    // 只清髒區，避免局部重繪時整窗透明重填造成閃爍。
                    if (clip != null) {
                        behind.fillRect(clip.x, clip.y, clip.width, clip.height);
                    } else {
                        behind.fillRect(0, 0, getWidth(), getHeight());
                    }
                    behind.setComposite(AlphaComposite.SrcOver);
                }
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
