package com.example.ui;

import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileTransferDialogTest {

    @Test
    public void showRequiresEdt() {
        try {
            FileTransferDialog.show(null, List.of());
            fail("非 EDT 不該打開視窗");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("EDT"));
        }
    }

    @Test
    public void kindAndStatusUseRealFieldsOnly() {
        assertEquals("文字檔", FileTransferDialog.kindLabel("error_log.txt"));
        assertEquals("原始碼", FileTransferDialog.kindLabel("Main.java"));
        assertEquals("圖片", FileTransferDialog.kindLabel("截圖.png"));
        assertEquals("檔案", FileTransferDialog.kindLabel("notes"));
        assertEquals("在線", FriendListItem.statusLine(true, "  "));
        assertEquals("在線 · 2.0.2", FriendListItem.statusLine(true, "2.0.2"));
        assertEquals("離線", FriendListItem.statusLine(false, null));
        assertEquals("A", FriendListItem.initial("aa123"));
        assertEquals("洋", FriendListItem.initial("洋流"));
        assertEquals("找不到檔案", FileTransferDialog.rejection(Path.of("missing-punchclock-file.txt")));
    }

    @Test
    public void offlineStaysGrayAndCannotBeChosen() throws Exception {
        withDialog(sampleFriends(), dialog -> {
            assertEquals(List.of("alpha", "beta", "guest"), dialog.visibleClientIds());
            assertNull(dialog.selectedClientId());
            assertTrue(dialog.selectClient("beta"));
            assertEquals("beta", dialog.selectedClientId());
            assertFalse(dialog.selectClient("guest"));
            assertEquals("beta", dialog.selectedClientId());
            assertFalse(dialog.selectClient("nobody"));
            assertFalse(dialog.canSend());
        });
    }

    @Test
    public void oneFriendIsShownAndPreselectedWhenOnline() throws Exception {
        List<FileTransferDialog.Friend> friends = List.of(
                new FileTransferDialog.Friend("solo", true, "", ""));
        withDialog(friends, dialog -> {
            assertEquals(List.of("solo"), dialog.visibleClientIds());
            assertEquals("solo", dialog.selectedClientId());
            assertTrue(dialog.getWidth() >= 640);
            assertTrue(dialog.getHeight() >= 480);
            assertTrue(dialog.getWidth() <= 900);
            assertTrue(dialog.getHeight() <= 650);
        });
    }

    @Test
    public void searchFiltersByClientIdAndListScrolls() throws Exception {
        List<FileTransferDialog.Friend> friends = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            friends.add(new FileTransferDialog.Friend("peer-" + i, true, i == 3 ? "1.4" : "", ""));
        }
        friends.add(new FileTransferDialog.Friend("離線的人", false, "", ""));
        withDialog(friends, dialog -> {
            assertEquals(13, dialog.visibleClientIds().size());
            assertTrue(dialog.visibleClientIds().get(12).equals("離線的人"));
            dialog.setQuery("PEER-1");
            List<String> matched = dialog.visibleClientIds();
            assertTrue(matched.contains("peer-1"));
            assertTrue(matched.contains("peer-10"));
            assertTrue(matched.contains("peer-11"));
            assertFalse(matched.contains("peer-2"));
            assertFalse(matched.contains("離線的人"));
            dialog.setQuery("");
            assertEquals(13, dialog.visibleClientIds().size());
            dialog.setQuery("沒有這個人");
            assertTrue(dialog.visibleClientIds().isEmpty());
        });
    }

    @Test
    public void filesCanBeAddedRemovedAndCancelSendsNothing() throws Exception {
        Path dir = Files.createTempDirectory("punchclock-transfer");
        Path file = dir.resolve("Main.java");
        Files.writeString(file, "class Main {}\n");
        Path empty = dir.resolve("empty.txt");
        Files.writeString(empty, "");
        try {
            withDialog(sampleFriends(), dialog -> {
                assertEquals(0, dialog.addPaths(List.of(empty), false));
                assertEquals(2, dialog.addPaths(List.of(file, dir), false));
                assertEquals(2, dialog.chosenPaths().size());
                assertEquals(0, dialog.addPaths(List.of(file), false));
                assertFalse(dialog.canSend());
                assertTrue(dialog.selectClient("beta"));
                assertTrue(dialog.canSend());
                List<String> described = dialog.chosenDescriptions();
                assertTrue(described.get(0).startsWith("Main.java|"));
                assertTrue(described.get(0).contains("原始碼"));
                assertTrue(described.get(1).contains("資料夾"));
                dialog.setNote("  晚點看  ");
                assertTrue(dialog.removeChosen(file));
                assertEquals(1, dialog.chosenPaths().size());
                FileTransferDialog.Result picked = dialog.confirmSelection();
                assertEquals("beta", picked.clientId());
                assertEquals(List.of(dir.toAbsolutePath().normalize()), picked.files());
                assertEquals("晚點看", picked.note());
                dialog.clearChosen();
                assertEquals(1, picked.files().size());
            });
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(empty);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void cancelClearsResultAndTempImages() throws Exception {
        withDialog(sampleFriends(), dialog -> {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            Path png = dialog.writeTempPng(image, "截圖");
            assertTrue(Files.exists(png));
            dialog.addTemporaryImage(png);
            assertEquals(1, dialog.chosenPaths().size());
            dialog.cancelSelection();
            assertFalse(Files.exists(png));
            assertNull(dialog.confirmSelection());
        });
    }

    @Test
    public void layoutKeepsSendButtonInsideMinimumWindow() throws Exception {
        Assume.assumeFalse("headless JVM cannot layout a dialog", GraphicsEnvironment.isHeadless());
        withDialog(sampleFriends(), dialog -> {
            dialog.layoutForTest(dialog.getMinimumSize().width, dialog.getMinimumSize().height);
            JButton send = dialog.sendButton();
            assertTrue(send.getWidth() > 40);
            assertTrue(send.getHeight() > 20);
            assertTrue(send.getX() >= 0);
            assertTrue(send.getY() >= 0);
            java.awt.Point point = SwingUtilities.convertPoint(send.getParent(), send.getLocation(), dialog.getContentPane());
            assertTrue(point.x + send.getWidth() <= dialog.getContentPane().getWidth() + 2);
            assertTrue(point.y + send.getHeight() <= dialog.getContentPane().getHeight() + 2);
            dialog.layoutForTest(900, 650);
            int width = dialog.splitWidth();
            int divider = dialog.dividerLocation();
            assertTrue(width > 500);
            assertTrue(divider > width * 0.28);
            assertTrue(divider < width * 0.45);
        });
    }

    @Test
    public void pastedImageJoinsTheFileList() throws Exception {
        withDialog(sampleFriends(), dialog -> {
            BufferedImage image = new BufferedImage(12, 10, BufferedImage.TYPE_INT_ARGB);
            Path png = dialog.writeTempPng(image, "貼上圖片");
            try {
                assertTrue(Files.size(png) > 0);
                BufferedImage read = ImageIO.read(png.toFile());
                assertEquals(12, read.getWidth());
                assertEquals(1, dialog.addPaths(List.of(png), false));
                assertTrue(dialog.chosenDescriptions().get(0).contains("圖片"));
            } finally {
                dialog.clearChosen();
                Files.deleteIfExists(png);
            }
        });
    }

    private static List<FileTransferDialog.Friend> sampleFriends() {
        return List.of(
                new FileTransferDialog.Friend("alpha", true, "", ""),
                new FileTransferDialog.Friend("guest", false, "0.9", ""),
                new FileTransferDialog.Friend("beta", true, "2.0.2", ""),
                new FileTransferDialog.Friend("", true, "x", ""),
                new FileTransferDialog.Friend("alpha", true, "dup", ""));
    }

    private static void withDialog(List<FileTransferDialog.Friend> friends, DialogCheck check) throws Exception {
        Assume.assumeFalse("headless JVM cannot create the transfer dialog", GraphicsEnvironment.isHeadless());
        FileTransferDialog[] holder = new FileTransferDialog[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = newDialog(friends));
        Exception[] failure = new Exception[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    check.check(holder[0]);
                } catch (Exception ex) {
                    failure[0] = ex;
                }
            });
            if (failure[0] != null) {
                throw failure[0];
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                holder[0].cancelSelection();
                holder[0].dispose();
            });
        }
    }

    private static FileTransferDialog newDialog(List<FileTransferDialog.Friend> friends) {
        return new FileTransferDialog(null, friends, null);
    }

    private interface DialogCheck {
        void check(FileTransferDialog dialog) throws Exception;
    }
}
