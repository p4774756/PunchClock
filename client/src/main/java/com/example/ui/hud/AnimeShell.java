package com.example.ui.hud;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import com.example.service.SchedulerService;
import com.example.ui.UiFonts;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private static final Color PINK = new Color(255, 122, 168);
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
    private HomeActions homeActions;
    private boolean holdingHome = true;
    private boolean useRoom = true;
    private int hot = -1;
    private java.awt.Rectangle character = new java.awt.Rectangle();
    private java.awt.Rectangle bust = new java.awt.Rectangle();
    private int paintedFrame = -1;
    private long paintedSecond = -1;
    private long paintedBreath = -1;

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
                    hot = id;
                    setCursor(id >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hot = -1;
                setCursor(Cursor.getDefaultCursor());
                repaint();
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
            boolean countdown = holdingHome && second != paintedSecond;
            boolean breathe = holdingHome && !reducedMotion && now / 200L != paintedBreath;
            boolean fade = holdingHome && board.animating();
            if (frame != paintedFrame || countdown || breathe || fade) {
                paintedFrame = frame;
                paintedSecond = second;
                paintedBreath = now / 200L;
                if (countdown || fade) {
                    repaint();
                } else {
                    if (!character.isEmpty()) {
                        java.awt.Rectangle dirty = new java.awt.Rectangle(character);
                        dirty.grow(2, 6);
                        repaint(dirty);
                    }
                    if (!bust.isEmpty()) {
                        repaint(bust);
                    }
                    if (character.isEmpty() && bust.isEmpty()) {
                        repaint();
                    }
                }
            }
        });
    }

    public boolean isHoldingHome() {
        return holdingHome;
    }

    public void showHome() {
        holdingHome = true;
        tabs.setVisible(false);
        revalidate();
        repaint();
    }

    /** 打開原本的分頁：0 排程、1 設定、2 好友與傳檔、3 日誌。 */
    public void openTab(int index) {
        holdingHome = false;
        if (index >= 0 && index < tabs.getTabCount() && tabs.getSelectedIndex() != index) {
            tabs.setSelectedIndex(index);
        }
        tabs.setVisible(true);
        revalidate();
        repaint();
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

    /** 首頁卡片要的真實資料，以及三個快捷按鈕。沒有設定時卡片顯示空狀態。 */
    public void setHomeActions(HomeActions actions) {
        this.homeActions = actions;
        repaint();
    }

    public interface HomeActions {
        HomeBrief read();

        void runTask();

        void openChat();

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
        } else if (id == HomeBoard.FRIENDS || id == HomeBoard.CHAT) {
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
                board.say("完成啦！做得很好 ♡");
                repaint();
            } else if (status == TaskStatus.FAILED) {
                hero.play(AnimeCast.REACT, 2.0);
                board.say("好像遇到問題了，要不要查看日誌？");
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
        }
        if (character.contains(x, y) || bust.contains(x, y)) {
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
        board.paint(g, brief, hot);
        paintCharacter(g, board.stage);
        board.paintBubble(g, character);
    }

    private void paintCharacter(Graphics2D g, java.awt.Rectangle area) {
        if (cast == null || area.width < 40 || area.height < 80) {
            character = new java.awt.Rectangle();
            return;
        }
        int bottom = board.reminder.height > 0 ? board.reminder.y - 8 : area.y + area.height - 8;
        int available = Math.max(1, bottom - area.y);
        int maxWidth = area.width >= 300 ? area.width - 168 : area.width;
        int height = available;
        int width = height * cast.frameWidth() / Math.max(1, cast.frameHeight());
        if (width > maxWidth) {
            width = Math.max(1, maxWidth);
            height = width * cast.frameHeight() / Math.max(1, cast.frameWidth());
        }
        int breathe = 0;
        if (!reducedMotion) {
            double wave = Math.sin(System.currentTimeMillis() / 4000.0 * Math.PI * 2.0);
            breathe = (int) Math.round(wave * 2.0);
        }
        int x = area.x + area.width - width;
        int y = bottom - height - breathe;
        if (y < area.y) {
            y = area.y;
        }
        character = new java.awt.Rectangle(x, y, width, height);
        cast.draw(g, hero.frame(), x, y, width, height);
    }

    private void paintRoom(Graphics2D g) {
        if (!useRoom || room == null) {
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
        if (holdingHome || cast == null) {
            return;
        }
        int top = nav.y + 78 + NAV.length * 48 + 8;
        int avail = nav.y + nav.height - top - 8;
        if (avail < 72) {
            return;
        }
        int height = Math.min(avail, 210);
        int width = height * cast.frameWidth() / Math.max(1, cast.frameHeight());
        if (width > nav.width - 20) {
            width = nav.width - 20;
            height = width * cast.frameHeight() / Math.max(1, cast.frameWidth());
        }
        int x = nav.x + (nav.width - width) / 2;
        int y = nav.y + nav.height - 8 - height;
        bust = new java.awt.Rectangle(x, y, width, height);
        cast.draw(g, hero.frame(), x, y, width, height);
    }

    private java.awt.Rectangle navBounds() {
        return new java.awt.Rectangle(16, 16, 176, Math.max(0, getHeight() - 32));
    }

    private java.awt.Rectangle contentBounds() {
        return new java.awt.Rectangle(204, 16, Math.max(0, getWidth() - 220), Math.max(0, getHeight() - 32));
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
