package com.example.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void macChineseFontStaysPingFang() {
        org.junit.Assume.assumeTrue(UiFonts.isMac());
        String family = UiFonts.chinesePlain(13).getFamily();
        assertFalse("主畫面不可改用 Dialog，會吃掉 HTTP POST／SSL", "Dialog".equalsIgnoreCase(family));
        assertTrue(family.contains("PingFang") || family.contains("蘋方"));
    }
}
