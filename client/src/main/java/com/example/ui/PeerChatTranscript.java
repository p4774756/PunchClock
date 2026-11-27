package com.example.ui;

import com.example.ChatProtocol;
import com.example.PeerGraffiti;
import com.example.service.PeerChatStore;
import com.example.service.PeerImage;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
 * 聊天視窗的對話區。訊息靠左；程式碼區塊自動換行、可展開收合。
 */
public class PeerChatTranscript extends JPanel implements Scrollable {

    static final int THUMB_EDGE = 220;
    static final int CODE_COLLAPSE_LINES = 15;
    private static final Color CODE_BG = new Color(0x2A, 0x2E, 0x36);
    private static final Color CODE_INK = new Color(0xE8, 0xEA, 0xED);
    private static final Color CODE_BORDER = new Color(0x4A, 0x4F, 0x58);

    private final String peerId;
    private final Font bodyFont;
    private final int fontSize;
    private final List<PeerChatStore.Entry> entries = new ArrayList<>();
    private final List<MessageView> views = new ArrayList<>();
    private final Font codeFont;
    private Consumer<BufferedImage> imageViewer;
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

    public void setImageViewer(Consumer<BufferedImage> viewer) {
        this.imageViewer = viewer;
    }

    public PeerChatRelayCard addRelayCard() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("addRelayCard on EDT");
        }
        PeerChatRelayCard card = new PeerChatRelayCard(bodyFont);
        JPanel line = wrapLeft(card);
        add(line);
        laidWidth = -1;
        refit(Math.max(getWidth(), 360));
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::scrollToEnd);
        return card;
    }

    public void append(PeerChatStore.Entry entry) {
        if (entry == null || entry.kind == PeerChatStore.Kind.POKE) {
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
        int width = Math.max(getWidth(), 360);
        laidWidth = -1;
        refit(width);
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

    String sideOf(String snippet) {
        for (MessageView view : views) {
            if (view.contains(snippet)) {
                return view.side;
            }
        }
        return "";
    }

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

    static String copyPayload(PeerChatStore.Entry entry) {
        if (entry == null) {
            return "";
        }
        return entry.text == null ? "" : entry.text;
    }

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
            outgoing = !file.status.startsWith("對方");
        }
        if (outgoing) {
            return "我";
        }
        String device = conversationId == null ? "" : conversationId;
        String name = entry.speaker == null || entry.speaker.isEmpty() ? device : entry.speaker;
        if (!entry.speaker.isEmpty() && !device.isEmpty() && !entry.speaker.equals(device)) {
            return name + " · " + device;
        }
        return name.isEmpty() ? "對方" : name;
    }

    static String messageTitle(PeerChatStore.Entry entry, String conversationId, ZoneId zone) {
        String source = sourceLabel(entry, conversationId);
        String when = formatTime(entry == null ? 0L : entry.timeMs, zone);
        if (source.isEmpty()) {
            return when;
        }
        return source + " · " + when;
    }

  /** 有縮排、stack trace、圍欄，或多行像程式碼的純文字。 */
    static boolean preserveLayout(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.contains("```")) {
            return true;
        }
        if (looksLikeSourceCode(text)) {
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

    static boolean looksLikeSourceCode(String text) {
        if (text == null || !text.contains("\n")) {
            return false;
        }
        String[] lines = text.split("\n", -1);
        if (lines.length < 2) {
            return false;
        }
        int keywordLines = 0;
        int punct = 0;
        int nonEmpty = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            nonEmpty++;
            if (trimmed.startsWith("package ")
                    || trimmed.startsWith("import ")
                    || trimmed.startsWith("public class ")
                    || trimmed.startsWith("public interface ")
                    || trimmed.startsWith("public enum ")) {
                keywordLines++;
            }
            for (int i = 0; i < trimmed.length(); i++) {
                char ch = trimmed.charAt(i);
                if (ch == '{' || ch == '}' || ch == ';') {
                    punct++;
                }
            }
        }
        if (keywordLines >= 2) {
            return true;
        }
        return nonEmpty >= 3 && punct >= nonEmpty;
    }

    static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private JPanel wrapLeft(JComponent block) {
        // 直傳卡片內容會在加入後才補齊（接收／拒收／取消列），
        // 不能用當下的 preferred height 鎖死 maximumSize，否則按鈕會被裁切。
        JPanel line = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        line.setOpaque(false);
        line.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.add(block, BorderLayout.CENTER);
        return line;
    }

    private void refit(int width) {
        if (width <= 0) {
            return;
        }
        if (width == laidWidth) {
            return;
        }
        laidWidth = width;
        int content = Math.max(120, width - 16);
        for (MessageView view : views) {
            view.fit(content);
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

    static String formatTime(long epochMs, ZoneId zone) {
        java.time.ZonedDateTime time = Instant.ofEpochMilli(epochMs).atZone(zone);
        boolean today = time.toLocalDate().equals(LocalDate.now(zone));
        return time.format(DateTimeFormatter.ofPattern(today ? "HH:mm" : "MM-dd HH:mm"));
    }

    private final class MessageView {
        private final PeerChatStore.Entry entry;
        private final String side;
        private final boolean preserved;
        private final JComponent row;
        private final JTextArea body;
        private boolean expanded;
        private JPanel codeShell;

        private MessageView(PeerChatStore.Entry entry) {
            this.entry = entry;
            this.preserved = entry.kind == PeerChatStore.Kind.MESSAGE
                    && preserveLayout(entry.text);
            if (entry.kind == PeerChatStore.Kind.POKE) {
                side = "";
            } else {
                side = "left";
            }
            body = buildBody();
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

        private void fit(int contentWidth) {
            if (body == null && codeShell == null) {
                lock(row);
                return;
            }
            if (preserved && body != null) {
                int lineH = body.getFontMetrics(body.getFont()).getHeight();
                int lines = countLines(body.getText());
                int showLines = expanded ? lines : Math.min(lines, CODE_COLLAPSE_LINES);
                int height = Math.max(lineH + 8, showLines * lineH + 12);
                if (codeShell != null) {
                    codeShell.setPreferredSize(new Dimension(contentWidth, height + 36));
                    codeShell.setMaximumSize(new Dimension(contentWidth, height + 36));
                }
            } else if (body != null) {
                body.setSize(new Dimension(contentWidth, Short.MAX_VALUE));
                Dimension pref = body.getPreferredSize();
                body.setPreferredSize(new Dimension(contentWidth, pref.height));
                body.setMaximumSize(new Dimension(contentWidth, pref.height));
            }
            lock(row);
        }

        private JComponent buildRow() {
            JPanel line = new JPanel(new BorderLayout());
            line.setOpaque(false);
            line.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            JComponent block = messageBlock();
            if ("left".equals(side)) {
                line.add(block, BorderLayout.CENTER);
            }
            return line;
        }

        private JComponent messageBlock() {
            JPanel panel = new JPanel(new BorderLayout(0, 2));
            panel.setOpaque(false);
            panel.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            panel.setBorder(new EmptyBorder(0, 0, 12, 0));
            panel.add(header(), BorderLayout.NORTH);
            FileNotice file = entry.kind == PeerChatStore.Kind.NOTE ? fileNotice(entry.text) : null;
            if (entry.kind == PeerChatStore.Kind.POKE) {
                panel.add(plainLabel(pokePhrase(), bodyFont, CandyTheme.MUTED),
                        BorderLayout.CENTER);
            } else if (file != null) {
                panel.add(fileCard(file), BorderLayout.CENTER);
            } else if (preserved) {
                panel.add(codeBlock(), BorderLayout.CENTER);
            } else if (body != null && entry.text != null && !entry.text.isEmpty()) {
                panel.add(body, BorderLayout.CENTER);
            }
            if (entry.hasImage()) {
                PeerGraffiti.Mode graffiti = PeerGraffiti.modeOf(entry.text);
                if (graffiti != null) {
                    panel.add(graffitiImages(entry.imageEncoded, graffiti), BorderLayout.SOUTH);
                } else {
                    panel.add(images(entry.imageEncoded), BorderLayout.SOUTH);
                }
            }
            return panel;
        }

        private JComponent header() {
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            String title = messageTitle(entry, peerId, ZoneId.systemDefault());
            JLabel label = plainLabel(title, bodyFont.deriveFont((float) Math.max(10, fontSize - 2)),
                    CandyTheme.MUTED);
            header.add(label, BorderLayout.WEST);
            return header;
        }

        private JTextArea buildBody() {
            if (entry.kind == PeerChatStore.Kind.POKE || entry.text == null || entry.text.isEmpty()
                    || PeerGraffiti.isTagOnly(entry.text)) {
                return null;
            }
            JTextArea area = new JTextArea(entry.text);
            area.setFont(preserved ? codeFont : bodyFont);
            area.setEditable(false);
            area.setOpaque(!preserved);
            if (preserved) {
                area.setBackground(CODE_BG);
                area.setForeground(CODE_INK);
            } else {
                area.setBackground(CandyTheme.PANEL);
                area.setForeground(CandyTheme.INK);
            }
            area.setCaretColor(area.getForeground());
            area.setLineWrap(true);
            area.setWrapStyleWord(false);
            area.setTabSize(4);
            area.setBorder(new EmptyBorder(4, 6, 4, 6));
            return area;
        }

        private JComponent graffitiImages(String encodedField, PeerGraffiti.Mode mode) {
            if (mode == PeerGraffiti.Mode.DIRECT) {
                return graffitiPicture(encodedField, false);
            }
            JPanel stack = new JPanel(new java.awt.CardLayout());
            stack.setOpaque(false);
            stack.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            stack.add(buildGraffitiSealedCard(stack), "sealed");
            stack.add(graffitiPicture(encodedField, true), "open");
            return stack;
        }

        private JComponent buildGraffitiSealedCard(JPanel stack) {
            JPanel card = new JPanel(new BorderLayout());
            card.setOpaque(true);
            card.setBackground(ClassicBevel.FACE);
            card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(ClassicBevel.DARK),
                    new EmptyBorder(10, 12, 10, 12)));
            card.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            JLabel label = plainLabel(PeerGraffiti.cardLabel(entry.outgoing), bodyFont, CandyTheme.INK);
            label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            card.add(label, BorderLayout.CENTER);
            MouseAdapter open = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    java.awt.LayoutManager lm = stack.getLayout();
                    if (lm instanceof java.awt.CardLayout) {
                        ((java.awt.CardLayout) lm).show(stack, "open");
                    }
                }
            };
            card.addMouseListener(open);
            label.addMouseListener(open);
            return card;
        }

        private JComponent graffitiPicture(String encodedField, boolean fromCard) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            row.setOpaque(false);
            row.putClientProperty(CandyPanel.MARK, Boolean.TRUE);
            List<String> images = ChatProtocol.splitPeerImages(encodedField);
            if (images == null || images.isEmpty()) {
                row.add(plainLabel("（圖片無法顯示）", bodyFont, CandyTheme.MUTED));
                return row;
            }
            String encoded = images.get(0);
            BufferedImage thumb = PeerImage.decodeThumbnail(encoded, THUMB_EDGE);
            if (thumb == null) {
                row.add(plainLabel("（圖片無法顯示）", bodyFont, CandyTheme.MUTED));
                return row;
            }
            JLabel icon = new JLabel(new ImageIcon(thumb));
            icon.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            icon.setToolTipText(fromCard ? "點一下放大" : "點一下放大");
            icon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (imageViewer != null) {
                        BufferedImage full = PeerImage.decode(encoded);
                        if (full != null) {
                            imageViewer.accept(full);
                        }
                    }
                }
            });
            row.add(icon);
            return row;
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
            codeShell = new JPanel(new BorderLayout(0, 4));
            codeShell.setOpaque(true);
            codeShell.setBackground(CODE_BG);
            codeShell.setBorder(new CompoundBorder(new LineBorder(CODE_BORDER), new EmptyBorder(6, 6, 6, 6)));
            codeShell.putClientProperty(CandyPanel.MARK, Boolean.TRUE);

            JPanel top = new JPanel(new BorderLayout(0, 0));
            top.setOpaque(false);
            String caption = isStackTrace(entry.text) ? "錯誤訊息" : "程式碼";
            JLabel label = plainLabel(caption, bodyFont.deriveFont((float) Math.max(11, fontSize - 2)), CODE_INK);
            top.add(label, BorderLayout.WEST);

            CandyButton copy = new CandyButton("複製", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
            copy.setFont(bodyFont.deriveFont((float) Math.max(11, fontSize - 2)));
            copy.addActionListener(e -> {
                String payload = copyPayload(entry);
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(payload), null);
                copy.setText("已複製");
                Timer reset = new Timer(1200, ev -> copy.setText("複製"));
                reset.setRepeats(false);
                reset.start();
            });
            top.add(copy, BorderLayout.EAST);
            codeShell.add(top, BorderLayout.NORTH);
            codeShell.add(body, BorderLayout.CENTER);

            int lines = countLines(entry.text);
            if (lines > CODE_COLLAPSE_LINES) {
                JPanel fold = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                fold.setOpaque(false);
                CandyButton toggle = new CandyButton("展開", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
                toggle.setFont(bodyFont.deriveFont((float) Math.max(11, fontSize - 2)));
                toggle.addActionListener(e -> {
                    expanded = !expanded;
                    toggle.setText(expanded ? "收合" : "展開");
                    laidWidth = -1;
                    refit(Math.max(getWidth(), 360));
                    revalidate();
                    repaint();
                });
                fold.add(toggle);
                codeShell.add(fold, BorderLayout.SOUTH);
            }

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(codeShell, BorderLayout.CENTER);
            return wrap;
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
                JLabel size = plainLabel(file.size, bodyFont, CandyTheme.MUTED);
                size.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(size);
            }
            JLabel status = plainLabel(file.status, bodyFont, CandyTheme.MUTED);
            status.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(status);
            return card;
        }
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
}