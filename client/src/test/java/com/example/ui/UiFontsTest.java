package com.example.ui;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UiFontsTest {

    @Test
    public void messageBodyKeepsLatinDigitsAndNewlines() {
        javax.swing.JTextArea area = UiFonts.messageBody(
                "2026-09-11 09:27:49\n【John Wick】傳來 eapp-backend.zip（16.3 MB）");

        assertEquals("2026-09-11 09:27:49\n【John Wick】傳來 eapp-backend.zip（16.3 MB）", area.getText());
        assertFalse(area.getText().contains("<html"));
        assertFalse(area.isEditable());
        assertFalse(area.isFocusable());
        assertEquals(420, area.getPreferredSize().width);
    }

    @Test
    public void messageBodyShowsMarkupAsText() {
        javax.swing.JTextArea area = UiFonts.messageBody("<script>alert(1)</script>");
        assertEquals("<script>alert(1)</script>", area.getText());
    }

    @Test
    public void messageBodyTreatsNullAsEmpty() {
        javax.swing.JTextArea area = UiFonts.messageBody(null);
        assertEquals("", area.getText());
    }

    @Test
    public void copyablePanelContainsMessageAndCopyAction() {
        javax.swing.JPanel panel = UiFonts.buildCopyablePanel("你好 PunchClock\n第二行");
        assertNotNull(panel);
        boolean sawArea = false;
        boolean sawCopy = false;
        for (java.awt.Component c : flatten(panel)) {
            if (c instanceof javax.swing.JTextArea) {
                sawArea = true;
                assertEquals("你好 PunchClock\n第二行", ((javax.swing.JTextArea) c).getText());
                assertFalse(((javax.swing.JTextArea) c).isEditable());
            }
            if (c instanceof javax.swing.JButton
                    && "複製文字".equals(((javax.swing.JButton) c).getText())) {
                sawCopy = true;
            }
        }
        assertTrue(sawArea);
        assertTrue(sawCopy);
    }

    @After
    public void clearRememberedDirectory() {
        UiFonts.setOpenDirectoryListener(null);
        UiFonts.setRememberedOpenDirectory(null);
    }

    @Test
    public void fileChooserOpensAtRememberedDirectory() throws Exception {
        Assume.assumeFalse("headless JVM cannot create a file chooser", GraphicsEnvironment.isHeadless());
        Path dir = Files.createTempDirectory("punch-chooser");
        Path missing = dir.resolve("missing");
        try {
            AtomicReference<Path> chosen = new AtomicReference<>();
            UiFonts.setOpenDirectoryListener(chosen::set);
            UiFonts.rememberOpenDirectory(dir.toFile());
            assertEquals(dir.toRealPath(), chosen.get().toRealPath());

            javax.swing.JFileChooser chooser = UiFonts.fileChooser();
            assertEquals(dir.toRealPath(), chooser.getCurrentDirectory().toPath().toRealPath());

            UiFonts.setRememberedOpenDirectory(missing.toString());
            javax.swing.JFileChooser reset = UiFonts.fileChooser();
            assertFalse(missing.toAbsolutePath().normalize().equals(
                    reset.getCurrentDirectory().toPath().toAbsolutePath().normalize()));
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void fileChooserExposesDetailsViewAction() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<JFileChooser> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> holder.set(UiFonts.fileChooser()));
        JFileChooser chooser = holder.get();
        Action details = chooser.getActionMap().get("viewTypeDetails");
        assertNotNull(details);
        Integer viewType = UiFonts.fileChooserViewTypeForTest(chooser);
        assertNotNull(viewType);
        assertEquals(Integer.valueOf(1), viewType);
    }

    @Test
    public void macFileChooserToolbarTipsAndReadableButtons() throws Exception {
        Assume.assumeTrue(UiFonts.isMac());
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<JFileChooser> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> holder.set(UiFonts.fileChooser()));
        JFileChooser chooser = holder.get();
        String[] tips = {"上一層", "家目錄", "新建資料夾", "清單", "詳細資料"};
        int matched = 0;
        boolean sawCancel = false;
        boolean sawOpen = false;
        for (java.awt.Component c : flatten(chooser)) {
            if (c instanceof JButton) {
                JButton button = (JButton) c;
                String tip = button.getToolTipText();
                if (tip != null) {
                    for (String expected : tips) {
                        if (expected.equals(tip)) {
                            matched++;
                            break;
                        }
                    }
                }
                String text = button.getText() == null ? "" : button.getText();
                if ("取消".equals(text) || "Cancel".equalsIgnoreCase(text)) {
                    sawCancel = true;
                    assertTrue(button.getForeground().getRed() < 200 || button.getForeground().getGreen() < 200);
                    assertFalse("取消鈕不應是白字疊白底",
                            button.getForeground().equals(Color.WHITE) && button.getBackground().getRed() > 200);
                }
                if ("開啟".equals(text) || "打開".equals(text) || "Open".equalsIgnoreCase(text)) {
                    sawOpen = true;
                    assertTrue(button.getForeground().getRed() < 200 || button.getForeground().getGreen() < 200
                            || button.getForeground().equals(Color.WHITE));
                }
            }
        }
        assertEquals(tips.length, matched);
        assertTrue(sawCancel);
        assertTrue(sawOpen);
    }

    @Test
    public void macChineseFontStaysPingFang() {
        org.junit.Assume.assumeTrue(UiFonts.isMac());
        String family = UiFonts.chinesePlain(13).getFamily();
        assertFalse("主畫面不可改用 Dialog，會吃掉 HTTP POST／SSL", "Dialog".equalsIgnoreCase(family));
        assertTrue(family.contains("PingFang") || family.contains("蘋方"));
    }

    private static java.util.List<java.awt.Component> flatten(java.awt.Component root) {
        java.util.ArrayList<java.awt.Component> list = new java.util.ArrayList<>();
        list.add(root);
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                list.addAll(flatten(child));
            }
        }
        return list;
    }
}
