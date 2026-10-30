package com.example.ui;

import com.example.service.PeerAvatar;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * 傳檔視窗左側的一位好友。沒有大頭照時畫名稱首字，離線時灰色且不能選。
 */
public final class FriendListItem extends JComponent {

    public static final int ROW_HEIGHT = 64;
    private static final int AVATAR = 40;
    private static final int CHECK = 20;
    private static final Color ONLINE_DOT = new Color(0x3D, 0xDC, 0x84);
    private static final Color SELECTED_TOP = new Color(0xF0, 0x7A, 0xB4);
    private static final Color SELECTED_BOTTOM = new Color(0x8D, 0x62, 0xD4);
    private static final Color IDLE = new Color(0x3A, 0x2C, 0x62);
    private static final Color OFFLINE_FILL = new Color(0x2A, 0x20, 0x48);

    private static final Color[] FACES = {
            new Color(0xFF, 0x78, 0xB5),
            new Color(0xB7, 0x9A, 0xEF),
            new Color(0x7A, 0xA2, 0xF7),
            new Color(0xE0, 0xA3, 0x6A),
            new Color(0x6F, 0xCF, 0x97)
    };

    private final String clientId;
    private final boolean online;
    private final String statusLine;
    private final Icon avatar;
    private final Color face;
    private boolean selected;
    private Runnable onActivate;

    public FriendListItem(String clientId, boolean online, String appVersion, String avatarEncoded) {
        this.clientId = clientId == null ? "" : clientId;
        this.online = online;
        this.statusLine = statusLine(online, appVersion);
        this.avatar = avatarIcon(avatarEncoded, AVATAR);
        this.face = faceColor(this.clientId);
        setOpaque(false);
        setToolTipText(this.clientId + (online ? "（在線）" : "（離線，無法傳送）"));
        setCursor(Cursor.getPredefinedCursor(online
                ? Cursor.HAND_CURSOR
                : Cursor.DEFAULT_CURSOR));
        if (online) {
            CandyTheme.watch(this);
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!FriendListItem.this.online || onActivate == null) {
                    return;
                }
                onActivate.run();
            }
        });
    }

    public static String statusLine(boolean online, String appVersion) {
        String status = online ? "在線" : "離線";
        if (appVersion == null || appVersion.isBlank()) {
            return status;
        }
        return status + " · " + appVersion.trim();
    }

    public static String initial(String clientId) {
        String text = clientId == null ? "" : clientId.trim();
        if (text.isEmpty()) {
            return "?";
        }
        int point = text.codePointAt(0);
        String letter = new String(Character.toChars(point));
        if (letter.length() == 1 && Character.isLetter(letter.charAt(0))) {
            return letter.toUpperCase(Locale.ROOT);
        }
        return letter;
    }

    public static Color faceColor(String clientId) {
        String key = clientId == null ? "" : clientId;
        int index = Math.floorMod(key.hashCode(), FACES.length);
        return FACES[index];
    }

    /** 圓形大頭照。沒有圖片時用名稱首字。 */
    public static JComponent badge(String clientId, boolean online, String avatarEncoded, int size) {
        return new Badge(clientId, online, avatarIcon(avatarEncoded, size), size);
    }

    public String clientId() {
        return clientId;
    }

    public boolean isOnline() {
        return online;
    }

    public void setSelected(boolean selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        repaint();
    }

    public boolean isItemSelected() {
        return selected;
    }

    public void setOnActivate(Runnable onActivate) {
        this.onActivate = onActivate;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(220, ROW_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(140, ROW_HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, ROW_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
        int w = Math.max(1, getWidth() - 1);
        int h = Math.max(1, getHeight() - 1);
        int arc = 14;
        float hover = online ? CandyTheme.hover(this) : 0f;
        if (selected && online) {
            g2.setPaint(new java.awt.GradientPaint(0, 0, SELECTED_TOP, w, h, SELECTED_BOTTOM));
        } else if (!online) {
            g2.setColor(OFFLINE_FILL);
        } else {
            g2.setColor(CandyTheme.mix(IDLE, CandyTheme.ROW_HOT, hover));
        }
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        if (selected && online) {
            g2.setColor(new Color(255, 214, 236, 190));
            g2.drawRoundRect(0, 0, w, h, arc, arc);
        } else if (hover > 0.04f) {
            g2.setColor(CandyTheme.mix(CandyTheme.EDGE, CandyTheme.EDGE_HOT, hover));
            g2.drawRoundRect(0, 0, w, h, arc, arc);
        } else {
            g2.setColor(new Color(0x92, 0x75, 0xBD, online ? 80 : 36));
            g2.drawRoundRect(0, 0, w, h, arc, arc);
        }

        int ay = (getHeight() - AVATAR) / 2;
        paintBadge(g2, 10, ay, AVATAR, clientId, online, avatar, face);

        int textX = 10 + AVATAR + 10;
        int textW = Math.max(0, getWidth() - textX - 12 - (selected && online ? CHECK + 6 : 0));
        if (selected && online) {
            int cx = getWidth() - 12 - CHECK;
            int cy = (getHeight() - CHECK) / 2;
            g2.setColor(Color.WHITE);
            g2.fillOval(cx, cy, CHECK, CHECK);
            g2.setColor(SELECTED_BOTTOM);
            g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.drawLine(cx + 6, cy + CHECK / 2, cx + CHECK / 2 - 1, cy + CHECK - 7);
            g2.drawLine(cx + CHECK / 2 - 1, cy + CHECK - 7, cx + CHECK - 5, cy + 6);
        }
        g2.setFont(UiFonts.chineseBold(14));
        FontMetrics nameMetrics = g2.getFontMetrics();
        g2.setFont(UiFonts.chinesePlain(12));
        FontMetrics statusMetrics = g2.getFontMetrics();
        int block = nameMetrics.getHeight() + 2 + statusMetrics.getHeight();
        int top = Math.max(4, (getHeight() - block) / 2);
        g2.setFont(UiFonts.chineseBold(14));
        g2.setColor(!online ? CandyTheme.DISABLED : selected ? Color.WHITE : CandyTheme.INK);
        g2.drawString(CandyTheme.fit(clientId, nameMetrics, textW), textX, top + nameMetrics.getAscent());

        g2.setFont(UiFonts.chinesePlain(12));
        if (!online) {
            g2.setColor(CandyTheme.DISABLED);
        } else if (selected) {
            g2.setColor(new Color(255, 236, 246));
        } else {
            g2.setColor(new Color(0xB7, 0xE8, 0xC8));
        }
        g2.drawString(CandyTheme.fit(statusLine, statusMetrics, textW), textX,
                top + nameMetrics.getHeight() + 2 + statusMetrics.getAscent());
        g2.dispose();
    }

    static void paintBadge(Graphics2D g, int x, int y, int size, String clientId, boolean online,
                           Icon avatar, Color face) {
        if (avatar != null) {
            avatar.paintIcon(null, g, x, y);
        } else {
            g.setColor(online ? face : CandyTheme.DISABLED);
            g.fillOval(x, y, size, size);
            g.setColor(online ? CandyTheme.PINK_INK : new Color(0x2A, 0x20, 0x48));
            g.setFont(UiFonts.chineseBold(Math.max(12, size / 2 - 2)));
            String letter = initial(clientId);
            FontMetrics metrics = g.getFontMetrics();
            int lx = x + (size - metrics.stringWidth(letter)) / 2;
            int ly = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(letter, lx, ly);
        }
        g.setColor(new Color(255, 255, 255, online ? 90 : 40));
        g.drawOval(x, y, size - 1, size - 1);
        if (online) {
            int dot = Math.max(8, size / 5);
            int dx = x + size - dot;
            int dy = y + size - dot;
            g.setColor(new Color(0x24, 0x18, 0x3F));
            g.fillOval(dx - 2, dy - 2, dot + 4, dot + 4);
            g.setColor(ONLINE_DOT);
            g.fillOval(dx, dy, dot, dot);
        }
    }

    private static Icon avatarIcon(String encoded, int size) {
        if (encoded == null || encoded.isBlank() || PeerAvatar.decode(encoded) == null) {
            return null;
        }
        return PeerAvatar.previewIcon(encoded, null, size);
    }

    /** 圓形大頭照，給右側「發送給」卡片用。 */
    public static final class Badge extends JComponent {
        private final String clientId;
        private final boolean online;
        private final Icon avatar;
        private final Color face;
        private final int size;

        private Badge(String clientId, boolean online, Icon avatar, int size) {
            this.clientId = clientId == null ? "" : clientId;
            this.online = online;
            this.avatar = avatar;
            this.face = faceColor(this.clientId);
            this.size = Math.max(16, size);
            setOpaque(false);
            Dimension box = new Dimension(this.size, this.size);
            setPreferredSize(box);
            setMinimumSize(box);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            int x = Math.max(0, (getWidth() - size) / 2);
            int y = Math.max(0, (getHeight() - size) / 2);
            paintBadge(g2, x, y, size, clientId, online, avatar, face);
            g2.dispose();
        }
    }

    /** 寬度不夠時省略文字，避免把旁邊的按鈕擠出視窗。 */
    public static final class FitLabel extends JLabel {
        private String full = "";

        public FitLabel(java.awt.Font font, Color color) {
            setFont(font);
            setForeground(color);
            setOpaque(false);
        }

        public void setFullText(String text) {
            full = text == null ? "" : text;
            setText(full);
            setToolTipText(full.isEmpty() ? null : full);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics metrics = g2.getFontMetrics();
            String shown = CandyTheme.fit(full, metrics, Math.max(0, getWidth()));
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(shown, 0, y);
            g2.dispose();
        }
    }
}
