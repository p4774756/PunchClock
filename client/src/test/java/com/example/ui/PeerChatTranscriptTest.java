package com.example.ui;

import com.example.PeerGraffiti;
import com.example.service.PeerChatStore;
import com.example.service.PeerImage;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
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
            assertEquals("left", t.sideOf("收到"));
            assertEquals("left", t.sideOf("店長說"));
            assertEquals("你好\n第二行", t.bodyOf("你好"));
            assertTrue(t.sideOf("小王").equals("left"));
            assertEquals("我", PeerChatTranscript.sourceLabel(
                    store.addMessage("John Wick", true, "x", "", 2_000L), "John Wick"));
            assertEquals("小王 · John Wick", PeerChatTranscript.sourceLabel(
                    store.addMessage("John Wick", false, "店長說", "", 1_500L, "小王"), "John Wick"));
            assertTrue(t.bodyOf("你好").contains("第二行"));
            assertFalse(t.preservesLayout("你好"));
        });
    }

    @Test
    public void skipsLegacyPokeAndRendersNote() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(480, 320);
            try {
                t.append(legacyPokeEntry("alice", false, 1_000L));
                t.append(legacyPokeEntry("alice", true, 2_000L));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            t.append(store.addNote("alice", "已直傳「a.txt」", 3_000L));

            assertEquals("", t.sideOf("戳了你一下"));
            assertEquals("", t.sideOf("你戳了對方一下"));
            assertEquals("left", t.sideOf("已直傳"));
            assertEquals("我", PeerChatTranscript.sourceLabel(store.addNote("alice", "已直傳「b.txt」", 4_000L), "alice"));
            assertEquals("alice", PeerChatTranscript.sourceLabel(store.addNote("alice", "對方傳來「b.txt」", 5_000L), "alice"));
            assertEquals("", PeerChatTranscript.sourceLabel(store.addNote("alice", "訊息沒有送出", 6_000L), "alice"));
            assertEquals("已直傳「a.txt」", t.bodyOf("已直傳"));
        });
    }

    @Test
    public void graffitiDirectSkipsTagText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            String encoded;
            try {
                encoded = PeerImage.encode(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB));
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
            t.append(store.addMessage("alice", false, PeerGraffiti.TAG_DIRECT, encoded, 1_000L));
            assertEquals("", t.bodyOf(PeerGraffiti.TAG_DIRECT));
            assertTrue(hasIcon(t));
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
    public void fileNotesShowNameAndSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(520, 360);
            t.append(store.addMessage("alice", true, "int x = 1;", "", 1_000L));
            assertFalse(t.preservesLayout("int x"));
            assertEquals("int x = 1;", t.bodyOf("int x"));

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
    public void shortOwnMessageShowsFullText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(480, 320);
            t.append(store.addMessage("alice", false, "早安！今天有什麼需要我幫忙的嗎？", "", 1_000L));
            t.append(store.addMessage("alice", true, "行程", "", 2_000L));
            t.append(store.addMessage("alice", false, "好的，已幫您確認行程。", "", 3_000L));
            t.doLayout();
            assertEquals("行程", t.bodyOf("行程"));
            assertFalse(hasCopyButton(t));
            JTextArea later = findArea(t, "好的，已幫您確認行程。");
            FontMetrics fm = later.getFontMetrics(later.getFont());
            int needed = fm.stringWidth("好的，已幫您確認行程。");
            assertTrue("later message clipped: size=" + later.getSize() + " needW=" + needed,
                    later.getWidth() >= needed || later.getHeight() >= fm.getHeight() * 2);
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
    public void detectsPlainJavaSourceAndShowsCopyOnCodeBlock() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String code = "package com.example.demo;\n"
                    + "import java.util.List;\n"
                    + "public class Demo {\n"
                    + "  void run() { System.out.println(1); }\n"
                    + "}";
            assertTrue(PeerChatTranscript.looksLikeSourceCode(code));
            assertTrue(PeerChatTranscript.preserveLayout(code));
            PeerChatStore store = new PeerChatStore();
            PeerChatTranscript t = new PeerChatTranscript("alice", "SansSerif", 13);
            t.setSize(520, 400);
            t.append(store.addMessage("alice", false, code, "", 1_000L));
            assertTrue(t.preservesLayout("package com.example"));
            assertTrue(hasCopyButton(t));
        });
    }

    @Test
    public void messageTitleUsesNameDotTime() {
        PeerChatStore store = new PeerChatStore();
        PeerChatStore.Entry entry = store.addMessage("alice", false, "hi", "", 3_600_000L);
        String title = PeerChatTranscript.messageTitle(entry, "alice", ZoneOffset.UTC);
        assertTrue(title.contains(" · "));
        assertFalse(title.contains("("));
    }

    @Test
    public void formatsTodayAsTimeOnlyAndOtherDaysWithDate() {
        ZoneId zone = ZoneOffset.UTC;
        long now = System.currentTimeMillis();
        assertTrue(PeerChatTranscript.formatTime(now, zone).matches("\\d{2}:\\d{2}"));
        long longAgo = 86_400_000L * 400;
        assertTrue(PeerChatTranscript.formatTime(longAgo, zone).matches("\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }

    private static PeerChatStore.Entry legacyPokeEntry(String peerId, boolean outgoing, long timeMs)
            throws Exception {
        Constructor<PeerChatStore.Entry> ctor = PeerChatStore.Entry.class.getDeclaredConstructor(
                String.class, boolean.class, PeerChatStore.Kind.class,
                String.class, String.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(peerId, outgoing, PeerChatStore.Kind.POKE, "", "", timeMs);
    }


    private static JTextArea findArea(Component component, String text) {
        if (component instanceof JTextArea) {
            JTextArea area = (JTextArea) component;
            if (text.equals(area.getText())) {
                return area;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JTextArea found = findArea(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean hasCopyButton(Component component) {
        if (component instanceof javax.swing.JButton) {
            String text = ((javax.swing.JButton) component).getText();
            if (text != null && text.startsWith("複製")) {
                return true;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (hasCopyButton(child)) {
                    return true;
                }
            }
        }
        return false;
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
