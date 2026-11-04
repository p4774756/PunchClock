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

import javax.swing.JButton;
import javax.swing.JLabel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerChatWindowTest {

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
            public void poke(String peerId) {
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
