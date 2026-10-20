package com.example.ui;

import com.example.service.PeerChatStore;
import com.example.service.PeerImage;
import org.junit.Test;

import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PeerChatTranscriptTest {

    private static String text(PeerChatTranscript t) throws Exception {
        StyledDocument doc = t.getStyledDocument();
        return doc.getText(0, doc.getLength());
    }

    @Test
    public void rendersIncomingAndOutgoingWithAlignment() throws Exception {
        PeerChatStore store = new PeerChatStore();
        PeerChatTranscript t = new PeerChatTranscript("John Wick", "SansSerif", 13);
        t.append(store.addMessage("John Wick", false, "你好\n第二行", "", 1_000L));
        t.append(store.addMessage("John Wick", true, "收到", "", 2_000L));

        String shown = text(t);
        assertTrue(shown, shown.contains("John Wick"));
        assertTrue(shown.contains("你好\n第二行"));
        assertTrue(shown.contains("我"));
        assertTrue(shown.contains("收到"));

        StyledDocument doc = t.getStyledDocument();
        int incomingPos = shown.indexOf("你好");
        int outgoingPos = shown.indexOf("收到");
        assertEquals(StyleConstants.ALIGN_LEFT,
                StyleConstants.getAlignment(doc.getParagraphElement(incomingPos).getAttributes()));
        assertEquals(StyleConstants.ALIGN_RIGHT,
                StyleConstants.getAlignment(doc.getParagraphElement(outgoingPos).getAttributes()));
    }

    @Test
    public void rendersPokeAndNote() throws Exception {
        PeerChatStore store = new PeerChatStore();
        PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
        t.append(store.addPoke("alice", false, 1_000L));
        t.append(store.addPoke("alice", true, 2_000L));
        t.append(store.addNote("alice", "已直傳「a.txt」", 3_000L));

        String shown = text(t);
        assertTrue(shown.contains("戳了你一下"));
        assertTrue(shown.contains("你戳了對方一下"));
        assertTrue(shown.contains("已直傳「a.txt」"));
        int notePos = shown.indexOf("已直傳");
        assertEquals(StyleConstants.ALIGN_CENTER,
                StyleConstants.getAlignment(t.getStyledDocument().getParagraphElement(notePos).getAttributes()));
    }

    @Test
    public void embedsImageAndSurvivesBrokenImage() throws Exception {
        PeerChatStore store = new PeerChatStore();
        PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
        String encoded = PeerImage.encode(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB));
        t.append(store.addMessage("alice", false, "", encoded, 1_000L));

        StyledDocument doc = t.getStyledDocument();
        boolean sawIcon = false;
        for (int i = 0; i < doc.getLength(); i++) {
            if (StyleConstants.getIcon(doc.getCharacterElement(i).getAttributes()) != null) {
                sawIcon = true;
            }
        }
        assertTrue("圖片要以圖示嵌進對話區", sawIcon);

        t.append(store.addMessage("alice", false, "壞圖", "not-an-image", 2_000L));
        assertTrue(text(t).contains("（圖片無法顯示）"));
    }

    @Test
    public void clearEmptiesTranscript() throws Exception {
        PeerChatStore store = new PeerChatStore();
        PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
        t.append(store.addMessage("alice", false, "hi", "", 1L));
        t.clear();
        assertEquals("", text(t));
    }

    @Test
    public void formatsTodayAsTimeOnlyAndOtherDaysWithDate() {
        ZoneId zone = ZoneOffset.UTC;
        long now = System.currentTimeMillis();
        assertTrue(PeerChatTranscript.formatTime(now, zone).matches("\\d{2}:\\d{2}:\\d{2}"));
        long longAgo = 86_400_000L * 400;
        assertTrue(PeerChatTranscript.formatTime(longAgo, zone).matches("\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }
}
