package com.example.ui;

import com.example.service.PeerChatStore;
import com.example.service.PeerImage;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerChatTranscriptTest {

    @Test
    public void rendersIncomingAndOutgoingWithAlignment() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("John Wick", "SansSerif", 13);
            t.setSize(480, 320);
            t.append(store.addMessage("John Wick", false, "你好\n第二行", "", 1_000L));
            t.append(store.addMessage("John Wick", true, "收到", "", 2_000L));
            t.append(store.addMessage("John Wick", false, "店長說", "", 1_500L, "小王"));

            assertEquals("left", t.sideOf("你好"));
            assertEquals("right", t.sideOf("收到"));
            assertEquals("left", t.sideOf("店長說"));
            assertEquals("你好\n第二行", t.bodyOf("你好"));
            assertTrue(t.sideOf("小王").equals("left"));
            assertEquals("本機", PeerChatTranscript.sourceLabel(
                    store.addMessage("John Wick", true, "x", "", 2_000L), "John Wick"));
            assertEquals("小王 · John Wick", PeerChatTranscript.sourceLabel(
                    store.addMessage("John Wick", false, "店長說", "", 1_500L, "小王"), "John Wick"));
            assertTrue(t.bodyOf("你好").contains("第二行"));
            assertFalse(t.preservesLayout("你好"));
        });
    }

    @Test
    public void rendersPokeAndNote() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(480, 320);
            t.append(store.addPoke("alice", false, 1_000L));
            t.append(store.addPoke("alice", true, 2_000L));
            t.append(store.addNote("alice", "已直傳「a.txt」", 3_000L));

            assertEquals("center", t.sideOf("戳了你一下"));
            assertEquals("center", t.sideOf("你戳了對方一下"));
            assertEquals("right", t.sideOf("已直傳"));
            assertEquals("本機", PeerChatTranscript.sourceLabel(store.addNote("alice", "已直傳「b.txt」", 4_000L), "alice"));
            assertEquals("alice", PeerChatTranscript.sourceLabel(store.addNote("alice", "對方傳來「b.txt」", 5_000L), "alice"));
            assertEquals("", PeerChatTranscript.sourceLabel(store.addNote("alice", "訊息沒有送出", 6_000L), "alice"));
            assertEquals("已直傳「a.txt」", t.bodyOf("已直傳"));
        });
    }

    @Test
    public void embedsImageAndSurvivesBrokenImage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(480, 320);
            String encoded;
            try {
                encoded = PeerImage.encode(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB));
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
            t.append(store.addMessage("alice", false, "", encoded, 1_000L));
            assertTrue("圖片要以縮圖放進氣泡", hasIcon(t));

            t.append(store.addMessage("alice", false, "壞圖", "not-an-image", 2_000L));
            assertEquals("壞圖", t.bodyOf("壞圖"));
            assertTrue(treeText(t).contains("（圖片無法顯示）"));
        });
    }

    @Test
    public void keepsCodeIndentAndOffersTheOriginalTextForCopy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String trace = "java.lang.IllegalStateException: boom\n"
                    + "    at com.example.App.main(App.java:10)\n"
                    + "\tstill indented";
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(480, 400);
            PeerChatStore.Entry entry = store.addMessage("alice", false, trace, "", 1_000L);
            t.append(entry);
            assertTrue(t.preservesLayout("IllegalStateException"));
            assertEquals(trace, t.bodyOf("IllegalStateException"));
            assertEquals(trace, PeerChatTranscript.copyPayload(entry));
            assertTrue(PeerChatTranscript.preserveLayout(trace));
            assertFalse(PeerChatTranscript.preserveLayout("你好\n第二行"));
        });
    }

    @Test
    public void developerModeKeepsPlainTextAsCodeAndFileNotesShowNameAndSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(520, 360);
            t.append(store.addMessage("alice", true, "int x = 1;", "", 1_000L));
            assertFalse(t.preservesLayout("int x"));
            t.setDeveloperMode(true);
            assertTrue(t.isDeveloperMode());
            assertTrue(t.preservesLayout("int x"));
            assertEquals("int x = 1;", t.bodyOf("int x"));
            assertTrue(t.usesCodeFont("int x"));

            PeerChatTranscript.FileNotice notice = PeerChatTranscript.fileNotice(
                    "對方傳來「notes.txt」（12 KB）");
            assertEquals("notes.txt", notice.name);
            assertEquals("12 KB", notice.size);
            assertEquals("對方傳來", notice.status);
            assertEquals(null, PeerChatTranscript.fileNotice("訊息沒有送出"));
            t.append(store.addNote("alice", "對方傳來「notes.txt」（12 KB）", 2_000L));
            assertEquals("left", t.sideOf("notes.txt"));
            assertTrue(treeText(t).contains("檔案  notes.txt"));
            assertTrue(treeText(t).contains("12 KB"));
        });
    }

    @Test
    public void clearEmptiesTranscript() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.append(store.addMessage("alice", false, "hi", "", 1L));
            t.clear();
            assertEquals("", t.sideOf("hi"));
            assertEquals(0, t.getComponentCount());
        });
    }

    @Test
    public void formatsTodayAsTimeOnlyAndOtherDaysWithDate() {
        ZoneId zone = ZoneOffset.UTC;
        long now = System.currentTimeMillis();
        assertTrue(PeerChatTranscript.formatTime(now, zone).matches("\\d{2}:\\d{2}:\\d{2}"));
        long longAgo = 86_400_000L * 400;
        assertTrue(PeerChatTranscript.formatTime(longAgo, zone).matches("\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    private static boolean hasIcon(Component component) {
        if (component instanceof JLabel && ((JLabel) component).getIcon() != null) {
            return true;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (hasIcon(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String treeText(Component component) {
        StringBuilder text = new StringBuilder();
        collect(component, text);
        return text.toString();
    }

    private static void collect(Component component, StringBuilder text) {
        if (component instanceof JLabel) {
            text.append(((JLabel) component).getText());
        }
        if (component instanceof javax.swing.text.JTextComponent) {
            text.append(((javax.swing.text.JTextComponent) component).getText());
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, text);
            }
        }
    }
}
