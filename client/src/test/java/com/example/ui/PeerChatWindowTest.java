package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerChatWindowTest {

    @Test
    public void pureImagesRouteToMessageAttachments() throws Exception {
        Assume.assumeFalse("headless JVM cannot create the chat window", GraphicsEnvironment.isHeadless());
        Path dir = Files.createTempDirectory("punch-chat-images");
        Path png = dir.resolve("a.png");
        Path jpg = dir.resolve("b.jpg");
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", png.toFile());
        ImageIO.write(image, "jpg", jpg.toFile());
        PeerChatWindow window = new PeerChatWindow("bob", host(new AtomicReference<>(), new boolean[]{true},
                new boolean[]{false}), null, List.of(), false);
        try {
            window.setPeerOnline(true);
            window.routeIncomingPaths(List.of(png, jpg));
            waitForAttachedImages(window);
            assertTrue(window.pendingFiles().isEmpty());
            assertFalse(window.attachedImage().isEmpty());
        } finally {
            window.dispose();
            Files.deleteIfExists(png);
            Files.deleteIfExists(jpg);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void mixedImagesAndFilesGoToPendingQueue() throws Exception {
        Assume.assumeFalse("headless JVM cannot create the chat window", GraphicsEnvironment.isHeadless());
        Path dir = Files.createTempDirectory("punch-chat-mix");
        Path png = dir.resolve("shot.png");
        Path note = dir.resolve("note.txt");
        BufferedImage image = new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", png.toFile());
        Files.writeString(note, "mix");
        PeerChatWindow window = new PeerChatWindow("bob", host(new AtomicReference<>(), new boolean[]{true},
                new boolean[]{false}), null, List.of(), false);
        try {
            window.setPeerOnline(true);
            window.routeIncomingPaths(List.of(png, note));
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(2, window.pendingFiles().size());
            assertTrue(window.attachedImage().isEmpty());
        } finally {
            window.dispose();
            Files.deleteIfExists(png);
            Files.deleteIfExists(note);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void singleFilePendingSummaryDoesNotMentionZip() throws Exception {
        Assume.assumeFalse("headless JVM cannot create the chat window", GraphicsEnvironment.isHeadless());
        Path dir = Files.createTempDirectory("punch-chat-single");
        Path note = dir.resolve("only.txt");
        Files.writeString(note, "one");
        PeerChatWindow window = new PeerChatWindow("bob", host(new AtomicReference<>(), new boolean[]{true},
                new boolean[]{false}), null, List.of(), false);
        try {
            window.setPeerOnline(true);
            window.routeIncomingPaths(List.of(note));
            assertEquals(1, window.pendingFiles().size());
            assertFalse(labelText(window).contains("ZIP"));
        } finally {
            window.dispose();
            Files.deleteIfExists(note);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void oversizedFolderRejectedWhenAddedToQueue() throws Exception {
        Assume.assumeFalse("headless JVM cannot create the chat window", GraphicsEnvironment.isHeadless());
        Path root = Files.createTempDirectory("punch-chat-fat-dir");
        Path heavy = root.resolve("heavy.bin");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(heavy.toFile(), "rw")) {
            raf.setLength(com.example.PeerFileRules.MAX_BYTES + 1L);
        }
        PeerChatWindow window = new PeerChatWindow("bob", host(new AtomicReference<>(), new boolean[]{true},
                new boolean[]{false}), null, List.of(), false);
        try {
            window.setPeerOnline(true);
            window.routeIncomingPaths(List.of(root));
            assertTrue(window.pendingFiles().isEmpty());
        } finally {
            window.dispose();
            Files.deleteIfExists(heavy);
            Files.deleteIfExists(root);
        }
    }

    @Test
    public void queuedFilesStayUntilSendStarts() throws Exception {
        Assume.assumeFalse("headless JVM cannot create the chat window", GraphicsEnvironment.isHeadless());
        Path dir = Files.createTempDirectory("punch-chat-files");
        Path note = dir.resolve("note.txt");
        Path again = dir.resolve("note.txt");
        Path folder = dir.resolve("pics");
        Files.writeString(note, "hello");
        Files.createDirectory(folder);
        AtomicReference<List<Path>> sent = new AtomicReference<>();
        boolean[] allowSend = {true};
        boolean[] messageSent = {false};
        PeerChatWindow window = new PeerChatWindow("bob", host(sent, allowSend, messageSent), null, List.of(), false);
        try {
            window.setPeerOnline(true);
            window.addPendingFiles(List.of(note, again, folder));
            assertEquals(2, window.pendingFiles().size());
            assertTrue(labelText(window).contains("note.txt"));
            assertTrue(labelText(window).contains("資料夾"));
            assertTrue(labelText(window).contains("打包成一個 ZIP"));

            Path otherDir = Files.createTempDirectory("punch-chat-dup");
            Path caseDir = Files.createTempDirectory("punch-chat-case");
            Path sameName = otherDir.resolve("note.txt");
            Path upperName = caseDir.resolve("NOTE.TXT");
            Path sameFolder = otherDir.resolve("pics");
            Files.writeString(sameName, "other");
            Files.writeString(upperName, "CASE");
            Files.createDirectory(sameFolder);
            try {
                window.addPendingFiles(List.of(sameName, upperName, sameFolder));
                assertEquals("同名項目要擋下", 2, window.pendingFiles().size());
            } finally {
                Files.deleteIfExists(sameName);
                Files.deleteIfExists(upperName);
                Files.deleteIfExists(sameFolder);
                Files.deleteIfExists(otherDir);
                Files.deleteIfExists(caseDir);
            }

            removeButtons(window).get(0).doClick();
            assertEquals(1, window.pendingFiles().size());
            assertFalse(labelText(window).contains("note.txt"));
            assertTrue(labelText(window).contains("資料夾"));
            window.addPendingFiles(List.of(note));
            assertEquals(2, window.pendingFiles().size());

            allowSend[0] = false;
            window.send();
            assertEquals(2, window.pendingFiles().size());
            assertFalse(messageSent[0]);

            allowSend[0] = true;
            window.send();
            assertTrue(window.pendingFiles().isEmpty());
            assertEquals(2, sent.get().size());
            assertFalse(labelText(window).contains("note.txt"));

            window.setPeerOnline(false);
            window.addPendingFiles(List.of(note));
            window.send();
            assertTrue("離線不能把檔案排進去，也不能送出", window.pendingFiles().isEmpty());
            assertFalse(messageSent[0]);
        } finally {
            window.dispose();
            Files.deleteIfExists(note);
            Files.deleteIfExists(folder);
            Files.deleteIfExists(dir);
        }
    }

    private static PeerChatWindow.Host host(AtomicReference<List<Path>> sent, boolean[] allowSend,
                                             boolean[] messageSent) {
        return new PeerChatWindow.Host() {
            @Override
            public boolean canSend(String peerId) {
                return true;
            }

            @Override
            public void sendMessage(String peerId, String text, String imageEncoded,
                                     java.util.function.Consumer<Boolean> done) {
                messageSent[0] = true;
                done.accept(true);
            }

            @Override
            public boolean sendQueuedFiles(String peerId, Component owner, List<Path> files) {
                if (!allowSend[0]) {
                    return false;
                }
                sent.set(new ArrayList<>(files));
                return true;
            }

            @Override
            public void chatClosed(PeerChatWindow window) {
            }
        };
    }

    private static List<JButton> removeButtons(PeerChatWindow window) {
        List<JButton> buttons = new ArrayList<>();
        for (Component component : flatten(window.getContentPane())) {
            if (component instanceof JButton && "移除此項".equals(((JButton) component).getToolTipText())) {
                buttons.add((JButton) component);
            }
        }
        return buttons;
    }

    private static String labelText(PeerChatWindow window) {
        StringBuilder text = new StringBuilder();
        for (Component component : flatten(window.getContentPane())) {
            if (component instanceof JLabel) {
                text.append(((JLabel) component).getText()).append('\n');
            }
        }
        return text.toString();
    }

    private static void waitForAttachedImages(PeerChatWindow window) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (window.attachedImage().isEmpty() && System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> {});
            Thread.sleep(25L);
        }
    }

    private static List<Component> flatten(Component root) {
        List<Component> list = new ArrayList<>();
        list.add(root);
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                list.addAll(flatten(child));
            }
        }
        return list;
    }
}
