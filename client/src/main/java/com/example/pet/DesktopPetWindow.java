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
    /** 左右差幾像素以內不重縮視窗，避免呼吸影格帶動座標。 */
    /**
     * 抓起時角色會略為升高。這段不把視窗往上長，否則拖曳中的座標會跳；
     * 多出來的幾像素裁在視窗外，待機頭頂仍貼著上緣。
     */

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
            setType(backgroundWindowType());
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
        // per-pixel 透明時與 root／layered pane 一致關掉雙緩衝，避免先露出被清空的原生面再貼幀。
        stage.setDoubleBuffered(!perPixel);
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
        applyControlModeWindowChrome(true);
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
        applyControlModeWindowChrome(false);
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
        applyControlModeWindowChrome(true);
        if (keepsAppInBackground()) {
            setAutoRequestFocus(true);
        }
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
        return DesktopPetScreenPlacement.usable(gc);
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
        flushStagePaint();
    }

    /**
     * per-pixel 透明視窗在 macOS 上非同步 {@link #repaint()} 容易露出一幀清空的原生面；
     * 計時器路徑改為同 tick 內同步畫滿 stage。
     */
    private void flushStagePaint() {
        if (perPixel && isDisplayable() && isVisible()) {
            int w = stage.getWidth();
            int h = stage.getHeight();
            if (w > 0 && h > 0) {
                stage.paintImmediately(0, 0, w, h);
                return;
            }
        }
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
    static double fitScale(int spriteX, int spriteY, int spriteSize,
            int pivotX, int pivotY, double radians,
            int canvasWidth, int canvasHeight, int margin) {
        return DesktopPetCarryFit.fitScale(spriteX, spriteY, spriteSize, pivotX, pivotY, radians,
                canvasWidth, canvasHeight, margin);
    }

    private void applyControlModeWindowChrome(boolean controlActive) {
        DesktopPetWindowChrome.ControlModeChrome chrome = DesktopPetWindowChrome.controlModeChrome(controlActive);
        setFocusableWindowState(chrome.focusableWindowState);
        setAutoRequestFocus(chrome.autoRequestFocus);
        stage.setFocusable(chrome.stageFocusable);
        adoptWindowType(chrome.windowType);
    }

    private void adoptWindowType(Type target) {
        if (getType() == target) {
            return;
        }
        boolean visible = isVisible();
        Point loc = new Point(getLocation());
        if (visible) {
            setVisible(false);
        }
        try {
            setType(target);
        } catch (RuntimeException ex) {
            // 不支援改 type 時維持原狀。
        }
        setLocation(loc);
        if (visible) {
            setVisible(true);
        }
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
        return DesktopPetWindowChrome.keepsAppInBackground();
    }

    static Type backgroundWindowType() {
        return DesktopPetWindowChrome.backgroundWindowType();
    }

    static Type controlModeWindowType() {
        return DesktopPetWindowChrome.controlModeWindowType();
    }

    static ControlModeChrome controlModeChrome(boolean controlActive) {
        DesktopPetWindowChrome.ControlModeChrome chrome = DesktopPetWindowChrome.controlModeChrome(controlActive);
        return new ControlModeChrome(chrome.windowType, chrome.focusableWindowState,
                chrome.autoRequestFocus, chrome.stageFocusable);
    }

    static final class ControlModeChrome {
        final Type windowType;
        final boolean focusableWindowState;
        final boolean autoRequestFocus;
        final boolean stageFocusable;

        ControlModeChrome(Type windowType, boolean focusableWindowState, boolean autoRequestFocus,
                boolean stageFocusable) {
            this.windowType = windowType;
            this.focusableWindowState = focusableWindowState;
            this.autoRequestFocus = autoRequestFocus;
            this.stageFocusable = stageFocusable;
        }
    }

    static void prepareMenuWindowTransparency(JWindow window) {
        DesktopPetWindowChrome.prepareMenuWindowTransparency(window);
    }

    private static Rectangle[] screenRects() {
        return DesktopPetScreenPlacement.screenRects();
    }

    static Point defaultSpot(Rectangle usable, int windowWidth, int windowHeight) {
        return DesktopPetScreenPlacement.defaultSpot(usable, windowWidth, windowHeight);
    }

    static Point clampDragToScreens(int x, int y, int windowWidth, int windowHeight, Rectangle[] screens) {
        return DesktopPetScreenPlacement.clampDragToScreens(x, y, windowWidth, windowHeight, screens);
    }

    static Point clampDragToScreens(
            int x, int y, int windowWidth, int windowHeight, int visibleTop, Rectangle[] screens) {
        return DesktopPetScreenPlacement.clampDragToScreens(x, y, windowWidth, windowHeight, visibleTop, screens);
    }

    static Point clampToScreens(int x, int y, int windowWidth, int windowHeight, Rectangle[] screens) {
        return DesktopPetScreenPlacement.clampToScreens(x, y, windowWidth, windowHeight, screens);
    }

    static final int MAIN_AVOID_GAP = DesktopPetScreenPlacement.MAIN_AVOID_GAP;

    static Point nudgeOutsideMainWindow(
            int x, int y, int windowWidth, int windowHeight,
            Rectangle main, Rectangle usable, Rectangle[] screens) {
        return DesktopPetScreenPlacement.nudgeOutsideMainWindow(
                x, y, windowWidth, windowHeight, main, usable, screens);
    }

    static HorizontalStep stepHorizontal(int x, int dx, int minX, int maxX) {
        DesktopPetScreenPlacement.HorizontalStep step =
                DesktopPetScreenPlacement.stepHorizontal(x, dx, minX, maxX);
        return new HorizontalStep(step.x, step.bounce);
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

    private Rectangle pixelBody() {
        int sw = Math.max(1, sprite.frameWidth() * SCALE);
        int sh = Math.max(1, sprite.frameHeight() * SCALE);
        int x = Math.max(0, (WIDTH - sw) / 2);
        int y = Math.max(0, HEIGHT - sh - 16);
        Rectangle rect = new Rectangle(x, y, Math.min(sw, WIDTH - x), Math.min(sh + 14, HEIGHT - y));
        Rectangle clip = rect.intersection(new Rectangle(0, 0, WIDTH, HEIGHT));
        if (clip.width < 1 || clip.height < 1) {
            return new Rectangle(0, 0, WIDTH, HEIGHT);
        }
        return clip;
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
            DesktopPetSpriteLoader.Frames milu, Rectangle fittedShell, DesktopPetAnimation animation) {
        return DesktopPetViewportRenderer.viewForPose(milu, fittedShell, animation);
    }

    static boolean hitMiluAtStage(Rectangle view, DesktopPetAnimation animation, int stageX, int stageY) {
        return DesktopPetViewportRenderer.hitMiluAtStage(view, animation, stageX, stageY);
    }

    static Rectangle shellForViewport(DesktopPetSpriteLoader.Frames milu, DesktopPetAnimation animation) {
        return DesktopPetViewportRenderer.shellForViewport(milu, animation);
    }

    static BufferedImage paintMiluViewport(Rectangle view, DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation, boolean drawViewportBorder) {
        return DesktopPetViewportRenderer.paintMiluViewport(view, milu, animation, drawViewportBorder);
    }

    static BufferedImage paintMiluViewport(Rectangle view, DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation, boolean drawViewportBorder,
            double pivotX, double pivotY, double radians, double scale) {
        return DesktopPetViewportRenderer.paintMiluViewport(
                view, milu, animation, drawViewportBorder, pivotX, pivotY, radians, scale);
    }

    static void paintMiluViewport(BufferedImage target, Rectangle view, DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation, double pivotX, double pivotY, double radians, double scale) {
        DesktopPetViewportRenderer.paintMiluViewport(target, view, milu, animation, pivotX, pivotY, radians, scale);
    }

    static void paintMiluViewport(BufferedImage target, Rectangle view, DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation, double pivotX, double pivotY, double radians, double scale,
            double pixelScale) {
        DesktopPetViewportRenderer.paintMiluViewport(
                target, view, milu, animation, pivotX, pivotY, radians, scale, pixelScale);
    }

    static void assertOpaqueWithinViewport(BufferedImage viewport, int threshold) {
        DesktopPetViewportRenderer.assertOpaqueWithinViewport(viewport, threshold);
    }

    static Rectangle shellFor(DesktopPetSpriteLoader.Frames frames) {
        return DesktopPetViewportRenderer.shellFor(frames);
    }

    static Rectangle estimatedMiluShell() {
        return DesktopPetViewportRenderer.estimatedMiluShell();
    }

    static Rectangle stableShellFor(DesktopPetSpriteLoader.Frames frames) {
        return DesktopPetViewportRenderer.stableShellFor(frames);
    }

    static boolean poseFitsShell(Rectangle shell, Rectangle raw) {
        return DesktopPetViewportRenderer.poseFitsShell(shell, raw);
    }

    static Rectangle miluBodyOnCanvas(BufferedImage image, int offsetX, int offsetY) {
        return DesktopPetViewportRenderer.miluBodyOnCanvas(image, offsetX, offsetY);
    }

    static Rectangle paintSpeechBubble(Graphics2D g, Rectangle view, DesktopPetSpriteLoader.Frames milu,
            DesktopPetAnimation animation, String text) {
        return DesktopPetViewportRenderer.paintSpeechBubble(g, view, milu, animation, text);
    }

    static void paintMiluShadow(Graphics2D g, int canvasWidth) {
        DesktopPetViewportRenderer.paintMiluShadow(g, canvasWidth);
    }

    static Rectangle opaqueBounds(BufferedImage image, int threshold) {
        return DesktopPetViewportRenderer.opaqueBounds(image, threshold);
    }

    static int visibleTopInset(BufferedImage image) {
        return DesktopPetViewportRenderer.visibleTopInset(image);
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
                return hitMiluAtStage(view, animation, x, y);
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
        public void update(Graphics graphics) {
            paint(graphics);
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
