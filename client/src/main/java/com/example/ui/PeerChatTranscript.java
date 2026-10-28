package com.example.ui;

import com.example.ChatProtocol;
import com.example.service.PeerChatStore;
import com.example.service.PeerImage;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 聊天視窗的對話區。自己的訊息在右邊、對方在左邊，戳一下與系統提示置中。
 * 長文在氣泡裡換行；程式碼與 stack trace 保留縮排，改在氣泡內左右捲動。
 */
public class PeerChatTranscript extends JPanel implements Scrollable {

    static final int THUMB_EDGE = 220;

    private static final Color SELF_FILL = new Color(0x7A, 0x3A, 0x62);
    private static final Color PEER_FILL = CandyTheme.FIELD;
    private static final Color NOTE_FILL = CandyTheme.HEADER;

    private final String peerId;
    private final Font bodyFont;
    private final int fontSize;
    private final List<PeerChatStore.Entry> entries = new ArrayList<>();
    private final List<MessageView> views = new ArrayList<>();
    private final Font codeFont;
    private Consumer<BufferedImage> imageViewer;
    private boolean developerMode;
    private int laidWidth = -1;

    public PeerChatTranscript(String peerId, String fontFamily, int fontSize) {
        this.peerId = peerId == null ? "" : peerId;
        this.fontSize = fontSize;
        this.bodyFont = new Font(fontFamily == null || fontFamily.isEmpty() ? Font.SANS_SERIF : fontFamily,
                Font.PLAIN, fontSize);
        this.codeFont = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(CandyTheme.PANEL);
        setBorder(new EmptyBorder(8, 8, 8, 8));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refit(getWidth());
            }
        });
    }

    /** 點圖片時要做什麼（通常是開一個可另存的檢視視窗）。 */
    public void setImageViewer(Consumer<BufferedImage> viewer) {
        this.imageViewer = viewer;
    }

    /**
     * 開發者模式：每則文字都用等寬字，並保留縮排、Tab 與換行。
     * 關掉之後，只有程式碼與 stack trace 維持等寬。
     */
    public void setDeveloperMode(boolean enabled) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setDeveloperMode(enabled));
            return;
        }
        if (developerMode == enabled) {
            return;
        }
        developerMode = enabled;
        rebuild();
    }

    public boolean isDeveloperMode() {
        return developerMode;
    }

    public void append(PeerChatStore.Entry entry) {
        if (entry == null) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> append(entry));
            return;
        }
        entries.add(entry);
        MessageView view = new MessageView(entry);
        views.add(view);
        add(view.row);
        refit(Math.max(getWidth(), 360));
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::scrollToEnd);
    }

    public void clear() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clear);
            return;
        }
        entries.clear();
        views.clear();
        removeAll();
        laidWidth = -1;
        revalidate();
        repaint();
    }

    private void rebuild() {
        removeAll();
        views.clear();
        for (PeerChatStore.Entry entry : entries) {
            MessageView view = new MessageView(entry);
            views.add(view);
            add(view.row);
        }
        laidWidth = -1;
        refit(Math.max(getWidth(), 360));
        revalidate();
        repaint();
    }

    /** 測試用：這段文字落在左邊、右邊或中間。 */
    String sideOf(String snippet) {
        for (MessageView view : views) {
            if (view.contains(snippet)) {
                return view.side;
            }
        }
        return "";
    }

    /** 測試用：氣泡裡的原文，含換行與縮排。 */
    String bodyOf(String snippet) {
        for (MessageView view : views) {
            if (view.contains(snippet)) {
                return view.bodyText();
            }
        }
        return "";
    }

    boolean preservesLayout(String snippet) {
        for (MessageView view : views) {
            if (view.contains(snippet)) {
                return view.preserved;
            }
        }
        return false;
    }

    boolean usesCodeFont(String snippet) {
        for (MessageView view : views) {
            if (view.contains(snippet) && view.body != null) {
                return Font.MONOSPACED.equals(view.body.getFont().getFamily())
                        || Font.MONOSPACED.equals(view.body.getFont().getName());
            }
        }
        return false;
    }

    /** 從傳檔提示裡取出檔名、大小與狀態。不是檔案訊息時回傳 null。 */
    static FileNotice fileNotice(String text) {
        if (text == null) {
            return null;
        }
        int open = text.indexOf('「');
        int close = open < 0 ? -1 : text.indexOf('」', open + 1);
        if (open < 0 || close <= open + 1) {
            return null;
        }
        String name = text.substring(open + 1, close).trim();
        if (name.isEmpty()) {
            return null;
        }
        String size = "";
        int sizeOpen = text.indexOf('（', close);
        int sizeClose = sizeOpen < 0 ? -1 : text.indexOf('）', sizeOpen + 1);
        if (sizeOpen > 0 && sizeClose > sizeOpen) {
            size = text.substring(sizeOpen + 1, sizeClose).trim();
        }
        String status = text.substring(0, open).trim();
        if (status.isEmpty()) {
            status = "檔案";
        }
        return new FileNotice(name, size, status);
    }

    static boolean isStackTrace(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("Exception") || text.contains("Caused by:") || text.contains("\tat ");
    }

    /** 放上剪貼簿的文字。圖片沒有文字時是空字串。 */
    static String copyPayload(PeerChatStore.Entry entry) {
        if (entry == null) {
            return "";
        }
        return entry.text == null ? "" : entry.text;
    }

    /** 氣泡標題上的來源。自己送出的顯示本機，對方顯示名稱，名稱和裝置不同時兩個都留。 */
    static String sourceLabel(PeerChatStore.Entry entry, String conversationId) {
        if (entry == null) {
            return "";
        }
        boolean outgoing = entry.outgoing;
        if (entry.kind == PeerChatStore.Kind.NOTE) {
            FileNotice file = fileNotice(entry.text);
            if (file == null) {
                return "";
            }
            // 傳檔提示一律記成 outgoing，方向要看文字
            outgoing = !file.status.startsWith("對方");
        }
        if (outgoing) {
            return "本機";
        }
        String device = conversationId == null ? "" : conversationId;
        String name = entry.speaker == null || entry.speaker.isEmpty() ? device : entry.speaker;
        if (!entry.speaker.isEmpty() && !device.isEmpty() && !entry.speaker.equals(device)) {
            return name + " · " + device;
        }
        return name.isEmpty() ? "對方" : name;
    }

    /** 有縮排、stack trace 或圍欄程式碼時，不要依單字折行。 */
    static boolean preserveLayout(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.contains("```")) {
            return true;
        }
        String[] lines = text.split("\n", -1);
        boolean indented = false;
        boolean trace = false;
        for (String line : lines) {
            if (line.startsWith("\t") || line.startsWith("  ")) {
                indented = true;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("at ") && trimmed.contains("(")) {
                trace = true;
            }
            if (trimmed.startsWith("Caused by:") || trimmed.contains("Exception")) {
                trace = true;
            }
        }
        return indented || trace;
    }

    private void refit(int width) {
        if (width <= 0 || views.isEmpty()) {
            return;
        }
        if (width == laidWidth) {
            return;
        }
        laidWidth = width;
        int bubble = Math.max(200, (int) (width * 0.72));
        for (MessageView view : views) {
            view.fit(bubble);
        }
    }

    private void scrollToEnd() {
        scrollRectToVisible(new Rectangle(0, Math.max(0, getHeight() - 1), 1, 1));
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(360, 280);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(1, visibleRect.height - 18);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /** 今天只顯示時間，其他日期加上月日。 */
    static String formatTime(long epochMs, ZoneId zone) {
        java.time.ZonedDateTime time = Instant.ofEpochMilli(epochMs).atZone(zone);
        boolean today = time.toLocalDate().equals(LocalDate.now(zone));
        return time.format(DateTimeFormatter.ofPattern(today ? "HH:mm:ss" : "MM-dd HH:mm:ss"));
    }

    private final class MessageView {
        private final PeerChatStore.Entry entry;
        private final String side;
        private final boolean preserved;
        private final JComponent row;
        private final JTextArea body;
        private final JScrollPane bodyScroll;

        private MessageView(PeerChatStore.Entry entry) {
            this.entry = entry;
            this.preserved = entry.kind == PeerChatStore.Kind.MESSAGE
                    && (developerMode || preserveLayout(entry.text));
            FileNotice file = entry.kind == PeerChatStore.Kind.NOTE ? fileNotice(entry.text) : null;
            if (file != null) {
                // 傳檔提示：「對方…」靠左，自己這邊（已直傳、已送出）靠右
                side = file.status.startsWith("對方") ? "left" : "right";
            } else if (entry.kind == PeerChatStore.Kind.NOTE || entry.kind == PeerChatStore.Kind.POKE) {
                side = "center";
            } else {
                side = entry.outgoing ? "right" : "left";
            }
            body = buildBody();
            bodyScroll = preserved ? scrollAround(body) : null;
            row = buildRow();
        }

        private boolean contains(String snippet) {
            if (snippet == null || snippet.isEmpty()) {
                return false;
            }
            if (entry.text != null && entry.text.contains(snippet)) {
                return true;
            }
            if (pokePhrase().contains(snippet)) {
                return true;
            }
            return sourceLabel(entry, peerId).contains(snippet);
        }

        private String pokePhrase() {
            if (entry.kind != PeerChatStore.Kind.POKE) {
                return "";
            }
            return entry.outgoing ? "你戳了對方一下" : "戳了你一下";
        }

        private String bodyText() {
            if (entry.kind != PeerChatStore.Kind.MESSAGE) {
                return entry.text == null ? "" : entry.text;
            }
            return body == null ? "" : body.getText();
        }

        private void fit(int bubbleWidth) {
            if (body == null) {
                lock(row);
                return;
            }
            int inner = Math.max(120, bubbleWidth - 28);
            if (preserved) {
                bodyScroll.setPreferredSize(new Dimension(inner, blockHeight(body)));
                bodyScroll.setMaximumSize(new Dimension(inner, blockHeight(body)));
            } else {
                body.setSize(new Dimension(inner, Short.MAX_VALUE));
                Dimension pref = body.getPreferredSize();
                int width = Math.min(inner, Math.max(pref.width, 40));
                body.setPreferredSize(new Dimension(width, pref.height));
                body.setMaximumSize(new Dimension(inner, pref.height));
            }
            lock(row);
        }

        private JComponent buildRow() {
            JPanel line = new JPanel(new BorderLayout());
            line.setOpaque(false);
            line.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            JComponent bubble = bubble();
            if ("right".equals(side)) {
                line.add(bubble, BorderLayout.EAST);
            } else if ("left".equals(side)) {
                line.add(bubble, BorderLayout.WEST);
            } else {
                JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
                center.setOpaque(false);
                center.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
                center.add(bubble);
                line.add(center, BorderLayout.CENTER);
            }
            return line;
        }

        private JComponent bubble() {
            Color fill = "right".equals(side) ? SELF_FILL : "left".equals(side) ? PEER_FILL : NOTE_FILL;
            Bubble panel = new Bubble(fill);
            panel.add(header(), BorderLayout.NORTH);
            FileNotice file = entry.kind == PeerChatStore.Kind.NOTE ? fileNotice(entry.text) : null;
            if (entry.kind == PeerChatStore.Kind.POKE) {
                panel.add(plainLabel(pokePhrase(), bodyFont, CandyTheme.PINK_HI),
                        BorderLayout.CENTER);
            } else if (file != null) {
                panel.add(fileCard(file), BorderLayout.CENTER);
            } else if (bodyScroll != null) {
                panel.add(codeBlock(), BorderLayout.CENTER);
            } else if (body != null && entry.text != null && !entry.text.isEmpty()) {
                panel.add(body, BorderLayout.CENTER);
            }
            if (entry.hasImage()) {
                panel.add(images(entry.imageEncoded), BorderLayout.SOUTH);
            }
            return panel;
        }

        private JComponent header() {
            JPanel header = new JPanel(new BorderLayout(6, 0));
            header.setOpaque(false);
            header.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            String source = sourceLabel(entry, peerId);
            String when = formatTime(entry.timeMs, ZoneId.systemDefault());
            String title = source.isEmpty() ? when : source + "  " + when;
            JLabel label = plainLabel(title, bodyFont.deriveFont((float) Math.max(11, fontSize - 2)), CandyTheme.LAVENDER);
            header.add(label, BorderLayout.CENTER);
            FileNotice file = entry.kind == PeerChatStore.Kind.NOTE ? fileNotice(entry.text) : null;
            boolean canCopy = file != null
                    || (entry.kind == PeerChatStore.Kind.MESSAGE && entry.text != null && !entry.text.isEmpty());
            if (canCopy) {
                JButton copy = new CandyButton("複製", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
                copy.setFont(bodyFont.deriveFont((float) Math.max(11, fontSize - 1)));
                String payload = file != null ? file.name : copyPayload(entry);
                copy.setText(file != null ? "複製檔名" : preserved ? "複製程式碼" : "複製");
                copy.setToolTipText(file != null
                        ? "複製檔名"
                        : preserved ? "複製這段程式碼，縮排與換行都會留下" : "複製這則訊息");
                copy.addActionListener(e -> copyToClipboard(payload, copy));
                header.add(copy, BorderLayout.EAST);
            }
            return header;
        }

        private JTextArea buildBody() {
            if (entry.kind == PeerChatStore.Kind.POKE || entry.text == null || entry.text.isEmpty()) {
                return null;
            }
            JTextArea area = new JTextArea(entry.text);
            area.setFont(preserved ? codeFont : bodyFont);
            area.setEditable(false);
            area.setOpaque(false);
            area.setForeground(CandyTheme.INK);
            area.setCaretColor(CandyTheme.INK);
            area.setLineWrap(!preserved);
            area.setWrapStyleWord(false);
            area.setTabSize(4);
            area.putClientProperty(CandyTextField.MARK, Boolean.TRUE);
            area.setBorder(new EmptyBorder(0, 0, 0, 0));
            return area;
        }

        private JComponent images(String encodedField) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            row.setOpaque(false);
            row.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            List<String> images = ChatProtocol.splitPeerImages(encodedField);
            if (images == null) {
                row.add(plainLabel("（圖片無法顯示）", bodyFont, CandyTheme.MUTED));
                return row;
            }
            for (String encoded : images) {
                BufferedImage thumb = PeerImage.decodeThumbnail(encoded, THUMB_EDGE);
                if (thumb == null) {
                    row.add(plainLabel("（圖片無法顯示）", bodyFont, CandyTheme.MUTED));
                    continue;
                }
                JLabel icon = new JLabel(new ImageIcon(thumb));
                icon.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                icon.setToolTipText("點一下放大");
                icon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 1 && imageViewer != null) {
                            BufferedImage full = PeerImage.decode(encoded);
                            if (full != null) {
                                imageViewer.accept(full);
                            }
                        }
                    }
                });
                row.add(icon);
            }
            return row;
        }

        private JComponent codeBlock() {
            JPanel stack = new JPanel();
            stack.setOpaque(false);
            stack.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
            String caption = isStackTrace(entry.text) ? "錯誤訊息" : "程式碼";
            JLabel label = plainLabel(caption, bodyFont.deriveFont((float) Math.max(11, fontSize - 2)),
                    CandyTheme.LAVENDER);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(label);
            bodyScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(bodyScroll);
            return stack;
        }

        private JComponent fileCard(FileNotice file) {
            JPanel card = new JPanel();
            card.setOpaque(false);
            card.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
            JLabel name = plainLabel("檔案  " + file.name, bodyFont.deriveFont(Font.BOLD), CandyTheme.INK);
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(name);
            if (!file.size.isEmpty()) {
                JLabel size = plainLabel(file.size, bodyFont, CandyTheme.LAVENDER);
                size.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(size);
            }
            JLabel status = plainLabel(file.status, bodyFont, CandyTheme.MUTED);
            status.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(status);
            return card;
        }
    }

    private static JScrollPane scrollAround(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.putClientProperty(CandyScrollBar.MARK, Boolean.TRUE);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        return scroll;
    }

    private static int blockHeight(JTextArea area) {
        int lines = 1;
        String text = area.getText();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        int line = area.getFontMetrics(area.getFont()).getHeight();
        return Math.min(180, Math.max(line + 8, lines * line + 8));
    }

    private static JLabel plainLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static void lock(JComponent row) {
        Dimension pref = row.getPreferredSize();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static void copyToClipboard(String text, JButton button) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        String previous = button.getText();
        button.setText("已複製");
        javax.swing.Timer timer = new javax.swing.Timer(900, e -> button.setText(previous));
        timer.setRepeats(false);
        timer.start();
    }

    static final class FileNotice {
        final String name;
        final String size;
        final String status;

        private FileNotice(String name, String size, String status) {
            this.name = name;
            this.size = size;
            this.status = status;
        }
    }

    private static final class Bubble extends JPanel {
        private final Color fill;

        private Bubble(Color fill) {
            super(new BorderLayout(0, 4));
            this.fill = fill;
            setOpaque(false);
            putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            setBorder(new EmptyBorder(8, 10, 8, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = CandyDraw.quality((Graphics2D) g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
