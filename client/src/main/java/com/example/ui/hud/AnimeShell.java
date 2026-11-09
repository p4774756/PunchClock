package com.example.ui.hud;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.service.SchedulerService;
import com.example.ui.UiFonts;
import com.example.ui.WindowChrome;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
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
 * 打卡程式的外框：房間背景、左側導覽、首頁角色。
 * 排程、好友、設定、日誌仍是原來的面板，只是分頁列收起來。
 */
public final class AnimeShell extends JPanel {

    private static final Color INK = new Color(255, 252, 255);
    private static final Color MUTED = new Color(226, 218, 242);
    private static final Color PANEL = new Color(54, 42, 96, 228);
    private static final Color PANEL_EDGE = new Color(214, 198, 255, 150);
    private static final String[] NAV = {"首頁", "排程任務", "好友與傳檔", "設定", "日誌"};
    /** 側邊順序和 JTabbedPane 不同：分頁是排程、設定、好友、日誌。 */
    private static final int[] NAV_TO_TAB = {-1, 0, 2, 1, 3};

    private final JTabbedPane tabs;
    private final SchedulerService scheduler;
    private final AnimeCast cast = AnimeCast.load();
    private final HeroMotion hero = new HeroMotion();
    private final HomeBoard board = new HomeBoard();
    private final BufferedImage room = loadRoom();
    private final Timer timer;
    private final boolean reducedMotion = reducedMotion();
    private final Map<String, TaskStatus> seenTasks = new HashMap<>();
    private CharacterTheme theme = CharacterTheme.SWEATER;
    /** 軌道版「下一個任務」：約 85% 不透明，文字／邊框另畫。 */
    private static final Color ORBITAL_CARD = new Color(42, 24, 78, 217);
    /** 腰部平切線局部遮罩（不透明，避免透出水平切口）。 */
    private static final Color ORBITAL_WAIST_MASK = new Color(42, 24, 78, 255);
    private HomeActions homeActions;
    private boolean holdingHome = true;
    private boolean useRoom = true;
    private int hot = -1;
    private java.awt.Rectangle bust = new java.awt.Rectangle();
    private java.awt.image.BufferedImage homeFigure;
    private java.awt.Rectangle homeFigureBounds;
    /** 軌道素材縮放快取：場景／全身／側欄半身都很大，重複雙線性縮放會讓 macOS 透明窗閃。 */
    private BufferedImage orbitalSceneLayer;
    private int orbitalSceneW = -1;
    private int orbitalSceneH = -1;
    private BufferedImage orbitalFigureLayer;
    private BufferedImage orbitalFigureSource;
    private int orbitalFigureW = -1;
    private int orbitalFigureH = -1;
    private BufferedImage orbitalBustLayer;
    private int orbitalBustW = -1;
    private int orbitalBustH = -1;
    private int paintedFrame = -1;
    private long paintedSecond = -1;
    private String paintedHomeKey;

    public AnimeShell(SchedulerService scheduler, JTabbedPane tabs) {
        super(null);
        this.scheduler = scheduler;
        this.tabs = tabs;
        setOpaque(true);
        board.setReducedMotion(reducedMotion);
        tabs.setVisible(false);
        add(tabs);
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
                activate(hit(e.getX(), e.getY()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        timer = new Timer(40, e -> {
            hero.tick(0.04);
            noticeStatus();
            int frame = hero.frame();
            long now = System.currentTimeMillis();
            long second = now / 1000L;
            if (holdingHome) {
                // 首頁每秒只檢查卡片資料；沒變就不重畫。台詞淡入淡出只畫泡泡那一帶。
                if (second != paintedSecond) {
                    paintedSecond = second;
                    String key = homeActions == null ? "" : homeActions.read().paintKey();
                    if (!key.equals(paintedHomeKey)) {
                        paintedHomeKey = key;
                        repaint(contentBounds());
                    }
                }
                if (board.animating()) {
                    repaint(board.bubbleArea());
                }
                return;
            }
            // 側欄表情只有毛衣女孩會眨眼；軌道版半身是靜態圖。
            if (frame != paintedFrame) {
                paintedFrame = frame;
                if (theme == CharacterTheme.ORBITAL) {
                    return;
                }
                if (bust.isEmpty()) {
                    repaint();
                } else {
                    repaint(bust);
                }
            }
        });
    }

    /** 熱區換了只重畫新舊兩格。macOS 透明窗整塊重繪太慢會閃。 */
    private void repaintHot(int previous, int next) {
        repaint(hotArea(previous));
        repaint(hotArea(next));
    }

    private java.awt.Rectangle hotArea(int id) {
        if (id >= 0 && id < NAV.length) {
            java.awt.Rectangle row = navRow(navBounds(), id);
            row.grow(2, 2);
            return row;
        }
        if (id == 20) {
            return holdingHome ? new java.awt.Rectangle() : new java.awt.Rectangle(bust);
        }
        java.awt.Rectangle box = board.hotArea(id);
        if (box.width > 0) {
            box.grow(2, 2);
        }
        return box;
    }

    public boolean isHoldingHome() {
        return holdingHome;
    }

    public void setCharacterTheme(CharacterTheme theme) {
        this.theme = theme == null ? CharacterTheme.SWEATER : theme;
        board.setReminderFill(this.theme == CharacterTheme.ORBITAL ? ORBITAL_CARD : null);
        clearOrbitalCaches();
        if (this.theme == CharacterTheme.ORBITAL) {
            releaseHomeFigure();
        }
        revalidate();
        repaint();
        // 非首頁不改窗框留白：設定頁切主題若 relayout，macOS 透明窗後續 hover 會一直閃。
        if (holdingHome) {
            refreshChrome();
        }
    }

    public void showHome() {
        holdingHome = true;
        tabs.setVisible(false);
        revalidate();
        repaint();
        refreshChrome();
    }

    /** 打開原本的分頁：0 排程、1 設定、2 好友與傳檔、3 日誌。 */
    public void openTab(int index) {
        holdingHome = false;
        clearOrbitalCaches();
        if (index >= 0 && index < tabs.getTabCount() && tabs.getSelectedIndex() != index) {
            tabs.setSelectedIndex(index);
        }
        tabs.setVisible(true);
        revalidate();
        repaint();
        refreshChrome();
    }

    /** 問候氣泡改畫在這個高度下面，避開垂下來的手指。 */
    void dropHomeBubbleBelow(int yInShell) {
        board.setBubbleFloor(yInShell);
    }

    /** 點到跨在面板外的上半身時，跟點角色一樣打招呼。 */
    public void greet() {
        hero.play(AnimeCast.WAVE, 1.8);
        board.sayClick();
        repaint();
    }

    /** 右側放角色和「下一個任務」的區域，座標在這個面板裡。 */
    java.awt.Rectangle homeStageBounds() {
        return new java.awt.Rectangle(board.stage);
    }

    /**
     * 全身圖和卡片用同一組座標。圖畫在卡片後面，手指由前景層再畫一次。
     */
    void presentHomeFigure(
            java.awt.Rectangle figureInShell,
            java.awt.Rectangle cardInShell,
            int textRightInShell,
            java.awt.image.BufferedImage image) {
        homeFigure = image;
        homeFigureBounds = figureInShell == null || figureInShell.width <= 0
                ? null
                : new java.awt.Rectangle(figureInShell);
        if (cardInShell == null || cardInShell.width < 80) {
            return;
        }
        board.seatReminder(cardInShell, textRightInShell);
    }

    /**
     * 軌道版角色：軀幹畫在殼層（卡片後面），跨框頭髮由畫布前景補上。
     */
    void presentOrbitalFigure(java.awt.Rectangle figureInShell) {
        homeFigure = OrbitalAssets.shared().foreground();
        homeFigureBounds = figureInShell == null || figureInShell.width <= 0 || homeFigure == null
                ? null
                : new java.awt.Rectangle(figureInShell);
        prepareHomeLayout();
        board.seatOrbitalReminderAtWaist(homeFigureBounds);
        if (board.reminder.height > 0) {
            dropHomeBubbleBelow(board.reminder.y + board.reminder.height + 8);
        }
    }

    /** 換主題或離開首頁時清掉上一張角色，避免毛衣女孩留在軌道版上。 */
    void releaseHomeFigure() {
        homeFigure = null;
        homeFigureBounds = null;
        board.clearSeat();
    }

    /** 「下一個任務」卡片，座標在這個面板裡。不在首頁時是空的。 */
    public java.awt.Rectangle homeReminderBounds() {
        if (!holdingHome) {
            return new java.awt.Rectangle();
        }
        // 繪製途中不要重算 layout，否則腰部卡片會跑掉。
        return new java.awt.Rectangle(board.reminder);
    }

    public void prepareHomeLayout() {
        if (holdingHome) {
            board.layout(contentBounds());
        }
    }

    private void refreshChrome() {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame) {
            WindowChrome.relayout((JFrame) window);
        }
    }

    private static int navIndexForTab(int tab) {
        for (int i = 0; i < NAV_TO_TAB.length; i++) {
            if (NAV_TO_TAB[i] == tab) {
                return i;
            }
        }
        return 0;
    }

    public void followTab() {
        if (holdingHome) {
            return;
        }
        tabs.setVisible(true);
        revalidate();
        repaint();
        refreshChrome();
    }

    public void setUseRoom(boolean useRoom) {
        this.useRoom = useRoom;
        setOpaque(useRoom);
        repaint();
    }

    public void play(int frame) {
        hero.play(frame, 2.0);
        repaint();
    }

    /** 首頁卡片要的真實資料，以及快捷按鈕。沒有設定時卡片顯示空狀態。 */
    public void setHomeActions(HomeActions actions) {
        this.homeActions = actions;
        repaint();
    }

    public interface HomeActions {
        HomeBrief read();

        void runTask();

        void openChat();

        /** 打開卡片上那位好友的聊天視窗。沒有對象時改去好友頁。 */
        void openRecentFriend();

        void sendFile();
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
        });
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
    public void doLayout() {
        int margin = 16;
        int gap = 12;
        int navW = 176;
        int x = margin + navW + gap;
        int y = margin;
        int w = Math.max(0, getWidth() - x - margin);
        int h = Math.max(0, getHeight() - margin * 2);
        tabs.setBounds(x, y, w, h);
    }

    private void activate(int id) {
        if (id >= 0 && id < NAV.length) {
            int tab = NAV_TO_TAB[id];
            if (tab < 0) {
                showHome();
            } else {
                openTab(tab);
            }
            return;
        }
        if (id == 20) {
            hero.play(AnimeCast.WAVE, 1.8);
            board.sayClick();
            repaint();
            return;
        }
        if (homeActions == null) {
            return;
        }
        if (id == HomeBoard.SCHEDULE) {
            openTab(0);
        } else if (id == HomeBoard.FRIENDS) {
            homeActions.openRecentFriend();
        } else if (id == HomeBoard.CHAT) {
            homeActions.openChat();
        } else if (id == HomeBoard.RUN) {
            homeActions.runTask();
        } else if (id == HomeBoard.FILE) {
            homeActions.sendFile();
        }
    }

    private void noticeStatus() {
        Set<String> live = new HashSet<>();
        for (CheckInTask task : tasks()) {
            if (task.getId() == null || task.getStatus() == null) {
                continue;
            }
            live.add(task.getId());
            TaskStatus status = task.getStatus();
            TaskStatus previous = seenTasks.put(task.getId(), status);
            if (previous == null || previous == status) {
                continue;
            }
            if (status == TaskStatus.CHECKING_IN) {
                hero.play(AnimeCast.SMILE, 2.4);
            } else if (status == TaskStatus.SUCCESS) {
                hero.play(AnimeCast.SMILE, 2.0);
                board.sayDone();
                repaint();
            } else if (status == TaskStatus.FAILED) {
                hero.play(AnimeCast.REACT, 2.0);
                board.sayTrouble();
                repaint();
            }
        }
        seenTasks.keySet().retainAll(live);
    }

    private int hit(int x, int y) {
        java.awt.Rectangle nav = navBounds();
        for (int i = 0; i < NAV.length; i++) {
            if (navRow(nav, i).contains(x, y)) {
                return i;
            }
        }
        if (holdingHome) {
            int home = board.hit(x, y);
            if (home >= 0) {
                return home;
            }
            // 右側舞台（角色、泡泡、空白處）都算打招呼。毛衣女孩的身體不在側欄 bust 裡。
            if (board.stage.contains(x, y)) {
                return 20;
            }
        }
        if (bust.contains(x, y)) {
            return 20;
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintRoom(g);
        java.awt.Rectangle nav = navBounds();
        panel(g, nav);
        g.setFont(UiFonts.chineseBold(18));
        g.setColor(INK);
        g.drawString("助手", nav.x + 18, nav.y + 36);
        g.setFont(UiFonts.chinesePlain(12));
        g.setColor(MUTED);
        g.drawString("洋流", nav.x + 18, nav.y + 56);
        int selected = holdingHome ? 0 : navIndexForTab(tabs.getSelectedIndex());
        for (int i = 0; i < NAV.length; i++) {
            java.awt.Rectangle row = navRow(nav, i);
            if (selected == i) {
                g.setColor(new Color(255, 122, 168, 220));
                g.fillRoundRect(row.x, row.y, row.width, row.height, 12, 12);
            } else if (hot == i) {
                g.setColor(new Color(255, 255, 255, 36));
                g.fillRoundRect(row.x, row.y, row.width, row.height, 12, 12);
            }
            g.setFont(UiFonts.chinesePlain(15));
            g.setColor(selected == i ? new Color(48, 18, 36) : INK);
            g.drawString(NAV[i], row.x + 16, row.y + 24);
        }
        paintBust(g, nav);
        if (holdingHome) {
            paintHome(g, contentBounds());
        }
        g.dispose();
    }

    private void paintHome(Graphics2D g, java.awt.Rectangle area) {
        HomeBrief brief = homeActions == null ? HomeBrief.empty() : homeActions.read();
        board.layout(area);
        if (theme == CharacterTheme.ORBITAL) {
            paintOrbitalScene(g);
            if (homeFigureBounds != null) {
                board.seatOrbitalReminderAtWaist(homeFigureBounds);
            } else {
                board.seatOrbitalReminder();
            }
        }
        board.paint(g, brief, hot);
        // 軌道軀幹先畫，再局部遮切口，最後半透明卡片蓋上。
        paintHomeFigure(g);
        paintOrbitalWaistMask(g);
        board.paintReminder(g, brief);
        board.paintHomeBubble(g);
    }

    /** 地球、星空、太空站裁在右側舞台；左緣以透明漸層接室內背景。 */
    private void paintOrbitalScene(Graphics2D g) {
        java.awt.image.BufferedImage image = OrbitalAssets.shared().stageImage();
        if (image == null || board.stage.width < 40 || board.stage.height < 40) {
            return;
        }
        int w = board.stage.width;
        int h = board.stage.height;
        if (orbitalSceneLayer == null || orbitalSceneW != w || orbitalSceneH != h) {
            BufferedImage layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D lg = layer.createGraphics();
            try {
                lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                OrbitalAssets.drawCover(
                        lg,
                        image,
                        new java.awt.Rectangle(0, 0, w, h),
                        OrbitalAssets.shared().stageAnchorX(),
                        OrbitalAssets.shared().stageAnchorY());
                // 左約 14%：場景 alpha 0→1，只遮 scene 層，室內背景可透出。
                int fade = Math.max(28, Math.min(140, w * 14 / 100));
                lg.setComposite(AlphaComposite.DstIn);
                lg.setPaint(new GradientPaint(
                        0, 0, new Color(1f, 1f, 1f, 0f),
                        fade, 0, new Color(1f, 1f, 1f, 1f)));
                lg.fillRect(0, 0, fade, h);
            } finally {
                lg.dispose();
            }
            orbitalSceneLayer = layer;
            orbitalSceneW = w;
            orbitalSceneH = h;
        }
        g.drawImage(orbitalSceneLayer, board.stage.x, board.stage.y, null);
    }

    private void clearOrbitalCaches() {
        orbitalSceneLayer = null;
        orbitalSceneW = -1;
        orbitalSceneH = -1;
        orbitalFigureLayer = null;
        orbitalFigureSource = null;
        orbitalFigureW = -1;
        orbitalFigureH = -1;
        orbitalBustLayer = null;
        orbitalBustW = -1;
        orbitalBustH = -1;
    }

    /** 全身畫在卡片後面；軌道版再裁到卡片上緣內。 */
    private void paintHomeFigure(Graphics2D g) {
        if (homeFigure == null || homeFigureBounds == null || board.stage.width < 40) {
            return;
        }
        Shape clip = g.getClip();
        int x = board.stage.x;
        int width = Math.max(0, getWidth() - x);
        g.clipRect(x, 0, width, getHeight());
        if (theme == CharacterTheme.ORBITAL && board.reminder.height > 40) {
            // 底緣藏進卡片上段，半透明卡片再靠腰線遮罩補實。
            int hide = board.reminder.y + Math.max(24, board.reminder.height * 30 / 100);
            g.clipRect(0, 0, getWidth(), Math.max(0, hide));
        }
        if (theme == CharacterTheme.ORBITAL) {
            BufferedImage scaled = scaledOrbitalFigure();
            if (scaled != null) {
                g.drawImage(scaled, homeFigureBounds.x, homeFigureBounds.y, null);
            }
        } else {
            Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(
                    homeFigure,
                    homeFigureBounds.x,
                    homeFigureBounds.y,
                    homeFigureBounds.width,
                    homeFigureBounds.height,
                    null);
            if (previous != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
            }
        }
        g.setClip(clip);
    }

    private BufferedImage scaledOrbitalFigure() {
        if (homeFigure == null || homeFigureBounds == null || homeFigureBounds.width < 2 || homeFigureBounds.height < 2) {
            return null;
        }
        int w = homeFigureBounds.width;
        int h = homeFigureBounds.height;
        // 畫布每次繪製都會 release 再 present 一次，快取只看來源圖與尺寸。
        if (orbitalFigureLayer == null || orbitalFigureSource != homeFigure
                || orbitalFigureW != w || orbitalFigureH != h) {
            BufferedImage layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D lg = layer.createGraphics();
            try {
                lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                lg.drawImage(homeFigure, 0, 0, w, h, null);
            } finally {
                lg.dispose();
            }
            orbitalFigureLayer = layer;
            orbitalFigureSource = homeFigure;
            orbitalFigureW = w;
            orbitalFigureH = h;
        }
        return orbitalFigureLayer;
    }

    private BufferedImage scaledOrbitalBust(BufferedImage portrait, int width, int height) {
        if (portrait == null || width < 2 || height < 2) {
            return null;
        }
        if (orbitalBustLayer == null || orbitalBustW != width || orbitalBustH != height) {
            BufferedImage layer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D lg = layer.createGraphics();
            try {
                lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                lg.drawImage(portrait, 0, 0, width, height, null);
            } finally {
                lg.dispose();
            }
            orbitalBustLayer = layer;
            orbitalBustW = width;
            orbitalBustH = height;
        }
        return orbitalBustLayer;
    }

    /** 在角色平切線畫一條不透明帶，避免 85% 卡片透出水平切口。 */
    private void paintOrbitalWaistMask(Graphics2D g) {
        if (theme != CharacterTheme.ORBITAL
                || homeFigureBounds == null
                || board.reminder.height < 40) {
            return;
        }
        int cutY = Math.min(
                homeFigureBounds.y + homeFigureBounds.height - 1,
                board.reminder.y + board.reminder.height - 4);
        if (cutY < board.reminder.y + 4 || cutY > board.reminder.y + board.reminder.height - 4) {
            return;
        }
        int band = Math.max(10, board.reminder.height * 12 / 100);
        int top = Math.max(board.reminder.y, cutY - band / 2);
        int bottom = Math.min(board.reminder.y + board.reminder.height, cutY + band / 2 + 1);
        int left = Math.max(board.reminder.x + 2, homeFigureBounds.x - 6);
        int right = Math.min(board.reminder.x + board.reminder.width - 2, homeFigureBounds.x + homeFigureBounds.width + 6);
        if (right - left < 24 || bottom - top < 4) {
            return;
        }
        Shape clip = g.getClip();
        g.clipRect(board.reminder.x, board.reminder.y, board.reminder.width, board.reminder.height);
        g.setColor(ORBITAL_WAIST_MASK);
        g.fillRoundRect(left, top, right - left, bottom - top, 10, 10);
        g.setClip(clip);
    }

    private void paintRoom(Graphics2D g) {
        if (!useRoom) {
            // 使用者自訂背景：讓底下 WindowChrome 畫的背景圖透出來，不要蓋掉。
            return;
        }
        if (room == null) {
            g.setColor(new Color(28, 20, 48));
            g.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        double scale = Math.max(getWidth() / (double) room.getWidth(), getHeight() / (double) room.getHeight());
        int dw = (int) Math.ceil(room.getWidth() * scale);
        int dh = (int) Math.ceil(room.getHeight() * scale);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(room, (getWidth() - dw) / 2, (getHeight() - dh) / 2, dw, dh, null);
        g.setColor(new Color(16, 10, 32, 36));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private List<CheckInTask> tasks() {
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

    private void paintBust(Graphics2D g, java.awt.Rectangle nav) {
        bust = new java.awt.Rectangle();
        if (holdingHome) {
            return;
        }
        if (theme == CharacterTheme.ORBITAL) {
            java.awt.image.BufferedImage portrait = OrbitalAssets.shared().sidebarImage();
            if (portrait == null) {
                return;
            }
            bust = bustBox(nav, portrait.getWidth(), portrait.getHeight());
            if (bust.width <= 0) {
                return;
            }
            // 透明半身等比例完整顯示臉與髮頂；縮放結果快取，避免 hover 反覆雙線性縮放。
            BufferedImage scaled = scaledOrbitalBust(portrait, bust.width, bust.height);
            if (scaled != null) {
                g.drawImage(scaled, bust.x, bust.y, null);
            }
            return;
        }
        if (cast == null) {
            return;
        }
        bust = bustBox(nav, cast.frameWidth(), cast.frameHeight());
        if (bust.width <= 0) {
            return;
        }
        cast.draw(g, hero.frame(), bust.x, bust.y, bust.width, bust.height);
    }

    /** 側欄底部的小立繪。排程、好友、設定、日誌共用這一個位置。 */
    private static java.awt.Rectangle bustBox(java.awt.Rectangle nav, int imageW, int imageH) {
        int top = nav.y + 78 + NAV.length * 48 + 8;
        int avail = nav.y + nav.height - top - 8;
        if (avail < 72 || imageW < 1 || imageH < 1) {
            return new java.awt.Rectangle();
        }
        int height = Math.min(avail, 240);
        int width = height * imageW / imageH;
        if (width > nav.width - 16) {
            width = nav.width - 16;
            height = width * imageH / Math.max(1, imageW);
        }
        int x = nav.x + (nav.width - width) / 2;
        int y = nav.y + nav.height - 8 - height;
        return new java.awt.Rectangle(x, y, width, height);
    }

    private java.awt.Rectangle navBounds() {
        return new java.awt.Rectangle(16, 16, 176, Math.max(0, getHeight() - 32));
    }

    private java.awt.Rectangle contentBounds() {
        return new java.awt.Rectangle(204, 16, Math.max(0, getWidth() - 220), Math.max(0, getHeight() - 32));
    }

    static int navCount() {
        return NAV.length;
    }

    static String navLabel(int index) {
        return NAV[index];
    }

    static int tabForNav(int index) {
        return NAV_TO_TAB[index];
    }

    private static java.awt.Rectangle navRow(java.awt.Rectangle nav, int index) {
        return new java.awt.Rectangle(nav.x + 12, nav.y + 78 + index * 48, nav.width - 24, 38);
    }

    private static void panel(Graphics2D g, java.awt.Rectangle rect) {
        g.setColor(PANEL);
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 18, 18);
        g.setColor(PANEL_EDGE);
        g.drawRoundRect(rect.x, rect.y, rect.width - 1, rect.height - 1, 18, 18);
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

    private static BufferedImage loadRoom() {
        try (InputStream in = AnimeShell.class.getResourceAsStream("/hud/anime/room.jpg")) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }
}
