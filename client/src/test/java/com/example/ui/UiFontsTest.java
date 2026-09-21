package com.example.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UiFontsTest {

    @Test
    public void htmlMessageKeepsLatinDigitsAndEscapesMarkup() {
        String html = UiFonts.toHtmlMessage(
                "2026-09-11 09:27:49\n【John Wick】傳來 eapp-backend.zip（16.3 MB）");

        assertTrue(html.contains("Helvetica Neue"));
        assertTrue(html.contains("2026-09-11 09:27:49"));
        assertTrue(html.contains("John Wick"));
        assertTrue(html.contains("16.3 MB"));
        assertTrue(html.contains("<br>"));
        assertFalse(html.contains("\n"));
    }

    @Test
    public void htmlMessageEscapesTags() {
        String html = UiFonts.toHtmlMessage("<script>alert(1)</script>");
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert"));
    }

    @Test
    public void htmlMessageTreatsNullAsEmpty() {
        String html = UiFonts.toHtmlMessage(null);
        assertTrue(html.startsWith("<html>"));
        assertTrue(html.contains("</body></html>"));
        assertEquals(-1, html.indexOf("null"));
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
