package com.example.ui;

import com.example.service.PeerChatStore;
import com.example.service.PeerImage;

import javax.swing.ImageIcon;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * 聊天視窗的對話區：自己的訊息靠右、同事的靠左，戳一下與系統提示置中。文字可選取複製，雙擊圖片可放大／另存。
 */
public class PeerChatTranscript extends JTextPane {

    static final int THUMB_EDGE = 320;
    private static final String IMAGE_ATTR = "peerImage";

    private static final Color SELF_COLOR = new Color(30, 64, 175);
    private static final Color PEER_COLOR = new Color(21, 128, 61);
    private static final Color POKE_COLOR = new Color(194, 65, 12);
    private static final Color NOTE_COLOR = new Color(100, 116, 139);

    private final String peerId;
    private final String fontFamily;
    private final int fontSize;
    private Consumer<BufferedImage> imageViewer;

    public PeerChatTranscript(String peerId, String fontFamily, int fontSize) {
        this.peerId = peerId == null ? "" : peerId;
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        setEditable(false);
        setOpaque(true);
        setBackground(Color.WHITE);
        setMargin(new java.awt.Insets(8, 10, 8, 10));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openImageAt(e.getPoint());
                }
            }
        });
    }

    /** 雙擊圖片時要做什麼（通常是開一個可另存的檢視視窗）。 */
    public void setImageViewer(Consumer<BufferedImage> viewer) {
        this.imageViewer = viewer;
    }

    public void append(PeerChatStore.Entry entry) {
        if (entry == null) {
            return;
        }
        StyledDocument doc = getStyledDocument();
        try {
            int start = doc.getLength();
            int align = StyleConstants.ALIGN_LEFT;
            if (entry.kind == PeerChatStore.Kind.NOTE) {
                align = StyleConstants.ALIGN_CENTER;
                insert(doc, formatTime(entry.timeMs, ZoneId.systemDefault()) + "  " + entry.text + "\n",
                        style(NOTE_COLOR, fontSize - 2, false));
            } else {
                align = entry.outgoing ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT;
                String who = entry.outgoing ? "我" : (entry.speaker.isEmpty() ? peerId : entry.speaker);
                insert(doc, who + "  " + formatTime(entry.timeMs, ZoneId.systemDefault()) + "\n",
                        style(entry.outgoing ? SELF_COLOR : PEER_COLOR, fontSize - 2, true));
                if (entry.kind == PeerChatStore.Kind.POKE) {
                    insert(doc, (entry.outgoing ? "你戳了對方一下" : "戳了你一下") + "\n",
                            style(POKE_COLOR, fontSize, false));
                } else {
                    if (!entry.text.isEmpty()) {
                        insert(doc, entry.text + "\n", style(Color.BLACK, fontSize, false));
                    }
                    if (entry.hasImage()) {
                        insertImage(doc, entry.imageEncoded);
                    }
                }
            }
            SimpleAttributeSet para = new SimpleAttributeSet();
            StyleConstants.setAlignment(para, align);
            doc.setParagraphAttributes(start, doc.getLength() - start, para, false);

            SimpleAttributeSet gap = new SimpleAttributeSet();
            StyleConstants.setAlignment(gap, align);
            StyleConstants.setSpaceBelow(gap, 8f);
            doc.setParagraphAttributes(Math.max(start, doc.getLength() - 1), 1, gap, false);
        } catch (BadLocationException ignored) {
            // 只會在文件被同時改動時發生，這裡只往尾端加，不會發生
        }
        setCaretPosition(doc.getLength());
    }

    public void clear() {
        setText("");
    }

    private void insertImage(StyledDocument doc, String encoded) throws BadLocationException {
        BufferedImage thumb = PeerImage.decodeThumbnail(encoded, THUMB_EDGE);
        if (thumb == null) {
            insert(doc, "（圖片無法顯示）\n", style(NOTE_COLOR, fontSize, false));
            return;
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setIcon(attrs, new ImageIcon(thumb));
        attrs.addAttribute(IMAGE_ATTR, encoded);
        doc.insertString(doc.getLength(), " ", attrs);
        doc.insertString(doc.getLength(), "\n", style(Color.BLACK, fontSize, false));
    }

    private void openImageAt(Point point) {
        if (imageViewer == null) {
            return;
        }
        int pos = viewToModel2D(point);
        if (pos < 0) {
            return;
        }
        StyledDocument doc = getStyledDocument();
        // 點到圖片左右邊緣時 pos 可能落在相鄰一格，兩格都找
        for (int p : new int[]{pos, pos - 1}) {
            if (p < 0 || p >= doc.getLength()) {
                continue;
            }
            Object encoded = doc.getCharacterElement(p).getAttributes().getAttribute(IMAGE_ATTR);
            if (encoded instanceof String) {
                BufferedImage full = PeerImage.decode((String) encoded);
                if (full != null) {
                    imageViewer.accept(full);
                }
                return;
            }
        }
    }

    private void insert(StyledDocument doc, String text, AttributeSet attrs) throws BadLocationException {
        doc.insertString(doc.getLength(), text, attrs);
    }

    private SimpleAttributeSet style(Color color, int size, boolean bold) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, fontFamily);
        StyleConstants.setFontSize(attrs, size);
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setForeground(attrs, color);
        return attrs;
    }

    /** 今天只顯示時間，其他日期加上月日。 */
    static String formatTime(long epochMs, ZoneId zone) {
        java.time.ZonedDateTime time = Instant.ofEpochMilli(epochMs).atZone(zone);
        boolean today = time.toLocalDate().equals(LocalDate.now(zone));
        return time.format(DateTimeFormatter.ofPattern(today ? "HH:mm:ss" : "MM-dd HH:mm:ss"));
    }
}
