package com.example.pet;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;
import com.example.ui.hud.PixelSprite;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * 透明、無邊框的桌面寵物視窗。
 * <p>
 * 不掛在主視窗底下（沒有 owner），主視窗最小化時寵物仍留在桌面。
 * Windows 與 macOS 都先試 per-pixel 透明；系統不支援時改畫圓角底，避免整塊黑底。
 * 監聽器與計時器只在建構時建立一次，顯示與隱藏只開關計時器。
 */
public final class DesktopPetWindow extends JFrame {

    static final int WIDTH = 260;
    static final int HEIGHT = 260;
    static final int MARGIN = 24;
    /** 對話框留白。喵露耳朵在畫布上方約 20px，泡泡畫在這條留白裡。 */
    static final int BUBBLE_SLOT = 44;
    private static final int SCALE = 3;
    private static final int DRAG_THRESHOLD = 4;

    private final DesktopPetSettings settings;
    private final DesktopPetAnimation animation = new DesktopPetAnimation();
    private final PixelSprite sprite = PixelSprite.load();
    private final JPanel stage = new Stage();
    private final Timer timer;
    private final boolean perPixel;
    private boolean placed;
    private DesktopPetSpriteLoader.Frames milu;
    private boolean miluFailed;
    private boolean retired;

    private Point pressScreen;
    private Point grabOffset;
    private boolean dragging;

    DesktopPetWindow(DesktopPetSettings settings) {
        super("桌面寵物");
        requireEdt();
        if (settings == null) {
            throw new IllegalArgumentException("settings");
        }
        this.settings = settings;
        setUndecorated(true);
        try {
            setType(Type.UTILITY);
        } catch (RuntimeException ex) {
            // 沒設成工具視窗時，仍是沒有 owner 的獨立視窗。
        }
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setFocusableWindowState(false);
        setAutoRequestFocus(false);
        setResizable(false);
        try {
            setAlwaysOnTop(true);
        } catch (SecurityException ex) {
            // 沒有置頂權限時，寵物仍顯示在桌面上。
        }
        perPixel = tryPerPixelTransparency();
        if (!perPixel) {
            setBackground(CandyTheme.NIGHT);
            try {
                setShape(new RoundRectangle2D.Float(8, 8, WIDTH - 16, HEIGHT - 16, 28, 28));
            } catch (RuntimeException ex) {
                // 形狀裁切失敗時，圓角底仍畫在視窗裡。
            }
        }
        stage.setOpaque(false);
        stage.setDoubleBuffered(false);
        stage.setFocusable(false);
        stage.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        stage.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setContentPane(stage);
        setSize(WIDTH, HEIGHT);
        JComponent root = getRootPane();
        root.setOpaque(false);
        root.setDoubleBuffered(false);
        root.putClientProperty("Window.shadow", Boolean.FALSE);
        root.putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
        if (getLayeredPane() instanceof JComponent) {
            JComponent layered = (JComponent) getLayeredPane();
            layered.setOpaque(false);
            layered.setDoubleBuffered(false);
        }
        MouseAdapter pointer = pointer();
        stage.addMouseListener(pointer);
        stage.addMouseMotionListener(pointer);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopMotion();
            }
        });
        timer = new Timer(DesktopPetAnimation.TICK_MS, event -> onTick());
        timer.setRepeats(true);
        timer.setCoalesce(true);
        DesktopPetSpriteLoader.loadAsync(this::acceptFrames);
    }

    /** 測試直接裝上已在背景讀好的影格。正式路徑走 {@link DesktopPetSpriteLoader#loadAsync}。 */
    void useFrames(DesktopPetSpriteLoader.Frames frames) {
        requireEdt();
        if (frames == null) {
            return;
        }
        if (milu != null && milu != frames) {
            milu.flush();
        }
        milu = frames;
        miluFailed = false;
        repaint();
    }

    boolean usingMilu() {
        return milu != null;
    }

    java.awt.image.BufferedImage currentImage() {
        if (milu == null) {
            return null;
        }
        return milu.imageFor(animation);
    }

    private void acceptFrames(DesktopPetSpriteLoader.Frames frames) {
        if (retired || milu != null) {
            if (frames != null && frames != milu) {
                frames.flush();
            }
            return;
        }
        if (frames == null) {
            miluFailed = true;
        } else {
            milu = frames;
        }
        repaint();
    }

    void showPet() {
        requireEdt();
        if (!placed) {
            setLocation(initialLocation());
            placed = true;
        }
        if (!isVisible()) {
            setVisible(true);
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    void hidePet() {
        requireEdt();
        stopMotion();
        rememberLocation();
        if (isVisible()) {
            setVisible(false);
        }
    }

    void disposePet() {
        requireEdt();
        retired = true;
        stopMotion();
        rememberLocation();
        if (milu != null) {
            milu.flush();
            milu = null;
        }
        dispose();
    }

    boolean isAnimating() {
        return timer != null && timer.isRunning();
    }

    int interactionListenerCount() {
        return stage.getMouseListeners().length
                + stage.getMouseMotionListeners().length
                + getWindowListeners().length;
    }

    DesktopPetAnimation animation() {
        return animation;
    }

    JPanel stage() {
        return stage;
    }

    Rectangle usableBounds() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        return usable(gc);
    }

    void tickOnce() {
        requireEdt();
        if (!isAnimating()) {
            return;
        }
        onTick();
    }

    private void stopMotion() {
        if (timer != null) {
            timer.stop();
        }
    }

    private void onTick() {
        if (!isVisible()) {
            return;
        }
        animation.tick();
        int dx = animation.stepX();
        if (dx != 0) {
            Rectangle bounds = usableBounds();
            int minX = bounds.x;
            int maxX = bounds.x + bounds.width - getWidth();
            HorizontalStep step = stepHorizontal(getX(), dx, minX, maxX);
            if (step.bounce) {
                animation.turnAround();
            }
            if (step.x != getX()) {
                setLocation(step.x, getY());
            }
        }
        repaint();
    }

    private Point initialLocation() {
        Rectangle[] screens = screenRects();
        if (settings.hasPosition()) {
            return clampToScreens(settings.x(), settings.y(), getWidth(), getHeight(), screens);
        }
        return defaultSpot(usableBounds(), getWidth(), getHeight());
    }

    private void rememberLocation() {
        if (!placed) {
            return;
        }
        settings.savePosition(getX(), getY());
    }

    private MouseAdapter pointer() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                dragging = false;
                pressScreen = e.getLocationOnScreen();
                Point origin = getLocationOnScreen();
                grabOffset = new Point(pressScreen.x - origin.x, pressScreen.y - origin.y);
                animation.setCarried(true);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressScreen == null || grabOffset == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                if (pressScreen.distance(now) >= DRAG_THRESHOLD) {
                    dragging = true;
                }
                if (!dragging) {
                    return;
                }
                Point clamped = clampToScreens(
                        now.x - grabOffset.x,
                        now.y - grabOffset.y,
                        getWidth(),
                        getHeight(),
                        screenRects());
                setLocation(clamped);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (pressScreen == null || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                animation.setCarried(false);
                if (dragging) {
                    Point clamped = clampToScreens(getX(), getY(), getWidth(), getHeight(), screenRects());
                    setLocation(clamped);
                    rememberLocation();
                } else {
                    animation.poke();
                }
                pressScreen = null;
                grabOffset = null;
                dragging = false;
                repaint();
            }
        };
    }

    private boolean tryPerPixelTransparency() {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                return false;
            }
            setBackground(new Color(0, 0, 0, 0));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Rectangle[] screenRects() {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        Rectangle[] screens = new Rectangle[devices.length];
        for (int i = 0; i < devices.length; i++) {
            screens[i] = usable(devices[i].getDefaultConfiguration());
        }
        return screens;
    }

    private static Rectangle usable(GraphicsConfiguration gc) {
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(1, bounds.width - insets.left - insets.right),
                Math.max(1, bounds.height - insets.top - insets.bottom));
    }

    static Point defaultSpot(Rectangle usable, int windowWidth, int windowHeight) {
        int x = usable.x + Math.max(0, usable.width - windowWidth - MARGIN);
        int y = usable.y + Math.max(0, usable.height - windowHeight - MARGIN);
        return new Point(Math.max(usable.x, x), Math.max(usable.y, y));
    }

    static Point clampToScreens(int x, int y, int windowWidth, int windowHeight, Rectangle[] screens) {
        if (screens == null || screens.length == 0) {
            return new Point(x, y);
        }
        Rectangle spot = new Rectangle(x, y, windowWidth, windowHeight);
        Rectangle target = null;
        int bestArea = -1;
        for (Rectangle screen : screens) {
            if (screen == null) {
                continue;
            }
            Rectangle hit = screen.intersection(spot);
            int area = Math.max(0, hit.width) * Math.max(0, hit.height);
            if (area > bestArea) {
                bestArea = area;
                target = screen;
            }
        }
        if (target == null) {
            return new Point(x, y);
        }
        int maxX = target.x + Math.max(0, target.width - windowWidth);
        int maxY = target.y + Math.max(0, target.height - windowHeight);
        return new Point(
                Math.max(target.x, Math.min(x, maxX)),
                Math.max(target.y, Math.min(y, maxY)));
    }

    static HorizontalStep stepHorizontal(int x, int dx, int minX, int maxX) {
        if (maxX < minX) {
            maxX = minX;
        }
        if (dx == 0) {
            return new HorizontalStep(Math.max(minX, Math.min(maxX, x)), false);
        }
        int next = x + dx;
        if (next < minX || next > maxX) {
            int parked = Math.max(minX, Math.min(maxX, next));
            boolean walkingOut = (next < minX && dx < 0) || (next > maxX && dx > 0);
            return new HorizontalStep(parked, walkingOut);
        }
        return new HorizontalStep(next, false);
    }

    static final class HorizontalStep {
        final int x;
        final boolean bounce;

        HorizontalStep(int x, boolean bounce) {
            this.x = x;
            this.bounce = bounce;
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("桌面寵物視窗必須在 EDT 上操作");
        }
    }

    private final class Stage extends JPanel {
        @Override
        public boolean isOpaque() {
            return false;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setComposite(AlphaComposite.Src);
                if (perPixel) {
                    g.setColor(new Color(0, 0, 0, 0));
                    g.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    g.setColor(CandyTheme.NIGHT);
                    g.fillRoundRect(8, 8, getWidth() - 16, getHeight() - 16, 28, 28);
                }
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (milu != null || miluFailed) {
                    paintShadow(g);
                    paintSprite(g);
                }
                paintBubble(g);
            } finally {
                g.dispose();
            }
        }

        private void paintShadow(Graphics2D g) {
            int sw = milu != null ? DesktopPetSpriteLoader.DISPLAY : sprite.frameWidth() * SCALE;
            int x = (getWidth() - sw) / 2;
            int y = milu != null ? BUBBLE_SLOT + DesktopPetSpriteLoader.DISPLAY - 14 : getHeight() - 18;
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(x + sw / 5, y, Math.max(12, sw - sw / 3), 10);
        }

        private void paintSprite(Graphics2D g) {
            if (milu != null) {
                int x = (getWidth() - DesktopPetSpriteLoader.DISPLAY) / 2;
                // 左右走路各用自己的影格，不在這裡鏡像。
                DesktopPetSpriteLoader.draw(
                        g, milu.imageFor(animation), x, BUBBLE_SLOT, DesktopPetSpriteLoader.DISPLAY);
                return;
            }
            int sw = sprite.frameWidth() * SCALE;
            int sh = sprite.frameHeight() * SCALE;
            int x = (getWidth() - sw) / 2;
            int y = getHeight() - sh - 12 + animation.bob();
            Graphics2D copy = (Graphics2D) g.create();
            try {
                if (animation.facingRight()) {
                    sprite.draw(copy, animation.column(), animation.row(), x, y, SCALE);
                } else {
                    copy.translate(x + sw, y);
                    copy.scale(-1, 1);
                    sprite.draw(copy, animation.column(), animation.row(), 0, 0, SCALE);
                }
            } finally {
                copy.dispose();
            }
        }

        private void paintBubble(Graphics2D g) {
            String text = animation.bubble();
            if (text == null || text.isEmpty()) {
                return;
            }
            g.setFont(UiFonts.chinesePlain(12));
            FontMetrics metrics = g.getFontMetrics();
            String fitted = CandyTheme.fit(text, metrics, getWidth() - 24);
            if (fitted.isEmpty()) {
                return;
            }
            int textWidth = metrics.stringWidth(fitted);
            int bubbleW = textWidth + 16;
            int bubbleH = metrics.getHeight() + 8;
            int anchorX = getWidth() / 2;
            int anchorY = milu != null
                    ? BUBBLE_SLOT + 18
                    : getHeight() - sprite.frameHeight() * SCALE - 12 + animation.bob() - 4;
            int bx = Math.max(4, Math.min(anchorX - bubbleW / 2, getWidth() - bubbleW - 4));
            int by = Math.max(4, anchorY - bubbleH);
            g.setColor(CandyTheme.PINK_HI);
            g.fillRoundRect(bx, by, bubbleW, bubbleH, 12, 12);
            g.setColor(CandyTheme.PINK_DEEP);
            g.drawRoundRect(bx, by, bubbleW, bubbleH, 12, 12);
            int tailX = Math.max(bx + 10, Math.min(anchorX, bx + bubbleW - 10));
            g.setColor(CandyTheme.PINK_HI);
            g.fillPolygon(
                    new int[] {tailX - 5, tailX + 5, tailX},
                    new int[] {by + bubbleH - 1, by + bubbleH - 1, by + bubbleH + 6},
                    3);
            g.setColor(CandyTheme.PINK_INK);
            g.drawString(fitted, bx + 8, by + metrics.getAscent() + 4);
        }
    }
}
