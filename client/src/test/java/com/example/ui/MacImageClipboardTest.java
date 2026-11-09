package com.example.ui;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacImageClipboardTest {

    @Test
    public void pathIsPassedAsArgumentNotInsideScript() {
        String path = "/tmp/it's \"odd\".png";
        List<String> command = MacImageClipboard.command(Paths.get(path));
        assertEquals("osascript", command.get(0));
        assertEquals(path, command.get(command.size() - 1));
        for (int i = 0; i < command.size() - 1; i++) {
            assertFalse(command.get(i).contains("odd"));
        }
        assertTrue(String.join(" ", command).contains("«class PNGf»"));
    }

    @Test
    public void nullImageIsRejected() {
        assertFalse(MacImageClipboard.copyPng(null));
    }
}
