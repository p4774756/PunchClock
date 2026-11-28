package com.example.ui.hud;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.service.SchedulerService;
import com.example.ui.AppBranding;
import com.example.ui.CandyButton;
import com.example.ui.CandyTheme;
import com.example.ui.ClassicBevel;
import com.example.ui.MiniBarDrag;
import com.example.ui.UiFonts;
import com.example.ui.WindowChrome;

import javax.imageio.ImageIO;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 小方塊外框：底部分頁圖示、首頁角色與實用泡泡（#274）。
 */
public final class AnimeShell extends JPanel {

    private static final Color INK = new Color(255, 252, 255);
    private static final Color MUTED = new Color(196, 186, 220);
    private static final Color NAV_INK = new Color(32, 32, 32);
    private static final Color NAV_INK_MUTED = new Color(64, 64, 64);
    private static final String[] NAV = {"首頁", "排程", "好友傳檔", "日誌", "設定"};
    private static final String[] NAV_TIPS = {
            "首頁：小寶貝與狀態泡泡",
            "排程任務與打卡紀錄",
            "好友、聊天與傳檔",
            "系統日誌",
            "連線、外觀與關於"
    };
    /** 分頁：首頁、排程0、好友2、日誌3、設定1 */
    private static final int[] NAV_TO_TAB = {-1, 0, 2, 3, 1};
    public static final int CHARACTER_HIT = 20;

    private final JTabbedPane tabs;
    private final SchedulerService scheduler;
    private final BabyAssets baby = BabyAssets.load();
    private final HomeBoard board = new HomeBoard();
    private final Map<HomeIllustration, BufferedImage> homeIllustrations = loadHomeIllustrations();
    private final Timer timer;
    private HomeIllustration homeIllustration = HomeIllustration.ROOM_B;
    private final boolean reducedMotion = reducedMotion();
    private final Map<String, TaskStatus> seenTasks = new HashMap<>();
    private HomeActions homeActions;
    private boolean holdingHome = true;
    private boolean useRoom = true;
    private boolean miniMode;
    private int hot = -1;
    private String eventFlash = "";
    private long eventUntil;
    private java.awt.Rectangle figureBox = new java.awt.Rectangle();
    private long paintedSecond = -1;
    private String paintedHomeKey;
    private Point miniPressScreen;
    private Rectangle miniStartBounds;
    private boolean miniDragging;
    private int friendNavUnread;
    private boolean checkInUiVisible = true;
    private final JButton swapIllustrationButton;
    private Runnable swapIllustrationHandler;

    public AnimeShell(SchedulerService scheduler, JTabbedPane tabs) {
        super(null);
        this.scheduler = scheduler;
        this.tabs = tabs;
        setOpaque(true);
        board.setReducedMotion(reducedMotion);
        tabs.setVisible(false);
        add(tabs);
        swapIllustrationButton = new CandyButton("換圖", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        swapIllustrationButton.setFont(UiFonts.chinesePlain(12));
        swapIllustrationButton.setToolTipText("輪換首頁插畫");
        swapIllustrationButton.addActionListener(e -> {
            if (swapIllustrationHandler != null) {
                swapIllustrationHandler.run();
            }
        });
        add(swapIllustrationButton);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int id = hit(e.getX(), e.getY());
                if (hot != id) {
                    int previous = hot;
                    hot = id;
                    setCursor(id >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaintHot(previous, id);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hot < 0) {
                    return;
                }
                int previous = hot;
                hot = -1;
                setCursor(Cursor.getDefaultCursor());
                repaintHot(previous, -1);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (miniMode && SwingUtilities.isLeftMouseButton(e)) {
                    miniDragging = false;
                    miniPressScreen = e.getLocationOnScreen();
                    java.awt.Window window = SwingUtilities.getWindowAncestor(AnimeShell.this);
                    miniStartBounds = window == null ? null : window.getBounds();
                    return;
                }
                activate(hit(e.getX(), e.getY()));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!miniMode || !SwingUtilities.isLeftMouseButton(e) || miniPressScreen == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                if (!miniDragging && MiniBarDrag.isClick(miniPressScreen, now)) {
                    activate(-2);
                }
                miniDragging = false;
                miniPressScreen = null;
                miniStartBounds = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!miniMode || miniPressScreen == null || miniStartBounds == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int dx = now.x - miniPressScreen.x;
                int dy = now.y - miniPressScreen.y;
                if (!miniDragging) {
                    if (MiniBarDrag.isClick(dx, dy)) {
                        return;
                    }
                    miniDragging = true;
                }
                java.awt.Window window = SwingUtilities.getWindowAncestor(AnimeShell.this);
                if (!(window instanceof JFrame)) {
                    return;
                }
                JFrame frame = (JFrame) window;
                int x = miniStartBounds.x + dx;
                int y = miniStartBounds.y + dy;
                Point clamped = MiniBarDrag.clampLocation(
                        x, y, miniStartBounds.width, miniStartBounds.height, screenRects());
                frame.setLocation(clamped);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setToolTipText(navTooltip(0));
        timer = new Timer(40, e -> {
            noticeStatus();
            long second = System.currentTimeMillis() / 1000L;
            if (holdingHome && !miniMode) {
                if (second != paintedSecond) {
                    paintedSecond = second;
                    String key = homeActions == null ? "" : homeActions.paintKey();
                    if (!key.equals(paintedHomeKey)) {
                        paintedHomeKey = key;
                        refreshBubble();
                        repaint(contentBounds());
                    }
                }
                if (board.animating()) {
                    repaint(board.bubbleArea());
                }
            } else if (miniMode && second != paintedSecond) {
                paintedSecond = second;
                repaint();
            }
        });
    }

    private void repaintHot(int previous, int next) {
        repaint(hotArea(previous));
        repaint(hotArea(next));
    }

    private java.awt.Rectangle hotArea(int id) {
        if (id >= 0 && id < NAV.length) {
            return tabHitBox(id);
        }
        if (id == CHARACTER_HIT || id == HomeBoard.BUBBLE) {
            java.awt.Rectangle box = board.hotArea(id);
            if (box.width > 0) {
                box.grow(4, 4);
                return box;
            }
            if (figureBox.width > 0) {
                return new java.awt.Rectangle(figureBox);
            }
        }
        return new java.awt.Rectangle();
    }

    public boolean isHoldingHome() {
        return holdingHome;
    }

    public boolean isMiniMode() {
        return miniMode;
    }

    public void setMiniMode(boolean miniMode) {
        this.miniMode = miniMode;
        tabs.setVisible(!miniMode && !holdingHome);
        swapIllustrationButton.setVisible(!miniMode && holdingHome);
        layoutSwapIllustrationButton();
        revalidate();
        repaint();
        refreshChrome();
    }

    public void setCharacterTheme(CharacterTheme theme) {
        revalidate();
        repaint();
        if (holdingHome) {
            refreshChrome();
        }
    }

    public void setFriendNavUnread(int count) {
        int next = Math.max(0, count);
        if (next > friendNavUnread) {
            board.sayUnread(HomeMoodPool.pickUnread(-1));
            repaint(board.bubbleArea());
        }
        if (friendNavUnread == next) {
            return;
        }
        friendNavUnread = next;
        int slot = navSlotForId(2);
        if (slot >= 0) {
            repaint(tabHitBox(slot));
        }
    }

    public void flashEvent(String text, long millis) {
        eventFlash = text == null ? "" : text;
        eventUntil = System.currentTimeMillis() + Math.max(1200L, millis);
        refreshBubble();
        repaint(contentBounds());
    }

    public void showHome() {
        holdingHome = true;
        tabs.setVisible(false);
        swapIllustrationButton.setVisible(!miniMode);
        layoutSwapIllustrationButton();
        revalidate();
        repaint();
        refreshChrome();
    }

    public void openTab(int index) {
        if (!checkInUiVisible && index == 0) {
            showHome();
            return;
        }
        holdingHome = false;
        if (index >= 0 && index < tabs.getTabCount() && tabs.getSelectedIndex() != index) {
            tabs.setSelectedIndex(index);
        }
        tabs.setVisible(!miniMode);
        swapIllustrationButton.setVisible(false);
        revalidate();
        repaint();
        refreshChrome();
    }

    void releaseHomeFigure() {
        figureBox = new java.awt.Rectangle();
    }

    public java.awt.Rectangle homeStageBounds() {
        return new java.awt.Rectangle(board.stage);
    }

    public java.awt.Rectangle homeReminderBounds() {
        return new java.awt.Rectangle();
    }

    public void prepareHomeLayout() {
        if (holdingHome) {
            board.layout(contentBounds());
            figureBox = new java.awt.Rectangle();
        }
    }

    public void greet() {
        board.sayClick();
        repaint(board.bubbleArea());
    }

    private void refreshChrome() {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            WindowChrome.relayout((JFrame) window);
        }
    }

    private int navIndexForTab(int tab) {
        for (int i = 0; i < NAV_TO_TAB.length; i++) {
            if (NAV_TO_TAB[i] == tab) {
                return navSlotForId(i);
            }
        }
        return 0;
    }

    public void setCheckInUiVisible(boolean visible) {
        if (checkInUiVisible == visible) {
            return;
        }
        checkInUiVisible = visible;
        if (!visible && !holdingHome && tabs.getSelectedIndex() == 0) {
            showHome();
        }
        refreshBubble();
        revalidate();
        repaint();
        refreshChrome();
    }

    boolean checkInUiVisibleForTests() {
        return checkInUiVisible;
    }

    int visibleNavSlotCountForTests() {
        return navSlotCount();
    }

    int navIdForSlotForTests(int slot) {
        return navIdForSlot(slot);
    }

    private int navSlotCount() {
        return checkInUiVisible ? NAV.length : NAV.length - 1;
    }

    private int navIdForSlot(int slot) {
        if (checkInUiVisible || slot < 1) {
            return slot;
        }
        return slot + 1;
    }

    private int navSlotForId(int id) {
        if (checkInUiVisible || id <= 0) {
            return id;
        }
        if (id == 1) {
            return -1;
        }
        return id - 1;
    }

    public void followTab() {
        if (holdingHome) {
            return;
        }
        tabs.setVisible(!miniMode);
        revalidate();
        repaint();
        refreshChrome();
    }

    public void setUseRoom(boolean useRoom) {
        this.useRoom = useRoom;
        setOpaque(useRoom);
        repaint();
    }

    /** 舊版側欄立繪動畫；小寶貝版為 no-op。 */
    public void play(int frame) {
    }

    public void setHomeActions(HomeActions actions) {
        this.homeActions = actions;
        wireMoodPickers();
        refreshBubble();
        repaint();
    }

    private void wireMoodPickers() {
        if (homeActions == null) {
            board.setMoodPicker(i -> "", () -> HomeMoodPool.Band.MORNING);
            board.setClickPicker(HomeMoodPool::pickClick);
            return;
        }
        board.setMoodPicker(
                i -> HomeMoodPool.moodAt(homeActions.moodBand(), i),
                homeActions::moodBand);
        board.setClickPicker(i -> HomeMoodPool.clickAt(i));
    }

    public interface HomeActions {
        HomeBrief read();

        String moodLine();

        HomeMoodPool.Band moodBand();

        List<CheckInTask> tasks();

        void openLog();

        void runTask();

        void openChat();

        void openRecentFriend();

        void sendFile();

        String paintKey();
    }

    public static void hideTabStrip(JTabbedPane tabs) {
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override
            protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
                return 0;
            }

            @Override
            protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
            }

            @Override
            public int tabForCoordinate(JTabbedPane pane, int x, int y) {
                return pane.getSelectedIndex();
            }
        });
        tabs.setFocusable(false);
        tabs.setRequestFocusEnabled(false);
        disableTabPaneKeyboard(tabs);
    }

    static void disableTabPaneKeyboard(JTabbedPane tabs) {
        for (int condition : new int[]{
                JComponent.WHEN_FOCUSED,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                JComponent.WHEN_IN_FOCUSED_WINDOW
        }) {
            InputMap map = tabs.getInputMap(condition);
            if (map != null) {
                map.clear();
            }
            ActionMap actions = tabs.getActionMap();
            if (actions != null && condition == JComponent.WHEN_FOCUSED) {
                actions.clear();
            }
        }
    }

    public void setSwapIllustrationHandler(Runnable handler) {
        swapIllustrationHandler = handler;
    }

    JButton swapIllustrationButtonForTests() {
        return swapIllustrationButton;
    }

    private void layoutSwapIllustrationButton() {
        if (!swapIllustrationButton.isVisible()) {
            return;
        }
        java.awt.Dimension size = swapIllustrationButton.getPreferredSize();
        int margin = 10;
        int x = Math.max(margin, getWidth() - size.width - margin);
        int y = margin;
        swapIllustrationButton.setBounds(x, y, size.width, size.height);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (event == null) {
            return super.getToolTipText();
        }
        if (miniMode) {
            return "點一下展開";
        }
        int id = hit(event.getX(), event.getY());
        if (id >= 0 && id < NAV.length) {
            return navTooltip(id);
        }
        return null;
    }

    @Override
    public void doLayout() {
        java.awt.Rectangle content = contentBounds();
        tabs.setBounds(content);
        layoutSwapIllustrationButton();
    }

    private void activate(int id) {
        if (miniMode) {
            setMiniMode(false);
            if (onMiniExpand != null) {
                onMiniExpand.run();
            }
            return;
        }
        if (id >= 0 && id < NAV.length) {
            int tab = NAV_TO_TAB[id];
            if (tab < 0) {
                showHome();
            } else {
                openTab(tab);
            }
            return;
        }
        if (id == CHARACTER_HIT) {
            greet();
            return;
        }
        if (id == HomeBoard.BUBBLE) {
            if (board.bubbleOpensLog()) {
                if (homeActions != null) {
                    homeActions.openLog();
                } else {
                    openTab(3);
                }
            }
            greet();
            return;
        }
        if (homeActions == null) {
            return;
        }
    }

    private Runnable onMiniExpand;

    public void setMiniModeHandler(Runnable handler) {
        onMiniExpand = handler;
    }

    public void setHomeIllustration(HomeIllustration illustration) {
        HomeIllustration next = illustration == null ? HomeIllustration.ROOM_B : illustration;
        if (next == homeIllustration) {
            return;
        }
        homeIllustration = next;
        repaint(contentBounds());
    }

    HomeIllustration homeIllustrationForTests() {
        return homeIllustration;
    }

    private void noticeStatus() {
        Set<String> live = new HashSet<>();
        for (CheckInTask task : taskList()) {
            if (task.getId() == null || task.getStatus() == null) {
                continue;
            }
            live.add(task.getId());
            TaskStatus status = task.getStatus();
            TaskStatus previous = seenTasks.put(task.getId(), status);
            if (previous == null || previous == status) {
                continue;
            }
            if (!checkInUiVisible) {
                continue;
            }
            if (status == TaskStatus.SUCCESS) {
                board.sayDone();
                repaint(board.bubbleArea());
            } else if (status == TaskStatus.FAILED) {
                board.sayTrouble();
                repaint(board.bubbleArea());
            }
        }
        seenTasks.keySet().retainAll(live);
        if (eventUntil > 0 && System.currentTimeMillis() > eventUntil) {
            eventUntil = 0;
            eventFlash = "";
            refreshBubble();
        }
    }

    private void refreshBubble() {
        HomeBrief brief = homeActions == null ? HomeBrief.empty() : homeActions.read();
        List<CheckInTask> tasks = homeActions == null ? List.of() : homeActions.tasks();
        String mood = homeActions == null ? "" : homeActions.moodLine();
        String flash = eventUntil > System.currentTimeMillis() ? eventFlash : "";
        board.setBubble(HomeBubbleFormat.resolve(brief, tasks, flash, mood, checkInUiVisible));
    }

    private int hit(int x, int y) {
        if (miniMode) {
            return -2;
        }
        for (int slot = 0; slot < navSlotCount(); slot++) {
            if (tabHitBox(slot).contains(x, y)) {
                return navIdForSlot(slot);
            }
        }
        if (holdingHome) {
            int bubble = board.hit(x, y);
            if (bubble >= 0) {
                return bubble;
            }
            if (figureBox.contains(x, y) || board.stage.contains(x, y)) {
                return CHARACTER_HIT;
            }
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (miniMode) {
            paintMini(g);
            g.dispose();
            return;
        }
        if (holdingHome) {
            paintRoom(g);
        } else {
            g.setColor(CandyTheme.PANEL);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        java.awt.Rectangle content = contentBounds();
        if (holdingHome) {
            board.layout(content);
            figureBox = new java.awt.Rectangle();
            paintHome(g, content);
        }
        paintTabBar(g);
        g.dispose();
    }

    private void paintHome(Graphics2D g, java.awt.Rectangle area) {
        BufferedImage background = homeIllustrations.get(homeIllustration);
        int imgW = background == null ? 1 : background.getWidth();
        int imgH = background == null ? 1 : background.getHeight();
        board.setIllustrationContext(
                homeIllustration, imgW, imgH, swapIllustrationButton.getBounds());
        board.paint(g, hot == HomeBoard.BUBBLE ? HomeBoard.BUBBLE : -1);
    }

    private void paintMini(Graphics2D g) {
        g.setColor(new Color(28, 20, 48));
        g.fillRect(0, 0, getWidth(), getHeight());
        int h = getHeight();
        int icon = Math.min(Math.max(8, h - 4), 44);
        int x = 6;
        int y = (h - icon) / 2;
        if (baby != null && baby.prone() != null) {
            BufferedImage img = baby.prone();
            int w = icon * img.getWidth() / Math.max(1, img.getHeight());
            w = Math.min(w, 72);
            g.drawImage(img, x, y, w, icon, null);
            x += w + 6;
        }
        int glyph = 10;
        int gy = (h - glyph) / 2;
        WindowChrome.paintTitleCollapseGlyph(g, x + 2, gy, glyph, true);
        x += glyph + 8;
        HomeBrief brief = homeActions == null ? HomeBrief.empty() : homeActions.read();
        boolean online = brief != null && brief.cloudOn;
        g.setColor(online ? new Color(117, 214, 162) : new Color(160, 148, 188));
        g.fillOval(x, h / 2 - 5, 10, 10);
        x += 16;
        g.setColor(INK);
        String line;
        List<CheckInTask> tasks = homeActions == null ? List.of() : homeActions.tasks();
        CheckInTask next = null;
        if (checkInUiVisible) {
            next = HomeBubbleFormat.resolve(brief, tasks, "", "", true).text.contains("失敗")
                    ? null : pickNextForMini(tasks);
        }
        if (next != null) {
            line = HomeBubbleFormat.formatNext(next);
        } else if (checkInUiVisible && brief != null && brief.nextName != null) {
            line = brief.nextTime + " " + brief.nextName;
        } else {
            line = AppBranding.DISPLAY_NAME + " · 點一下展開";
        }
        int max = Math.max(40, getWidth() - x - 6);
        int size = 12;
        g.setFont(UiFonts.chinesePlain(size));
        while (size > 10 && g.getFontMetrics().stringWidth(line) > max) {
            size--;
            g.setFont(UiFonts.chinesePlain(size));
        }
        String shown = g.getFontMetrics().stringWidth(line) <= max ? line : fit(g, line, max);
        g.drawString(shown, x, h / 2 + 5);
    }

    private static CheckInTask pickNextForMini(List<CheckInTask> tasks) {
        HomeBubbleFormat.BubbleLine b = HomeBubbleFormat.resolve(HomeBrief.empty(), tasks, "", "");
        if (b.text.isEmpty()) {
            return null;
        }
        if (tasks == null) {
            return null;
        }
        for (CheckInTask task : tasks) {
            if (task == null || task.getStatus() == null) {
                continue;
            }
            if (task.getStatus() == TaskStatus.SCHEDULED || task.getStatus() == TaskStatus.CHECKING_IN) {
                return task;
            }
        }
        return null;
    }

    private void paintTabBar(Graphics2D g) {
        java.awt.Rectangle bar = tabBarBounds();
        ClassicBevel.fillSunken(g, bar.x, bar.y, bar.width, bar.height, CandyTheme.PANEL);
        int selected = holdingHome ? 0 : navIndexForTab(tabs.getSelectedIndex());
        for (int slot = 0; slot < navSlotCount(); slot++) {
            int id = navIdForSlot(slot);
            paintNavSlot(g, tabHitBox(slot), TabNavIcon.navIconId(id), NAV[id], selected == slot, hot == id, false);
            if (id == 2 && friendNavUnread > 0) {
                paintNavUnreadBadge(g, tabHitBox(slot));
            }
        }
    }

    private void paintNavUnreadBadge(Graphics2D g, java.awt.Rectangle box) {
        int badge = 8;
        int x = box.x + box.width - badge - 6;
        int y = box.y + 6;
        g.setColor(new Color(180, 40, 40));
        g.fillOval(x, y, badge, badge);
        if (friendNavUnread > 1) {
            g.setColor(Color.WHITE);
            g.setFont(UiFonts.latinBold(8));
            String label = friendNavUnread > 9 ? "9+" : String.valueOf(friendNavUnread);
            java.awt.FontMetrics fm = g.getFontMetrics();
            int tx = x + (badge - fm.stringWidth(label)) / 2;
            int ty = y + (badge + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(label, tx, ty);
        }
    }

    private void paintNavSlot(
            Graphics2D g,
            java.awt.Rectangle box,
            String iconId,
            String label,
            boolean active,
            boolean hover,
            boolean miniSlot) {
        int slotX = box.x + 2;
        int slotY = box.y + 4;
        int slotW = box.width - 4;
        int slotH = box.height - 8;
        if (active) {
            ClassicBevel.fillRaised(g, slotX, slotY, slotW, slotH, CandyTheme.PANEL);
        } else if (hover) {
            g.setColor(new Color(208, 208, 208));
            g.fillRect(slotX, slotY, slotW, slotH);
        }
        Color labelInk = active ? NAV_INK : navLabelColor(iconId);
        g.setFont(UiFonts.chinesePlain(11));
        java.awt.FontMetrics labelMetrics = g.getFontMetrics();
        int iconSize = TabNavIcon.ICON_SIZE;
        int gap = TabNavIcon.ICON_GAP;
        int stack = iconSize + gap + labelMetrics.getHeight();
        int top = box.y + Math.max(6, (box.height - stack) / 2);
        int iconX = box.x + (box.width - iconSize) / 2;
        TabNavIcon.draw(g, iconId, iconX, top, iconSize, active);
        g.setColor(labelInk);
        int labelY = top + iconSize + gap + labelMetrics.getAscent();
        int lw = labelMetrics.stringWidth(label);
        g.drawString(label, box.x + (box.width - lw) / 2, labelY);
    }

    private java.awt.Rectangle tabBarBounds() {
        int h = ShellDimensions.TAB_BAR_HEIGHT;
        return new java.awt.Rectangle(0, Math.max(0, getHeight() - h), getWidth(), h);
    }

    private java.awt.Rectangle contentBounds() {
        java.awt.Rectangle bar = tabBarBounds();
        return new java.awt.Rectangle(0, 0, getWidth(), Math.max(0, bar.y));
    }

    java.awt.Rectangle tabHitBox(int slot) {
        java.awt.Rectangle bar = tabBarBounds();
        int slots = navSlotCount();
        int slotW = Math.max(36, bar.width / slots);
        int x = slot * slotW;
        return new java.awt.Rectangle(bar.x + x, bar.y, slotW, bar.height);
    }

    static int navCount() {
        return NAV.length;
    }

    static String navLabel(int index) {
        return NAV[index];
    }

    static Color navLabelColor(String iconId) {
        return iconId == null || iconId.isEmpty() ? NAV_INK_MUTED : NAV_INK_MUTED;
    }

    static String navTooltip(int index) {
        return index >= 0 && index < NAV_TIPS.length ? NAV_TIPS[index] : "";
    }

    static int tabForNav(int index) {
        return NAV_TO_TAB[index];
    }

    static java.awt.Rectangle navRowBounds(int shellWidth, int shellHeight, int index) {
        AnimeShell shell = new AnimeShell(null, new JTabbedPane());
        shell.setSize(shellWidth, shellHeight);
        return shell.tabHitBox(index);
    }

    public static BufferedImage renderSidebarPreview(int width, int height) {
        return renderShellPreview(width, height, false);
    }

    public static BufferedImage renderShellPreview(int width, int height, boolean mini) {
        return renderShellPreview(width, height, mini, 0);
    }

    /** headless 預覽；{@code navIndex} 為底欄高亮分頁（0=首頁）。 */
    public static BufferedImage renderShellPreview(int width, int height, boolean mini, int navIndex) {
        JTabbedPane pane = new JTabbedPane();
        for (int i = 0; i < 4; i++) {
            pane.addTab("", new JPanel());
        }
        AnimeShell shell = new AnimeShell(null, pane);
        shell.setSize(width, height);
        shell.doLayout();
        shell.setMiniMode(mini);
        if (navIndex <= 0) {
            shell.showHome();
        } else if (navIndex < NAV.length) {
            shell.openTab(tabForNav(navIndex));
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(28, 20, 48));
            g.fillRect(0, 0, width, height);
            shell.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private List<CheckInTask> taskList() {
        List<CheckInTask> list = new ArrayList<>();
        if (scheduler == null) {
            return list;
        }
        for (CheckInTask task : scheduler.getAllTasks()) {
            if (task != null) {
                list.add(task);
            }
        }
        list.sort(Comparator.comparing(CheckInTask::getActualTriggerTime, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private void paintRoom(Graphics2D g) {
        if (!useRoom) {
            return;
        }
        BufferedImage background = homeIllustrations.get(homeIllustration);
        if (background == null) {
            g.setColor(new Color(28, 20, 48));
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        java.awt.Rectangle content = contentBounds();
        int imgW = background.getWidth();
        int imgH = background.getHeight();
        java.awt.Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                content.width, content.height, imgW, imgH);
        int x = content.x + draw.x;
        int y = content.y + draw.y;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(background, x, y, draw.width, draw.height, null);
        g.setColor(new Color(16, 10, 32, 48));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private static Map<HomeIllustration, BufferedImage> loadHomeIllustrations() {
        Map<HomeIllustration, BufferedImage> images = new HashMap<>();
        for (HomeIllustration illustration : HomeIllustration.values()) {
            images.put(illustration, loadImage(illustration.resourcePath()));
        }
        return images;
    }

    private static BufferedImage loadImage(String resource) {
        try (InputStream in = AnimeShell.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }

    private static Rectangle[] screenRects() {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        Rectangle[] screens = new Rectangle[devices.length];
        for (int i = 0; i < devices.length; i++) {
            GraphicsConfiguration gc = devices[i].getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();
            java.awt.Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            screens[i] = new Rectangle(
                    bounds.x + insets.left,
                    bounds.y + insets.top,
                    Math.max(1, bounds.width - insets.left - insets.right),
                    Math.max(1, bounds.height - insets.top - insets.bottom));
        }
        return screens;
    }

    private static String fit(Graphics2D g, String text, int width) {
        if (text == null) {
            return "";
        }
        var metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && metrics.stringWidth(trimmed + "…") > width) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "…";
    }

    private static boolean reducedMotion() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                process.destroy();
                return false;
            }
            String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return "1".equals(text);
        } catch (Exception ex) {
            return false;
        }
    }

}