package com.example.pet;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;
import com.example.ui.hud.PixelSprite;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Desktop;
import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 透明、無邊框的桌面寵物視窗。
 * <p>
 * 不掛在主視窗底下（沒有 owner），主視窗最小化時寵物仍留在桌面。
 * macOS 用 POPUP：點一下角色或拖曳不會把整個程式叫到前景，被擋住的主視窗留在後面。
 * 連點兩下角色、或點提醒泡泡，才會把主視窗拉上來。
 * Windows 與 macOS 都先試 per-pixel 透明；系統不支援時改畫圓角底，避免整塊黑底。
 * macOS 用視窗矩形判斷滑鼠落點，透明像素回傳 contains false 只會把點擊丟掉。
 * 視窗因此縮成角色加腳邊影子，頭頂貼著視窗上緣，才能拖到選單列下緣且不再擋住上方。
 * 監聽器與計時器只在建構時建立一次，顯示與隱藏只開關計時器。
 */
public final class DesktopPetWindow extends JFrame {

    static final int WIDTH = 260;
    static final int HEIGHT = 260;
    static final int MARGIN = 24;
    /** 對話框留白。喵露耳朵在畫布上方約 20px，泡泡畫在這條留白裡。 */
    static final int BUBBLE_SLOT = 44;
    /** 台詞泡泡：本機糖果色，不走已被 Win95 換裝改寫的 {@link CandyTheme#PINK_*}。 */
    private static final Color SPEECH_BUBBLE_FILL = new Color(255, 228, 240);
    private static final Color SPEECH_BUBBLE_EDGE = new Color(220, 140, 170);
    private static final Color SPEECH_BUBBLE_INK = new Color(72, 36, 56);
    private static final int SCALE = 3;
    private static final int DRAG_THRESHOLD = 4;

    private final DesktopPetSettings settings;
    private final DesktopPetAnimation animation = new DesktopPetAnimation();
    private final DesktopPetControl control = new DesktopPetControl();
    private final PixelSprite sprite = PixelSprite.load();
    private final JPanel stage = new Stage();
    private final Timer timer;
    private final boolean perPixel;
    private boolean placed;
    private DesktopPetSpriteLoader.Frames milu;
    private boolean miluFailed;
    private boolean retired;
    private boolean debug = Boolean.getBoolean("punchclock.pet.debug");

    private Point pressScreen;
    private Point grabOffset;
    private Point grabInStage;
    private Point lastDragScreen;
    private long lastDragNanos;
    private boolean dragging;
    private Runnable noticeEnded;
    private Runnable noticeReleased;
    private Runnable noticePump;
    private Runnable noticeOpen;
    private Runnable summonMain;
    /** 主視窗螢幕外框。最小化時為 null。呼喚與外框變大時用來避開重疊。 */
    private Supplier<Rectangle> mainBoundsSupplier = () -> null;
    private Runnable persistCare;
    private DesktopPetCare care;
    private long lastCarePersistAt;
    private Rectangle bubbleBounds;
    private int controlGroundY;
    private static final long CARE_PERSIST_MS = 30_000L;
    private JWindow petMenuWindow;
    private PetContextMenu petMenuPanel;
    private Timer menuAutoCloseTimer;
    private long menuPointerLeftAt = -1L;
    private AWTEventListener menuEventListener;
    private boolean suppressMenuOnRelease;
    private boolean controlWindowFocused = true;
    private Timer controlFocusTimer;
    private KeyEventDispatcher controlKeyDispatcher;
    /** 邏輯畫布上目前露出的區塊。視窗矩形就是這塊。 */
    private Rectangle view = new Rectangle(0, 0, WIDTH, HEIGHT);
    /** 待機角色加影子的穩定外框。頭頂就是這塊的上緣。 */
    private Rectangle fittedShell;
    private boolean fitting;
    private static final int VISIBLE_ALPHA = 16;
    /** 左右差幾像素以內不重縮視窗，避免呼吸影格帶動座標。 */
    private static final int SHELL_SLACK = 3;
    /**
     * 抓起時角色會略為升高。這段不把視窗往上長，否則拖曳中的座標會跳；
     * 多出來的幾像素裁在視窗外，待機頭頂仍貼著上緣。
     */
    private static final int CARRY_CLIP = 14;
    private static final List<CachedBody> BODY_CACHE = new ArrayList<CachedBody>();
    private static volatile Rectangle ESTIMATED_MILU_SHELL;

    DesktopPetWindow(DesktopPetSettings settings) {
        super("桌面寵物");
        requireEdt();
        if (settings == null) {
            throw new IllegalArgumentException("settings");
        }
        this.settings = settings;
        setUndecorated(true);
        try {
            // macOS 的 UTILITY 視窗一被點到，系統就會把整個程式叫到前景。
            // POPUP 在原生層是不啟動 App 的面板，點一下和拖曳不會把主視窗拉上來。
            setType(keepsAppInBackground() ? Type.POPUP : Type.UTILITY);
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
        stage.setDoubleBuffered(perPixel);
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
        installControlKeyBindings();
        KeyAdapter keys = keys();
        stage.addKeyListener(keys);
        addKeyListener(keys);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                hidePetMenu();
                stopMotion();
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (control.isActive()) {
                    controlWindowFocused = true;
                    repaint();
                }
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                if (control.isActive()) {
                    controlWindowFocused = false;
                    repaint();
                }
                // 等焦點轉到選單視窗後再判斷，避免一打開就被自己關掉。
                Window opposite = e.getOppositeWindow();
                SwingUtilities.invokeLater(() -> maybeHidePetMenuAfterFocusLost(opposite));
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
        fittedShell = null;
        refreshFit();
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

    boolean isDebug() {
        return debug;
    }

    /** 右鍵也會切換。預設關閉。 */
    void setDebug(boolean debug) {
        requireEdt();
        this.debug = debug;
        repaint();
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
            fittedShell = null;
        } else {
            milu = frames;
            fittedShell = null;
        }
        refreshFit();
        repaint();
    }

    void showPet() {
        requireEdt();
        refreshFit();
        if (!placed) {
            setLocation(initialLocation());
            placed = true;
        }
        nudgeAwayFromMainWindow();
        validate();
        if (!isVisible()) {
            setVisible(true);
            // 主視窗已經在前面時，只設 alwaysOnTop 不會把這扇 POPUP 排上去。
            toFront();
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    void hidePet() {
        requireEdt();
        stopMotion();
        rememberLocation();
        persistCareNow();
        if (isVisible()) {
            setVisible(false);
        }
    }

    void disposePet() {
        requireEdt();
        retired = true;
        removeControlKeyDispatcher();
        stopMotion();
        rememberLocation();
        persistCareNow();
        if (milu != null) {
            milu.flush();
            milu = null;
        }
        dispose();
    }

    /** 綁定養成邏輯；可為 null（測試路徑）。 */
    void bindCare(DesktopPetCare care, Runnable persistCare) {
        requireEdt();
        this.care = care;
        this.persistCare = persistCare;
        syncCareUi();
    }

    DesktopPetCare care() {
        return care;
    }

    void syncCareUi() {
        requireEdt();
        if (care == null || !care.isEnabled()) {
            animation.setCareIdle(false, DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT);
            stage.setToolTipText(null);
            return;
        }
        DesktopPetCare.Snapshot snap = care.tick();
        animation.setCareIdle(true, snap.hunger, snap.mood, snap.energy);
        stage.setToolTipText(care.statusLine());
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

    DesktopPetControl control() {
        return control;
    }

    JPanel stage() {
        return stage;
    }

    void enterControlMode() {
        requireEdt();
        if (control.isActive()) {
            return;
        }
        control.enter();
        animation.setPlayerControl(true);
        controlGroundY = getY();
        controlWindowFocused = true;
        setAutoRequestFocus(false);
        setFocusableWindowState(true);
        stage.setFocusable(true);
        scheduleControlFocusGrab();
        installControlKeyDispatcher();
        repaint();
    }

    void exitControlMode() {
        requireEdt();
        if (!control.isActive()) {
            return;
        }
        removeControlKeyDispatcher();
        control.exit();
        animation.setPlayerControl(false);
        animation.syncPlayerWalk(0);
        controlWindowFocused = true;
        if (controlFocusTimer != null) {
            controlFocusTimer.stop();
            controlFocusTimer = null;
        }
        setFocusableWindowState(false);
        stage.setFocusable(false);
        Point grounded = DesktopPetWindow.clampToScreens(
                getX(), controlGroundY, getWidth(), getHeight(), screenRects());
        setLocation(grounded);
        rememberLocation();
        repaint();
    }

    boolean controlModeActive() {
        return control.isActive();
    }

    boolean controlBindingsInstalled() {
        return PetControlKeyBindings.bindingsPresent(getRootPane());
    }

    private void installControlKeyBindings() {
        PetControlKeyBindings.install(getRootPane(), new PetControlKeyBindings.Handler() {
            @Override
            public boolean controlActive() {
                return control.isActive();
            }

            @Override
            public void onKeyPressed(int vk) {
                control.applyKeyPressed(vk);
                if (!control.isActive()) {
                    exitControlMode();
                    return;
                }
                tickControlMove();
                repaint();
            }

            @Override
            public void onKeyReleased(int vk) {
                control.applyKeyReleased(vk);
                animation.syncPlayerWalk(control.walkDirection());
                repaint();
            }

            @Override
            public boolean menuVisible() {
                return petMenuVisible();
            }

            @Override
            public void hideMenu() {
                hidePetMenu();
            }

            @Override
            public void exitControl() {
                exitControlMode();
            }
        });
    }

    private void scheduleControlFocusGrab() {
        if (controlFocusTimer != null) {
            controlFocusTimer.stop();
            controlFocusTimer = null;
        }
        Runnable grab = this::grabControlKeyboardFocus;
        SwingUtilities.invokeLater(grab);
        controlFocusTimer = new Timer(48, ev -> {
            ((Timer) ev.getSource()).stop();
            controlFocusTimer = null;
            grab.run();
        });
        controlFocusTimer.setRepeats(false);
        controlFocusTimer.start();
    }

    private void grabControlKeyboardFocus() {
        if (!control.isActive()) {
            return;
        }
        requestAppForeground();
        toFront();
        setFocusableWindowState(true);
        stage.setFocusable(true);
        requestFocus();
        stage.requestFocusInWindow();
        controlWindowFocused = isFocused();
        repaint();
    }

    private void installControlKeyDispatcher() {
        if (controlKeyDispatcher != null) {
            return;
        }
        controlKeyDispatcher = event -> {
            if (!control.isActive() || !isVisible()) {
                return false;
            }
            if (event.getID() != KeyEvent.KEY_PRESSED && event.getID() != KeyEvent.KEY_RELEASED) {
                return false;
            }
            int vk = event.getKeyCode();
            if (PetMenuDismiss.shouldCloseOnEscape(petMenuVisible(), vk)) {
                if (event.getID() == KeyEvent.KEY_PRESSED) {
                    hidePetMenu();
                }
                return true;
            }
            DesktopPetControl.KeyAction action = DesktopPetControl.keyAction(vk);
            if (action == DesktopPetControl.KeyAction.NONE) {
                return false;
            }
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                control.applyKeyPressed(vk);
                if (!control.isActive()) {
                    exitControlMode();
                    return true;
                }
                tickControlMove();
                repaint();
            } else {
                control.applyKeyReleased(vk);
                animation.syncPlayerWalk(control.walkDirection());
                repaint();
            }
            return true;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(controlKeyDispatcher);
    }

    private void removeControlKeyDispatcher() {
        if (controlKeyDispatcher == null) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(controlKeyDispatcher);
        controlKeyDispatcher = null;
    }

    private static void requestAppForeground() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            // isDesktopSupported 只代表 Desktop 存在；不支援這個 action 時 requestForeground 會丟例外。
            if (!desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                return;
            }
            desktop.requestForeground(true);
        } catch (RuntimeException ex) {
            // 部分環境不支援前景請求。
        }
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

    void setNoticeEnded(Runnable noticeEnded) {
        this.noticeEnded = noticeEnded;
    }

    void setNoticeReleased(Runnable noticeReleased) {
        this.noticeReleased = noticeReleased;
    }

    void setNoticePump(Runnable noticePump) {
        this.noticePump = noticePump;
    }

    /** 連點兩下角色時叫主視窗到前景。點一下只打招呼。 */
    void setSummonMain(Runnable summonMain) {
        this.summonMain = summonMain;
    }

    /**
     * 提供目前主視窗在螢幕上的外框。主視窗最小化或尚未顯示時應回傳 null。
     */
    void setMainBoundsSupplier(Supplier<Rectangle> mainBoundsSupplier) {
        this.mainBoundsSupplier = mainBoundsSupplier != null ? mainBoundsSupplier : () -> null;
    }

    boolean showNotice(String phrase, Runnable open) {
        requireEdt();
        if (!animation.showNotice(phrase)) {
            return false;
        }
        noticeOpen = open;
        repaint();
        return true;
    }

    boolean noticeActive() {
        return animation.isNotice();
    }

    boolean carried() {
        return animation.isCarried();
    }

    void applyCursorOutcome(DesktopPetCursorStatus.Outcome outcome) {
        requireEdt();
        if (outcome == null || outcome.action == DesktopPetCursorStatus.Action.NONE) {
            return;
        }
        switch (outcome.action) {
            case THINK:
                animation.setCursorHold(DesktopPetAnimation.Mode.THINK);
                animation.showCursorPhrase(outcome.bubble);
                break;
            case BUSY:
                animation.setCursorHold(DesktopPetAnimation.Mode.BUSY);
                animation.showCursorPhrase(outcome.bubble);
                break;
            case WORRY_FLASH:
                applyHoldPhase(outcome.hold);
                animation.flashWorry(outcome.bubble);
                break;
            case CELEBRATE:
                applyHoldPhase(outcome.hold);
                animation.celebrate(outcome.bubble);
                break;
            case CLEAR:
                animation.clearCursorHold();
                break;
            default:
                break;
        }
        repaint();
    }

    void clearCursorStatus() {
        requireEdt();
        animation.clearCursorHold();
        repaint();
    }

    private void applyHoldPhase(DesktopPetCursorStatus.Phase hold) {
        if (hold == DesktopPetCursorStatus.Phase.BUSY) {
            animation.setCursorHold(DesktopPetAnimation.Mode.BUSY);
        } else if (hold == DesktopPetCursorStatus.Phase.THINK) {
            animation.setCursorHold(DesktopPetAnimation.Mode.THINK);
        } else {
            animation.clearCursorHold();
        }
    }

    void tickOnce() {
        requireEdt();
        if (!isAnimating()) {
            return;
        }
        onTick();
    }

    /** 停掉走路計時，讓螢幕擷圖對得上這一格。 */
    void holdStill() {
        requireEdt();
        stopMotion();
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
        maybeTickCare();
        boolean wasNotice = animation.isNotice();
        animation.tick();
        if (control.isActive()) {
            control.tickJump();
        }
        if (wasNotice && !animation.isNotice()) {
            noticeOpen = null;
            if (noticeEnded != null) {
                noticeEnded.run();
            }
        } else if (!animation.isNotice() && animation.bubble() == null && !animation.isCarried()
                && !control.isActive() && noticePump != null) {
            noticePump.run();
        }
        if (control.isActive()) {
            tickControlMove();
        } else {
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
        }
        refreshFit();
        repaint();
    }

    private void tickControlMove() {
        animation.syncPlayerWalk(control.walkDirection());
        int dx = control.horizontalDelta();
        int lift = control.jumpLift();
        Point next = clampedControlMove(getX(), controlGroundY, dx, lift);
        if (dx != 0 && next.x == getX()) {
            int dir = control.walkDirection();
            if (dir < 0 && animation.facingRight()) {
                animation.turnAround();
            } else if (dir > 0 && !animation.facingRight()) {
                animation.turnAround();
            }
        }
        controlGroundY = next.y + lift;
        if (next.x != getX() || next.y != getY()) {
            setLocation(next);
        }
    }

    Point clampedControlMove(int x, int groundY, int dx, int jumpLift) {
        return DesktopPetControl.clampedMove(
                x, groundY, dx, jumpLift, getWidth(), getHeight(), screenRects());
    }

    private KeyAdapter keys() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (PetMenuDismiss.shouldCloseOnEscape(petMenuVisible(), e.getKeyCode())) {
                    hidePetMenu();
                    e.consume();
                }
            }
        };
    }

    private void maybeTickCare() {
        if (care == null) {
            return;
        }
        DesktopPetCare.Snapshot snap = care.tick();
        if (care.isEnabled()) {
            animation.setCareIdle(true, snap.hunger, snap.mood, snap.energy);
            stage.setToolTipText(care.statusLine());
        }
        long now = System.currentTimeMillis();
        if (lastCarePersistAt <= 0L || now - lastCarePersistAt >= CARE_PERSIST_MS) {
            persistCareNow();
        }
    }

    private void persistCareNow() {
        if (care != null) {
            care.tick();
        }
        lastCarePersistAt = System.currentTimeMillis();
        if (persistCare != null) {
            try {
                persistCare.run();
            } catch (RuntimeException ex) {
                // 偏好寫入失敗時畫面照常。
            }
        }
    }

    int viewOriginX() {
        return view.x;
    }

    int viewOriginY() {
        return view.y;
    }

    Rectangle currentView() {
        return new Rectangle(view);
    }

    void fitViewport() {
        requireEdt();
        refreshFit();
    }

    /**
     * 存的是邏輯畫布原點，不是視窗外框。舊版 260 視窗的左上角就是畫布原點，已經存過的位置不用搬。
     */
    private Point initialLocation() {
        Rectangle[] screens = screenRects();
        int windowWidth = Math.max(1, getWidth());
        int windowHeight = Math.max(1, getHeight());
        Point origin = settings.hasPosition()
                ? new Point(settings.x(), settings.y())
                : defaultSpot(usableBounds(), windowWidth, windowHeight);
        Point window = new Point(origin.x + view.x, origin.y + view.y);
        Point clamped = clampToScreens(window.x, window.y, windowWidth, windowHeight, screens);
        return nudgeOutsideMainWindow(
                clamped.x, clamped.y, windowWidth, windowHeight, mainWindowBounds(), usableBounds(), screens);
    }

    private Rectangle mainWindowBounds() {
        try {
            Rectangle bounds = mainBoundsSupplier.get();
            if (bounds == null || bounds.width < 1 || bounds.height < 1) {
                return null;
            }
            return bounds;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    void nudgeAwayFromMainWindow() {
        int windowWidth = Math.max(1, getWidth());
        int windowHeight = Math.max(1, getHeight());
        Point next = nudgeOutsideMainWindow(
                getX(),
                getY(),
                windowWidth,
                windowHeight,
                mainWindowBounds(),
                usableBounds(),
                screenRects());
        if (next.x != getX() || next.y != getY()) {
            setLocation(next);
        }
    }

    private void rememberLocation() {
        if (!placed) {
            return;
        }
        settings.savePosition(getX() - view.x, getY() - view.y);
    }

    private MouseAdapter pointer() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (careEnabled() && (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                        debug = !debug;
                        repaint();
                        return;
                    }
                    if (!careEnabled()) {
                        // 養成關閉：右鍵在 released 開選單；Shift+右鍵仍切換除錯。
                        pressScreen = null;
                        grabOffset = null;
                        return;
                    }
                    // 養成開啟：右鍵在 released 開選單，這裡先不拖曳。
                    pressScreen = null;
                    grabOffset = null;
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (control.isActive()) {
                    grabControlKeyboardFocus();
                }
                dragging = false;
                pressScreen = e.getLocationOnScreen();
                Point origin = getLocationOnScreen();
                grabOffset = new Point(pressScreen.x - origin.x, pressScreen.y - origin.y);
                grabInStage = e.getPoint();
                lastDragScreen = null;
                if (animation.isSettling()) {
                    animation.setCarried(false);
                }
                animation.setHandDown(true);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressScreen == null || grabOffset == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                if (!dragging && pressScreen.distance(now) >= DRAG_THRESHOLD) {
                    dragging = true;
                    if (control.isActive()) {
                        exitControlMode();
                    }
                    if (animation.isCarried()) {
                        animation.setCarried(false);
                    }
                    animation.setCarried(true);
                    lastDragScreen = now;
                    lastDragNanos = System.nanoTime();
                } else if (dragging && lastDragScreen != null) {
                    long nanos = System.nanoTime();
                    animation.noteCarryMotion(now.x - lastDragScreen.x, nanos - lastDragNanos);
                    lastDragScreen = now;
                    lastDragNanos = nanos;
                }
                if (!dragging) {
                    return;
                }
                Point clamped = clampDragToScreens(
                        now.x - grabOffset.x,
                        now.y - grabOffset.y,
                        getWidth(),
                        getHeight(),
                        screenRects());
                setLocation(clamped);
                followOpenPetMenu();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == 0) {
                        if (suppressMenuOnRelease) {
                            suppressMenuOnRelease = false;
                            return;
                        }
                        showPetMenu(e);
                    }
                    return;
                }
                if (pressScreen == null || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                boolean wasDrag = dragging;
                animation.setHandDown(false);
                if (wasDrag) {
                    animation.releaseCarry();
                    Point clamped = clampToScreens(getX(), getY(), getWidth(), getHeight(), screenRects());
                    setLocation(clamped);
                    // 拖曳允許只留一點在螢幕內；放開會把整扇拉回。看板要跟著這一下，否則停在舊座標蓋住角色。
                    followOpenPetMenu();
                    rememberLocation();
                    if (noticePump != null) {
                        noticePump.run();
                    }
                } else if (animation.isNotice() && bubbleBounds != null && bubbleBounds.contains(
                        e.getX() + view.x, e.getY() + view.y)) {
                    Runnable open = noticeOpen;
                    noticeOpen = null;
                    animation.dismissNotice();
                    if (noticeEnded != null) {
                        noticeEnded.run();
                    }
                    if (open != null) {
                        try {
                            open.run();
                        } catch (RuntimeException ex) {
                            System.err.println("[桌面寵物] 無法打開提醒：" + ex.getMessage());
                        }
                    }
                } else if (e.getClickCount() >= 2) {
                    summonMainWindow();
                } else if (careEnabled() && !control.isActive()) {
                    if (animation.isNotice()) {
                        noticeOpen = null;
                        if (noticeReleased != null) {
                            noticeReleased.run();
                        }
                    }
                    if (suppressMenuOnRelease) {
                        suppressMenuOnRelease = false;
                    } else {
                        showPetMenu(e);
                    }
                } else if (animation.isNotice()) {
                    noticeOpen = null;
                    animation.poke();
                    if (noticeReleased != null) {
                        noticeReleased.run();
                    }
                } else {
                    animation.poke();
                }
                pressScreen = null;
                grabOffset = null;
                dragging = false;
                if (!wasDrag) {
                    grabInStage = null;
                }
                repaint();
            }
        };
    }

    private boolean careEnabled() {
        return care != null && care.isEnabled();
    }

    private void showPetMenu(MouseEvent e) {
        if (animation.isCarried() || animation.isSettling()) {
            return;
        }
        if (PetMenuDismiss.shouldCloseOnToggle(petMenuVisible())) {
            hidePetMenu();
            return;
        }
        if (careEnabled()) {
            care.tick();
            syncCareUi();
        }
        long now = System.currentTimeMillis();
        PetContextMenu.Listener listener = new PetContextMenu.Listener() {
            @Override
            public void onToggleControl(boolean enable) {
                if (enable) {
                    SwingUtilities.invokeLater(DesktopPetWindow.this::enterControlMode);
                } else {
                    exitControlMode();
                }
            }

            @Override
            public void onCare(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode) {
                applyCareAction(action, mode);
            }

            @Override
            public void onToggleDebug() {
                debug = !debug;
                repaint();
            }

            @Override
            public void onDismiss() {
                hidePetMenu();
            }
        };
        PetContextMenu panel = PetContextMenu.build(
                careEnabled(), control.isActive(), debug, care, now, listener);
        armPetMenu(panel, e);
    }

    boolean petMenuVisible() {
        return petMenuWindow != null && petMenuWindow.isVisible();
    }

    PetContextMenu openPetMenuPanel() {
        return petMenuPanel;
    }

    void showPetMenuAt(int x, int y) {
        requireEdt();
        showPetMenu(new MouseEvent(
                stage, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                x, y, 1, false, MouseEvent.BUTTON3));
    }

    void hidePetMenu() {
        if (petMenuWindow != null && petMenuWindow.isVisible()) {
            petMenuWindow.setVisible(false);
        }
        cleanupPetMenu();
    }

    private void armPetMenu(PetContextMenu panel, MouseEvent e) {
        cleanupPetMenu();
        petMenuPanel = panel;
        suppressMenuOnRelease = false;
        menuPointerLeftAt = -1L;
        petMenuWindow = new JWindow(this);
        petMenuWindow.setFocusableWindowState(false);
        try {
            petMenuWindow.setAlwaysOnTop(true);
        } catch (SecurityException ex) {
            // 沒有置頂權限時仍顯示選單。
        }
        prepareMenuWindowTransparency(petMenuWindow);
        panel.setDoubleBuffered(false);
        petMenuWindow.getContentPane().add(panel);
        petMenuWindow.pack();
        placePetMenuWindow();
        claimMenuKeyboardFocus();
        menuEventListener = ev -> onPetMenuAwtEvent(ev);
        Toolkit.getDefaultToolkit().addAWTEventListener(
                menuEventListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        menuAutoCloseTimer = new Timer(200, ev -> tickPetMenuAutoClose());
        menuAutoCloseTimer.setRepeats(true);
        menuAutoCloseTimer.start();
        petMenuWindow.setVisible(true);
        petMenuWindow.toFront();
    }

    /** 看板不收鍵，Esc 要打到寵物視窗。還沒有焦點時主動要；呼叫端接著把看板顯示並 toFront。 */
    private void claimMenuKeyboardFocus() {
        if (!control.isActive()) {
            setFocusableWindowState(true);
            stage.setFocusable(true);
        }
        if (!PetMenuDismiss.needsKeyboardFocus(isFocused())) {
            return;
        }
        requestFocus();
        stage.requestFocusInWindow();
    }

    private void followOpenPetMenu() {
        if (!petMenuVisible()) {
            return;
        }
        placePetMenuWindow();
        petMenuWindow.toFront();
    }

    private void placePetMenuWindow() {
        if (petMenuWindow == null) {
            return;
        }
        Dimension size = petMenuWindow.getSize();
        Point origin = getLocationOnScreen();
        Point placed = PetContextMenu.placeBesidePet(
                origin.x, origin.y, getWidth(), getHeight(),
                size.width, size.height, screenRects());
        petMenuWindow.setLocation(placed);
    }

    private void cleanupPetMenu() {
        if (menuEventListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(menuEventListener);
            menuEventListener = null;
        }
        if (menuAutoCloseTimer != null) {
            menuAutoCloseTimer.stop();
            menuAutoCloseTimer = null;
        }
        menuPointerLeftAt = -1L;
        if (petMenuWindow != null) {
            petMenuWindow.dispose();
            petMenuWindow = null;
        }
        petMenuPanel = null;
        if (!control.isActive()) {
            setFocusableWindowState(false);
            stage.setFocusable(false);
        }
    }

    private void onPetMenuAwtEvent(AWTEvent ev) {
        if (!petMenuVisible()) {
            return;
        }
        if (ev instanceof KeyEvent) {
            KeyEvent key = (KeyEvent) ev;
            if (key.getID() == KeyEvent.KEY_PRESSED
                    && PetMenuDismiss.shouldCloseOnEscape(true, key.getKeyCode())) {
                hidePetMenu();
                key.consume();
            }
            return;
        }
        if (!(ev instanceof MouseEvent)) {
            return;
        }
        MouseEvent mouse = (MouseEvent) ev;
        if (mouse.getID() != MouseEvent.MOUSE_PRESSED) {
            return;
        }
        boolean inside = isEventInsideMenu(mouse);
        if (isEventOnPet(mouse)) {
            return;
        }
        if (PetMenuDismiss.shouldCloseOnOutsideClick(true, inside)) {
            hidePetMenu();
        }
    }

    private void tickPetMenuAutoClose() {
        if (!petMenuVisible()) {
            return;
        }
        boolean inside = isPointerOverMenu();
        long now = System.currentTimeMillis();
        menuPointerLeftAt = PetMenuDismiss.updateLeftAt(inside, menuPointerLeftAt, now);
        if (PetMenuDismiss.shouldAutoClose(true, inside, menuPointerLeftAt, now)) {
            hidePetMenu();
        }
    }

    private boolean isEventInsideMenu(MouseEvent mouse) {
        if (petMenuWindow == null) {
            return false;
        }
        Object src = mouse.getSource();
        if (src instanceof Component) {
            Component component = (Component) src;
            if (component == petMenuPanel || component == petMenuWindow
                    || (petMenuPanel != null && petMenuPanel.isAncestorOf(component))) {
                return true;
            }
            Window sourceWindow = SwingUtilities.getWindowAncestor(component);
            if (isPetMenuWindow(sourceWindow)) {
                return true;
            }
        }
        try {
            Point onScreen = mouse.getLocationOnScreen();
            Point origin = petMenuWindow.getLocationOnScreen();
            Rectangle bounds = new Rectangle(origin, petMenuWindow.getSize());
            return bounds.contains(onScreen);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isEventOnPet(MouseEvent mouse) {
        Object src = mouse.getSource();
        if (src == this || src == stage) {
            return true;
        }
        if (src instanceof java.awt.Component) {
            java.awt.Component component = (java.awt.Component) src;
            return component == this || isAncestorOf(component) || stage.isAncestorOf(component);
        }
        return false;
    }

    private boolean isPointerOverMenu() {
        if (petMenuWindow == null || !petMenuWindow.isVisible()) {
            return false;
        }
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return false;
        }
        try {
            Point origin = petMenuWindow.getLocationOnScreen();
            Rectangle bounds = new Rectangle(origin, petMenuWindow.getSize());
            return bounds.contains(info.getLocation());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void maybeHidePetMenuAfterFocusLost(Window opposite) {
        if (!petMenuVisible()) {
            return;
        }
        Window focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        boolean stayedOnMenu = isPetMenuWindow(opposite) || isPetMenuWindow(focused) || isPointerOverMenu();
        if (PetMenuDismiss.shouldCloseOnFocusLost(true, stayedOnMenu)) {
            hidePetMenu();
        }
    }

    private boolean isPetMenuWindow(Window window) {
        if (window == null || petMenuWindow == null) {
            return false;
        }
        return window == petMenuWindow;
    }

    private void applyCareAction(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode) {
        if (!careEnabled()) {
            return;
        }
        DesktopPetCare.Result result = care.perform(action);
        syncCareUi();
        persistCareNow();
        if (!result.ok) {
            if (result.bubble != null) {
                animation.celebrate(result.bubble);
            }
            repaint();
            return;
        }
        animation.playCare(mode, result.bubble);
        repaint();
    }

    /**
     * 旋轉後的角色圖層要留在視窗裡。繞抓取點縮小到四個角都進得了留白。
     */
    static double fitScale(
            int spriteX, int spriteY, int spriteSize,
            int pivotX, int pivotY, double radians,
            int canvasWidth, int canvasHeight, int margin) {
        if (Math.abs(radians) < 1e-4) {
            return 1;
        }
        if (cornersFit(1, spriteX, spriteY, spriteSize, pivotX, pivotY, radians,
                canvasWidth, canvasHeight, margin)) {
            return 1;
        }
        double low = 0.45;
        double high = 1;
        for (int i = 0; i < 14; i++) {
            double mid = (low + high) / 2;
            if (cornersFit(mid, spriteX, spriteY, spriteSize, pivotX, pivotY, radians,
                    canvasWidth, canvasHeight, margin)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static boolean cornersFit(
            double scale, int spriteX, int spriteY, int spriteSize,
            int pivotX, int pivotY, double radians,
            int canvasWidth, int canvasHeight, int margin) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int[] xs = {spriteX, spriteX + spriteSize};
        int[] ys = {spriteY, spriteY + spriteSize};
        for (int x : xs) {
            for (int y : ys) {
                double dx = x - pivotX;
                double dy = y - pivotY;
                double rx = pivotX + scale * (dx * cos - dy * sin);
                double ry = pivotY + scale * (dx * sin + dy * cos);
                if (rx < margin || ry < margin
                        || rx > canvasWidth - margin || ry > canvasHeight - margin) {
                    return false;
                }
            }
        }
        return true;
    }

    private void summonMainWindow() {
        if (summonMain == null) {
            return;
        }
        try {
            summonMain.run();
        } catch (RuntimeException ex) {
            System.err.println("[桌面寵物] 無法把主視窗叫到前面：" + ex.getMessage());
        }
    }

    /** macOS 點到一般視窗會啟動整個程式。POPUP 才不會把被擋住的主視窗拉上來。 */
    static boolean keepsAppInBackground() {
        String os = System.getProperty("os.name", "");
        return os.startsWith("Mac") || os.startsWith("Darwin");
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

    /**
     * 選單是獨立 JWindow。只把 content pane 塗透明不夠，圓角與陰影外的像素仍會露出視窗方底。
     * 與萌寵本體相同：支援 PERPIXEL_TRANSLUCENT 時把視窗底設成全透明，並關掉 root／layered pane 的不透明填色。
     */
    static void prepareMenuWindowTransparency(JWindow window) {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                window.setBackground(new Color(0, 0, 0, 0));
            }
        } catch (RuntimeException ex) {
            // 不支援 per-pixel 時不改視窗底色，選單內容仍畫得出來。
        }
        JComponent root = window.getRootPane();
        root.setOpaque(false);
        root.setDoubleBuffered(false);
        root.putClientProperty("Window.shadow", Boolean.FALSE);
        root.putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
        if (window.getLayeredPane() instanceof JComponent) {
            JComponent layered = (JComponent) window.getLayeredPane();
            layered.setOpaque(false);
            layered.setDoubleBuffered(false);
        }
        window.getContentPane().setBackground(new Color(0, 0, 0, 0));
        if (window.getContentPane() instanceof JComponent) {
            JComponent content = (JComponent) window.getContentPane();
            content.setOpaque(false);
            content.setDoubleBuffered(false);
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

    /**
     * 拖曳時讓視窗上緣貼到選單列下緣。視窗已經縮到角色，上緣就是可見頭頂。
     */
    static Point clampDragToScreens(int x, int y, int windowWidth, int windowHeight, Rectangle[] screens) {
        return clampDragToScreens(x, y, windowWidth, windowHeight, 0, screens);
    }

    /**
     * @param visibleTop 視窗座標裡第一列可見像素。貼合角色後是 0，頭頂才能貼上選單列。
     */
    static Point clampDragToScreens(
            int x, int y, int windowWidth, int windowHeight, int visibleTop, Rectangle[] screens) {
        if (screens == null || screens.length == 0) {
            return new Point(x, y);
        }
        int contentTop = 0;
        if (windowHeight > 0) {
            contentTop = Math.max(0, Math.min(visibleTop, windowHeight - 1));
        }
        int contentHeight = Math.max(1, windowHeight - contentTop);
        Rectangle content = new Rectangle(x, y + contentTop, Math.max(1, windowWidth), contentHeight);
        Rectangle host = null;
        int bestArea = -1;
        for (Rectangle screen : screens) {
            if (screen == null) {
                continue;
            }
            Rectangle hit = screen.intersection(content);
            int area = Math.max(0, hit.width) * Math.max(0, hit.height);
            if (area > bestArea) {
                bestArea = area;
                host = screen;
            }
        }
        if (host == null) {
            host = screens[0];
        }
        int minVisible = 24;
        int minX = host.x - windowWidth + minVisible;
        int maxX = host.x + host.width - minVisible;
        int minY = host.y - contentTop;
        int maxY = host.y + host.height - Math.min(minVisible, windowHeight);
        if (maxX < minX) {
            minX = maxX = host.x;
        }
        if (maxY < minY) {
            minY = maxY = host.y;
        }
        return new Point(
                Math.max(minX, Math.min(x, maxX)),
                Math.max(minY, Math.min(y, maxY)));
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

    static final int MAIN_AVOID_GAP = 6;

    /**
     * 呼喚時把寵物移出主視窗外框，並把整扇留在螢幕裡。
     * 外框變大後的平移可能不再和主視窗相交，但仍會伸出螢幕右緣；不相交時也要拉回來。
     * 四個方向都放不進螢幕時，改挑重疊最少的落點。
     */
    static Point nudgeOutsideMainWindow(
            int x,
            int y,
            int windowWidth,
            int windowHeight,
            Rectangle main,
            Rectangle usable,
            Rectangle[] screens) {
        Point onScreen = clampToScreens(x, y, windowWidth, windowHeight, screens);
        if (main == null || main.isEmpty() || windowWidth < 1 || windowHeight < 1) {
            return onScreen;
        }
        Rectangle pet = new Rectangle(onScreen.x, onScreen.y, windowWidth, windowHeight);
        if (!pet.intersects(main)) {
            return onScreen;
        }
        x = onScreen.x;
        y = onScreen.y;
        int gap = MAIN_AVOID_GAP;
        Point bestHoriz = null;
        long bestHorizDistance = Long.MAX_VALUE;
        Point bestVert = null;
        long bestVertDistance = Long.MAX_VALUE;
        int[][] proposals = {
            {main.x + main.width + gap, y},
            {main.x - gap - windowWidth, y},
            {x, main.y + main.height + gap},
            {x, main.y - gap - windowHeight},
        };
        for (int[] proposal : proposals) {
            Point candidate = clampToScreens(proposal[0], proposal[1], windowWidth, windowHeight, screens);
            Rectangle placed = new Rectangle(candidate.x, candidate.y, windowWidth, windowHeight);
            if (placed.intersects(main)) {
                continue;
            }
            boolean horizClear = placed.x >= main.x + main.width + gap
                    || placed.x + placed.width <= main.x - gap;
            boolean vertClear = placed.y >= main.y + main.height + gap
                    || placed.y + placed.height <= main.y - gap;
            if (!horizClear && !vertClear) {
                continue;
            }
            long distance = distanceSquared(x, y, candidate.x, candidate.y);
            if (horizClear) {
                if (distance < bestHorizDistance) {
                    bestHorizDistance = distance;
                    bestHoriz = candidate;
                }
            } else if (distance < bestVertDistance) {
                bestVertDistance = distance;
                bestVert = candidate;
            }
        }
        if (bestHoriz != null) {
            return bestHoriz;
        }
        if (bestVert != null) {
            return bestVert;
        }
        return clearestSpot(x, y, windowWidth, windowHeight, main, usable, screens);
    }

    /**
     * 微移失敗時的落點。能完全避開就避開；螢幕剩下的縫比寵物小，就貼著空出的那一側，讓重疊面積最小。
     */
    private static Point clearestSpot(
            int x,
            int y,
            int windowWidth,
            int windowHeight,
            Rectangle main,
            Rectangle usable,
            Rectangle[] screens) {
        java.util.ArrayList<Point> candidates = new java.util.ArrayList<Point>();
        if (usable != null && usable.width > 0 && usable.height > 0) {
            candidates.add(defaultSpot(usable, windowWidth, windowHeight));
            candidates.add(new Point(usable.x, usable.y));
            candidates.add(new Point(
                    Math.max(usable.x, usable.x + usable.width - windowWidth), usable.y));
            candidates.add(new Point(
                    usable.x, Math.max(usable.y, usable.y + usable.height - windowHeight)));
            candidates.add(new Point(
                    Math.max(usable.x, usable.x + usable.width - windowWidth),
                    Math.max(usable.y, usable.y + usable.height - windowHeight)));
        }
        if (screens != null) {
            for (Rectangle screen : screens) {
                if (screen == null) {
                    continue;
                }
                candidates.add(new Point(screen.x, screen.y));
                candidates.add(new Point(
                        Math.max(screen.x, screen.x + screen.width - windowWidth), screen.y));
                candidates.add(new Point(
                        screen.x, Math.max(screen.y, screen.y + screen.height - windowHeight)));
                candidates.add(new Point(
                        Math.max(screen.x, screen.x + screen.width - windowWidth),
                        Math.max(screen.y, screen.y + screen.height - windowHeight)));
            }
        }
        Point bestClear = null;
        long bestClearDistance = Long.MAX_VALUE;
        Point bestPartial = null;
        long bestPartialArea = Long.MAX_VALUE;
        long bestPartialDistance = Long.MAX_VALUE;
        for (Point raw : candidates) {
            Point candidate = clampToScreens(raw.x, raw.y, windowWidth, windowHeight, screens);
            Rectangle placed = new Rectangle(candidate.x, candidate.y, windowWidth, windowHeight);
            long distance = distanceSquared(x, y, candidate.x, candidate.y);
            if (!placed.intersects(main) && clearsMainWithGap(placed, main, MAIN_AVOID_GAP)) {
                if (distance < bestClearDistance) {
                    bestClearDistance = distance;
                    bestClear = candidate;
                }
                continue;
            }
            Rectangle overlap = placed.intersection(main);
            long area = (long) Math.max(0, overlap.width) * Math.max(0, overlap.height);
            if (area < bestPartialArea || (area == bestPartialArea && distance < bestPartialDistance)) {
                bestPartialArea = area;
                bestPartialDistance = distance;
                bestPartial = candidate;
            }
        }
        if (bestClear != null) {
            return bestClear;
        }
        if (bestPartial != null) {
            return bestPartial;
        }
        return new Point(x, y);
    }

    private static boolean clearsMainWithGap(Rectangle placed, Rectangle main, int gap) {
        if (main == null || main.isEmpty() || placed == null) {
            return true;
        }
        if (placed.intersects(main)) {
            return false;
        }
        boolean horizClear = placed.x >= main.x + main.width + gap
                || placed.x + placed.width <= main.x - gap;
        boolean vertClear = placed.y >= main.y + main.height + gap
                || placed.y + placed.height <= main.y - gap;
        return horizClear || vertClear;
    }

    private static long distanceSquared(int x1, int y1, int x2, int y2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        return dx * dx + dy * dy;
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

    /**
     * 把視窗縮到角色外框（{@link #stableShellFor} 全動作 union）。上緣對齊可見頭頂。
     * 外框已固定時不再 {@code setSize}；僅首次或 union 改變時才調整尺寸與原點。
     */
    private void refreshFit() {
        if (fitting) {
            return;
        }
        fitting = true;
        try {
            Rectangle next = targetView();
            if (next == null || next.width < 1 || next.height < 1 || next.equals(view)) {
                return;
            }
            int dx = next.x - view.x;
            int dy = next.y - view.y;
            view = next;
            Dimension size = new Dimension(view.width, view.height);
            stage.setPreferredSize(size);
            stage.setMinimumSize(size);
            stage.setMaximumSize(size);
            stage.setSize(view.width, view.height);
            setSize(view.width, view.height);
            java.awt.Container root = getContentPane();
            if (root != null) {
                root.setSize(view.width, view.height);
            }
            if (placed) {
                setLocation(getX() + dx, getY() + dy);
            }
            if (isDisplayable()) {
                validate();
            }
            if (!perPixel) {
                try {
                    setShape(new Rectangle(0, 0, view.width, view.height));
                } catch (RuntimeException ex) {
                    // 形狀裁切失敗時，視窗仍是角色外框。
                }
            }
        } finally {
            fitting = false;
        }
    }

    private Rectangle targetView() {
        if (milu == null) {
            if (miluFailed) {
                return pixelBody();
            }
            return estimatedMiluShell();
        }
        if (fittedShell == null) {
            fittedShell = stableShellFor(milu);
        }
        return viewForPose(milu, fittedShell, animation);
    }

    /**
     * 視窗在邏輯畫布上的露出區塊；測試與 {@link #paintMiluViewport} 共用。
     * {@code fittedShell}、{@code animation} 保留給既有測試呼叫端，實際一律 {@link #stableShellFor}。
     */
    static Rectangle viewForPose(
            DesktopPetSpriteLoader.Frames milu,
            Rectangle fittedShell,
            DesktopPetAnimation animation) {
        if (milu == null) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        return stableShellFor(milu);
    }

    /**
     * 與 {@link Stage#contains(int, int)} 相同：將舞台座標換成邏輯畫布後判斷是否點在角色上（headless 測試用）。
     */
    static boolean hitMiluAtStage(Rectangle view, DesktopPetAnimation animation, int stageX, int stageY) {
        if (view == null || stageX < 0 || stageY < 0 || stageX >= view.width || stageY >= view.height) {
            return false;
        }
        int canvasX = stageX + view.x;
        int canvasY = stageY + view.y;
        return miluSpriteHit(canvasX, canvasY, animation);
    }

    private static boolean miluSpriteHit(int x, int y, DesktopPetAnimation animation) {
        int size = DesktopPetSpriteLoader.DISPLAY;
        int spriteX = (WIDTH - size) / 2;
        int spriteY = BUBBLE_SLOT + animation.bob();
        int pad = 6;
        int left = spriteX - pad;
        int right = spriteX + size + pad;
        int top = Math.max(0, spriteY - 16);
        int bottom = spriteY + size + 16;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    /** 同一動作模式內固定的視野外框（該模式所有影格的 union）。 */
    static Rectangle shellForViewport(DesktopPetSpriteLoader.Frames milu, DesktopPetAnimation animation) {
        Rectangle union = null;
        for (DesktopPetSpriteLoader.Sprite sprite : milu.viewportSprites(animation)) {
            if (sprite == null || sprite.image == null) {
                continue;
            }
            Rectangle raw = cachedBody(sprite.image, sprite.offsetX, sprite.offsetY);
            union = union == null ? raw : unionRect(union, raw);
        }
        if (union == null) {
            return clipToCanvas(shellFor(milu));
        }
        return clipToCanvas(union);
    }

    /**
     * 把喵露與影子畫進與實際視窗同尺寸的緩衝圖（headless 驗收用）。
     *
     * @param drawViewportBorder 為 true 時在視窗外框畫粉線
     */
    static BufferedImage paintMiluViewport(
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            boolean drawViewportBorder) {
        return paintMiluViewport(view, milu, animation, drawViewportBorder, 0, 0, 0, 1);
    }

    static BufferedImage paintMiluViewport(
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            boolean drawViewportBorder,
            double pivotX,
            double pivotY,
            double radians,
            double scale) {
        if (view == null || view.width < 1 || view.height < 1 || milu == null) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage image = new BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_ARGB);
        paintMiluViewport(image, view, milu, animation, pivotX, pivotY, radians, scale);
        if (drawViewportBorder) {
            Graphics2D border = image.createGraphics();
            try {
                border.setColor(new Color(255, 64, 96, 220));
                border.drawRect(0, 0, Math.max(0, view.width - 1), Math.max(0, view.height - 1));
            } finally {
                border.dispose();
            }
        }
        return image;
    }

    static void paintMiluViewport(
            BufferedImage target,
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            double pivotX,
            double pivotY,
            double radians,
            double scale) {
        paintMiluViewport(target, view, milu, animation, pivotX, pivotY, radians, scale, 1);
    }

    static void paintMiluViewport(
            BufferedImage target,
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            double pivotX,
            double pivotY,
            double radians,
            double scale,
            double pixelScale) {
        if (target == null || view == null || view.width < 1 || view.height < 1 || milu == null) {
            return;
        }
        Graphics2D g = target.createGraphics();
        try {
            if (pixelScale > 1.0001) {
                g.scale(pixelScale, pixelScale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            }
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, view.width, view.height);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(-view.x, -view.y);
            paintMiluShadow(g, WIDTH);
            paintMiluSprite(g, milu, animation, pivotX, pivotY, radians, scale);
        } finally {
            g.dispose();
        }
    }

    /**
     * 把角色畫進已平移到邏輯畫布的 Graphics。拖曳時繞抓取點旋轉並縮小，避免貼到視窗邊。
     */
    private static void paintMiluSprite(
            Graphics2D g,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            double pivotX,
            double pivotY,
            double radians,
            double scale) {
        Graphics2D layer = g;
        boolean tilted = Math.abs(radians) > 1e-4 && scale > 0 && scale < 1.0001;
        if (tilted) {
            layer = (Graphics2D) g.create();
            layer.translate(pivotX, pivotY);
            layer.rotate(radians);
            layer.scale(scale, scale);
            layer.translate(-pivotX, -pivotY);
        }
        try {
            int x = (WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
            DesktopPetSpriteLoader.Sprite frame = milu.spriteFor(animation);
            DesktopPetSpriteLoader.draw(
                    layer,
                    frame.image,
                    x,
                    BUBBLE_SLOT,
                    DesktopPetSpriteLoader.DISPLAY,
                    frame.offsetX,
                    frame.offsetY);
        } finally {
            if (layer != g) {
                layer.dispose();
            }
        }
    }

    static void assertOpaqueWithinViewport(BufferedImage viewport, int threshold) {
        Rectangle bounds = opaqueBounds(viewport, threshold);
        if (bounds.width < 1 || bounds.height < 1) {
            throw new AssertionError("no opaque pixels");
        }
        if (bounds.x < 0 || bounds.y < 0
                || bounds.x + bounds.width > viewport.getWidth()
                || bounds.y + bounds.height > viewport.getHeight()) {
            throw new AssertionError(
                    "opaque " + bounds + " outside " + viewport.getWidth() + "x" + viewport.getHeight());
        }
    }

    private Rectangle pixelBody() {
        int sw = Math.max(1, sprite.frameWidth() * SCALE);
        int sh = Math.max(1, sprite.frameHeight() * SCALE);
        int x = Math.max(0, (WIDTH - sw) / 2);
        int y = Math.max(0, HEIGHT - sh - 16);
        return clipToCanvas(new Rectangle(x, y, Math.min(sw, WIDTH - x), Math.min(sh + 14, HEIGHT - y)));
    }

    /** 待機影格的外框。上緣是這張圖的第一列可見像素，左右納入接近的動作。 */
    static Rectangle shellFor(DesktopPetSpriteLoader.Frames frames) {
        if (frames == null) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        DesktopPetSpriteLoader.Sprite[] sprites = frames.allSprites();
        if (sprites.length == 0 || sprites[0] == null || sprites[0].image == null) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        Rectangle idle = cachedBody(sprites[0].image, sprites[0].offsetX, sprites[0].offsetY);
        return clipToCanvas(idle);
    }

    /** 影格尚未載入時使用的外框，與 {@link #stableShellFor} 同尺寸，避免先縮成像素後備格。 */
    static Rectangle estimatedMiluShell() {
        Rectangle cached = ESTIMATED_MILU_SHELL;
        if (cached != null) {
            return new Rectangle(cached);
        }
        DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
        Rectangle shell = frames != null ? stableShellFor(frames) : new Rectangle(0, 0, WIDTH, HEIGHT);
        ESTIMATED_MILU_SHELL = new Rectangle(shell);
        return new Rectangle(shell);
    }

    /** 全部動作影格 union 的穩定外框；換 mode 時視窗不再 resize。 */
    static Rectangle stableShellFor(DesktopPetSpriteLoader.Frames frames) {
        if (frames == null) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        Rectangle union = null;
        for (DesktopPetSpriteLoader.Sprite sprite : frames.allSprites()) {
            if (sprite == null || sprite.image == null) {
                continue;
            }
            Rectangle raw = cachedBody(sprite.image, sprite.offsetX, sprite.offsetY);
            union = union == null ? raw : unionRect(union, raw);
        }
        if (union == null) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        Rectangle hit = clipToCanvas(union);
        int top = Math.max(0, hit.y - BUBBLE_SLOT);
        int extraTop = hit.y - top;
        return clipToCanvas(new Rectangle(hit.x, top, hit.width, hit.height + extraTop));
    }

    private static boolean covered(Rectangle shell, Rectangle raw) {
        if (shell == null || raw == null) {
            return false;
        }
        return raw.x >= shell.x - SHELL_SLACK
                && raw.y >= shell.y - CARRY_CLIP
                && raw.x + raw.width <= shell.x + shell.width + SHELL_SLACK
                && raw.y + raw.height <= shell.y + shell.height + SHELL_SLACK;
    }

    static boolean poseFitsShell(Rectangle shell, Rectangle raw) {
        return covered(shell, raw);
    }

    private static Rectangle unionRect(Rectangle first, Rectangle second) {
        int left = Math.min(first.x, second.x);
        int top = Math.min(first.y, second.y);
        int right = Math.max(first.x + first.width, second.x + second.width);
        int bottom = Math.max(first.y + first.height, second.y + second.height);
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    private static Rectangle clipToCanvas(Rectangle rect) {
        Rectangle hit = rect.intersection(new Rectangle(0, 0, WIDTH, HEIGHT));
        if (hit.width < 1 || hit.height < 1) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        return hit;
    }

    static Rectangle miluBodyOnCanvas(BufferedImage image, int offsetX, int offsetY) {
        return cachedBody(image, offsetX, offsetY);
    }

    /**
     * 在已 {@code translate(-view.x,-view.y)}（並可選 {@code scale(pixelScale)}) 的 Graphics 上畫台詞泡泡。
     * 座標以視窗局部為準；回傳畫布座標的點擊區，無泡泡時回傳 null。
     */
    static Rectangle paintSpeechBubble(
            Graphics2D g,
            Rectangle view,
            DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation,
            String text) {
        if (text == null || text.isEmpty() || view == null) {
            return null;
        }
        g.setFont(UiFonts.chinesePlain(14));
        FontMetrics metrics = g.getFontMetrics();
        int innerMax = Math.max(96, view.width - 12);
        String fitted = CandyTheme.fit(text, metrics, innerMax);
        if (fitted.isEmpty()) {
            return null;
        }
        int padX = 12;
        int padY = 8;
        int textWidth = metrics.stringWidth(fitted);
        int bubbleW = textWidth + padX * 2;
        int bubbleH = metrics.getHeight() + padY;
        int bx = Math.max(0, (view.width - bubbleW) / 2);
        int headCanvasY = view.y + BUBBLE_SLOT;
        if (milu != null && animation != null) {
            DesktopPetSpriteLoader.Sprite sprite = milu.spriteFor(animation);
            if (sprite != null) {
                headCanvasY = DesktopPetMiluLayout.placement(sprite).headDisplayTop;
            }
        }
        int by = headCanvasY - view.y - bubbleH - 8;
        if (by < 0) {
            by = 0;
        }
        int anchorX = view.width / 2;
        g.setColor(SPEECH_BUBBLE_FILL);
        g.fillRoundRect(bx, by, bubbleW, bubbleH, 12, 12);
        g.setColor(SPEECH_BUBBLE_EDGE);
        g.drawRoundRect(bx, by, bubbleW, bubbleH, 12, 12);
        int tailX = Math.max(bx + 10, Math.min(anchorX, bx + bubbleW - 10));
        g.setColor(SPEECH_BUBBLE_FILL);
        g.fillPolygon(
                new int[] {tailX - 5, tailX + 5, tailX},
                new int[] {by + bubbleH - 1, by + bubbleH - 1, by + bubbleH + 6},
                3);
        g.setColor(SPEECH_BUBBLE_INK);
        g.drawString(fitted, bx + padX, by + metrics.getAscent() + padY / 2);
        return new Rectangle(bx + view.x, by + view.y, bubbleW, bubbleH + 6);
    }

    private static synchronized Rectangle cachedBody(BufferedImage image, int offsetX, int offsetY) {
        for (CachedBody entry : BODY_CACHE) {
            if (entry.image == image && entry.offsetX == offsetX && entry.offsetY == offsetY) {
                return new Rectangle(entry.bounds);
            }
        }
        Rectangle bounds = rasterBody(image, offsetX, offsetY);
        BODY_CACHE.add(new CachedBody(image, offsetX, offsetY, bounds));
        return new Rectangle(bounds);
    }

    private static Rectangle rasterBody(BufferedImage image, int offsetX, int offsetY) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = (WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
            DesktopPetSpriteLoader.draw(
                    g, image, x, BUBBLE_SLOT, DesktopPetSpriteLoader.DISPLAY, offsetX, offsetY);
            paintMiluShadow(g, WIDTH);
        } finally {
            g.dispose();
        }
        return opaqueBounds(canvas, VISIBLE_ALPHA);
    }

    static void paintMiluShadow(Graphics2D g, int canvasWidth) {
        int sw = DesktopPetSpriteLoader.DISPLAY;
        int x = (canvasWidth - sw) / 2;
        int y = BUBBLE_SLOT + DesktopPetSpriteLoader.DISPLAY - 14;
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(x + sw / 5, y, Math.max(12, sw - sw / 3), 10);
    }

    static Rectangle opaqueBounds(BufferedImage image, int threshold) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > threshold) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < 0) {
            return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** 視窗上緣到第一列可見像素。貼合之後是 0。 */
    static int visibleTopInset(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > VISIBLE_ALPHA) {
                    return y;
                }
            }
        }
        return image.getHeight();
    }

    private static final class CachedBody {
        final BufferedImage image;
        final int offsetX;
        final int offsetY;
        final Rectangle bounds;

        CachedBody(BufferedImage image, int offsetX, int offsetY, Rectangle bounds) {
            this.image = image;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.bounds = bounds;
        }
    }

    private final class Stage extends JPanel {
        private BufferedImage liveFrameBuffer;

        @Override
        public boolean isOpaque() {
            return false;
        }

        @Override
        public boolean contains(int x, int y) {
            if (!super.contains(x, y)) {
                return false;
            }
            if (control.isActive()) {
                int boxW = 120;
                int boxH = 24;
                int bx = getWidth() - boxW - 6;
                if (x >= bx && x <= bx + boxW && y >= 6 && y <= 6 + boxH) {
                    return true;
                }
            }
            int canvasX = x + view.x;
            int canvasY = y + view.y;
            if (bubbleBounds != null && bubbleBounds.contains(canvasX, canvasY)) {
                return true;
            }
            if (milu != null) {
                return miluSpriteHit(canvasX, canvasY, animation);
            }
            return legacySpriteHit(canvasX, canvasY);
        }

        private boolean legacySpriteHit(int x, int y) {
            int size = sprite.frameWidth() * SCALE;
            int spriteX = (WIDTH - size) / 2;
            int spriteY = HEIGHT - size - 12 + animation.bob();
            int pad = 6;
            int left = spriteX - pad;
            int right = spriteX + size + pad;
            int top = Math.max(0, spriteY - 16);
            int bottom = spriteY + size + 16;
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                BufferedImage frame = renderLiveFrame();
                boolean pixelFallback = milu == null && miluFailed;
                if (perPixel) {
                    g.setComposite(AlphaComposite.Src);
                    DesktopPetMiluLayout.drawLiveFrame(g, frame, view.width, view.height, pixelFallback);
                } else {
                    g.setComposite(AlphaComposite.Src);
                    g.setColor(CandyTheme.NIGHT);
                    g.fillRoundRect(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()), 16, 16);
                    g.setComposite(AlphaComposite.SrcOver);
                    DesktopPetMiluLayout.drawLiveFrame(g, frame, view.width, view.height, pixelFallback);
                }
                if (control.isActive()) {
                    g.setComposite(AlphaComposite.SrcOver);
                    paintControlHint(g);
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * 依裝置縮放畫完整隻。拖曳時的旋轉也做在緩衝上，再依螢幕變換貼回視窗。
         */
        private BufferedImage renderLiveFrame() {
            double pixelScale = DesktopPetMiluLayout.deviceScale(getGraphicsConfiguration());
            if (milu == null) {
                int w = Math.max(1, view.width);
                int h = Math.max(1, view.height);
                int bw = DesktopPetMiluLayout.bufferPixelWidth(w, pixelScale);
                int bh = DesktopPetMiluLayout.bufferPixelHeight(h, pixelScale);
                BufferedImage image = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
                Graphics2D canvas = image.createGraphics();
                try {
                    if (pixelScale > 1.0001) {
                        canvas.scale(pixelScale, pixelScale);
                    }
                    canvas.setComposite(AlphaComposite.Src);
                    canvas.setColor(new Color(0, 0, 0, 0));
                    canvas.fillRect(0, 0, w, h);
                    canvas.setComposite(AlphaComposite.SrcOver);
                    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    canvas.translate(-view.x, -view.y);
                    if (miluFailed) {
                        paintShadow(canvas);
                        paintSprite(canvas);
                    }
                    paintBubble(canvas);
                } finally {
                    canvas.dispose();
                }
                return image;
            }
            double radians = 0;
            double scale = 1;
            double pivotX = 0;
            double pivotY = 0;
            if (grabInStage != null) {
                radians = Math.toRadians(animation.leanDegrees());
                if (Math.abs(radians) >= 1e-4) {
                    int size = DesktopPetSpriteLoader.DISPLAY;
                    int spriteX = (WIDTH - size) / 2;
                    int spriteY = BUBBLE_SLOT;
                    pivotX = grabInStage.x + view.x;
                    pivotY = grabInStage.y + view.y;
                    scale = fitScale(
                            spriteX - view.x, spriteY - view.y, size,
                            grabInStage.x, grabInStage.y, radians,
                            Math.max(1, view.width), Math.max(1, view.height), 2);
                }
            }
            int w = Math.max(1, view.width);
            int h = Math.max(1, view.height);
            int bw = DesktopPetMiluLayout.bufferPixelWidth(w, pixelScale);
            int bh = DesktopPetMiluLayout.bufferPixelHeight(h, pixelScale);
            if (liveFrameBuffer == null
                    || liveFrameBuffer.getWidth() != bw
                    || liveFrameBuffer.getHeight() != bh) {
                liveFrameBuffer = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
            }
            paintMiluViewport(liveFrameBuffer, view, milu, animation, pivotX, pivotY, radians, scale, pixelScale);
            BufferedImage image = liveFrameBuffer;
            Graphics2D extra = image.createGraphics();
            try {
                extra.translate(-view.x, -view.y);
                if (pixelScale > 1.0001) {
                    extra.scale(pixelScale, pixelScale);
                }
                paintBubble(extra);
                if (debug) {
                    paintDebug(extra);
                }
                if (Math.abs(radians) >= 1e-4) {
                    // 拖曳時外圈留白。貼著外框的影子像素會讓視窗邊看起來被切了一刀。
                    extra.setTransform(new java.awt.geom.AffineTransform());
                    extra.setComposite(AlphaComposite.Src);
                    extra.setColor(new Color(0, 0, 0, 0));
                    int margin = 2;
                    extra.fillRect(0, 0, image.getWidth(), margin);
                    extra.fillRect(0, image.getHeight() - margin, image.getWidth(), margin);
                    extra.fillRect(0, 0, margin, image.getHeight());
                    extra.fillRect(image.getWidth() - margin, 0, margin, image.getHeight());
                }
            } finally {
                extra.dispose();
            }
            return image;
        }

        private void paintShadow(Graphics2D g) {
            if (milu != null) {
                paintMiluShadow(g, WIDTH);
                return;
            }
            int sw = sprite.frameWidth() * SCALE;
            int x = (WIDTH - sw) / 2;
            int y = HEIGHT - 18;
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(x + sw / 5, y, Math.max(12, sw - sw / 3), 10);
        }

        private void paintSprite(Graphics2D g) {
            Graphics2D layer = tilted(g);
            try {
                if (milu != null) {
                    int x = (WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2;
                    DesktopPetSpriteLoader.Sprite spriteFrame = milu.spriteFor(animation);
                    // 左右走路各用自己的影格，不在這裡鏡像。錨點修正只挪圖片，不改視窗座標。
                    DesktopPetSpriteLoader.draw(
                            layer,
                            spriteFrame.image,
                            x,
                            BUBBLE_SLOT,
                            DesktopPetSpriteLoader.DISPLAY,
                            spriteFrame.offsetX,
                            spriteFrame.offsetY);
                    return;
                }
                int sw = sprite.frameWidth() * SCALE;
                int sh = sprite.frameHeight() * SCALE;
                int x = (WIDTH - sw) / 2;
                int y = HEIGHT - sh - 12 + animation.bob();
                if (animation.facingRight()) {
                    sprite.draw(layer, animation.column(), animation.row(), x, y, SCALE);
                } else {
                    layer.translate(x + sw, y);
                    layer.scale(-1, 1);
                    sprite.draw(layer, animation.column(), animation.row(), 0, 0, SCALE);
                }
            } finally {
                if (layer != g) {
                    layer.dispose();
                }
            }
        }

        /** 只轉角色圖層。影子和泡泡留在地面。繞抓取點轉，游標底下的點不會跳。 */
        private Graphics2D tilted(Graphics2D g) {
            double radians = Math.toRadians(animation.leanDegrees());
            if (grabInStage == null || Math.abs(radians) < 1e-4) {
                return g;
            }
            int size = milu != null
                    ? DesktopPetSpriteLoader.DISPLAY
                    : sprite.frameWidth() * SCALE;
            int spriteX = (WIDTH - size) / 2;
            int spriteY = milu != null ? BUBBLE_SLOT : HEIGHT - size - 12 + animation.bob();
            int pivotX = grabInStage.x + view.x;
            int pivotY = grabInStage.y + view.y;
            double scale = fitScale(
                    spriteX - view.x, spriteY - view.y, size,
                    grabInStage.x, grabInStage.y, radians,
                    Math.max(1, view.width), Math.max(1, view.height), 2);
            Graphics2D layer = (Graphics2D) g.create();
            layer.translate(pivotX, pivotY);
            layer.rotate(radians);
            layer.scale(scale, scale);
            layer.translate(-pivotX, -pivotY);
            return layer;
        }

        private void paintBubble(Graphics2D g) {
            bubbleBounds =
                    paintSpeechBubble(g, view, milu, animation, animation.bubble());
        }

        private void paintControlHint(Graphics2D g) {
            String text = control.hintText(controlWindowFocused);
            if (text == null) {
                return;
            }
            g.setFont(UiFonts.chinesePlain(11));
            FontMetrics metrics = g.getFontMetrics();
            int pad = 6;
            int boxW = metrics.stringWidth(text) + pad * 2;
            int boxH = metrics.getHeight() + 4;
            int bx = getWidth() - boxW - 6;
            int by = 6;
            g.setColor(new Color(20, 16, 28, 200));
            g.fillRoundRect(bx, by, boxW, boxH, 8, 8);
            g.setColor(CandyTheme.PINK_HI);
            g.drawRoundRect(bx, by, boxW, boxH, 8, 8);
            g.setColor(CandyTheme.PINK_INK);
            g.drawString(text, bx + pad, by + metrics.getAscent() + 2);
        }

        private void paintDebug(Graphics2D g) {
            DesktopPetSpriteLoader.Sprite placed = milu.spriteFor(animation);
            int centerX = (WIDTH - DesktopPetSpriteLoader.DISPLAY) / 2
                    + (int) Math.round(DesktopPetSpriteLoader.ANCHOR_X * DesktopPetSpriteLoader.SCALE);
            int groundY = BUBBLE_SLOT
                    + (int) Math.round(DesktopPetSpriteLoader.ANCHOR_Y * DesktopPetSpriteLoader.SCALE);
            g.setColor(new Color(255, 64, 96, 220));
            g.drawLine(centerX, 0, centerX, getHeight());
            g.drawLine(0, groundY, getWidth(), groundY);
            g.fillRect(2, 2, 3, 3);
            String mode = animation.isCarried() ? carryName(animation.carryPhase()) : modeName(animation.mode());
            String text = mode + "  " + String.format(java.util.Locale.ROOT, "%+.1f°", animation.leanDegrees())
                    + "  " + placed.name
                    + "  offset " + placed.offsetX + "," + placed.offsetY
                    + "  視窗 " + DesktopPetWindow.this.getX() + "," + DesktopPetWindow.this.getY();
            g.setFont(UiFonts.chinesePlain(11));
            FontMetrics metrics = g.getFontMetrics();
            int boxW = Math.min(getWidth() - 8, metrics.stringWidth(text) + 8);
            g.setColor(new Color(20, 16, 28, 210));
            g.fillRoundRect(4, 8, boxW, metrics.getHeight() + 4, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString(text, 8, 8 + metrics.getAscent() + 1);
        }
    }

    private static String carryName(DesktopPetAnimation.CarryPhase phase) {
        if (phase == DesktopPetAnimation.CarryPhase.GRAB) {
            return "抓起";
        }
        if (phase == DesktopPetAnimation.CarryPhase.RELEASE) {
            return "放下";
        }
        return "掙扎";
    }

    private static String modeName(DesktopPetAnimation.Mode mode) {
        if (mode == DesktopPetAnimation.Mode.WALK) {
            return "走路";
        }
        if (mode == DesktopPetAnimation.Mode.REACT) {
            return "揮手";
        }
        if (mode == DesktopPetAnimation.Mode.THINK) {
            return "思考";
        }
        if (mode == DesktopPetAnimation.Mode.BUSY) {
            return "忙碌";
        }
        if (mode == DesktopPetAnimation.Mode.WORRY) {
            return "困擾";
        }
        if (mode == DesktopPetAnimation.Mode.HUNGRY) {
            return "餓肚子";
        }
        if (mode == DesktopPetAnimation.Mode.SIT) {
            return "坐下";
        }
        if (mode == DesktopPetAnimation.Mode.SLEEP) {
            return "睡覺";
        }
        return "待機";
    }
}
