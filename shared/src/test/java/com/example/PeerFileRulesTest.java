package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerFileRulesTest {

    @Test
    public void sanitizeFilename_stripsPathAndUnsafeChars() {
        assertEquals("notes.txt", PeerFileRules.sanitizeFilename("../../secret/notes.txt"));
        assertEquals("photo.jpg", PeerFileRules.sanitizeFilename("C:\\Users\\a\\photo.jpg"));
        assertEquals("a_b.pdf", PeerFileRules.sanitizeFilename("a:b.pdf"));
        assertEquals("", PeerFileRules.sanitizeFilename(".."));
        assertEquals("", PeerFileRules.sanitizeFilename(""));
    }

    @Test
    public void isAllowedFilename_acceptsAllowlistOnly() {
        assertTrue(PeerFileRules.isAllowedFilename("a.pdf"));
        assertTrue(PeerFileRules.isAllowedFilename("b.PNG"));
        assertTrue(PeerFileRules.isAllowedFilename("c.jpeg"));
        assertFalse(PeerFileRules.isAllowedFilename("payload.exe"));
        assertFalse(PeerFileRules.isAllowedFilename("noext"));
        assertFalse(PeerFileRules.isAllowedFilename("ok.txt.exe"));
    }

    @Test
    public void mimeFor_usesExtensionNotClaimedType() {
        assertEquals("application/pdf", PeerFileRules.mimeFor("doc.pdf"));
        assertEquals("image/jpeg", PeerFileRules.mimeFor("x.jpg"));
        assertEquals("application/octet-stream", PeerFileRules.mimeFor("x.bin"));
    }

    @Test
    public void isAllowedSize_rejectsEmptyAndOverMax() {
        assertFalse(PeerFileRules.isAllowedSize(0));
        assertTrue(PeerFileRules.isAllowedSize(1));
        assertTrue(PeerFileRules.isAllowedSize(PeerFileRules.MAX_BYTES));
        assertFalse(PeerFileRules.isAllowedSize(PeerFileRules.MAX_BYTES + 1));
    }

    @Test
    public void encodeName_roundTripsUnicode() {
        String name = "備忘錄 1.txt";
        assertEquals(name, PeerFileRules.decodeName(PeerFileRules.encodeName(name)));
    }

    @Test
    public void formatSize_usesHumanUnits() {
        assertEquals("12 B", PeerFileRules.formatSize(12));
        assertEquals("1.0 KB", PeerFileRules.formatSize(1024));
        assertEquals("1.5 MB", PeerFileRules.formatSize((long) (1.5 * 1024 * 1024)));
    }
}
