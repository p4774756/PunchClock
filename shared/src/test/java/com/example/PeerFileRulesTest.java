package com.example;

import org.junit.Test;

import java.nio.file.Path;

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
    public void isAllowedFilename_acceptsAnyExtensionIncludingNone() {
        assertTrue(PeerFileRules.isAllowedFilename("a.pdf"));
        assertTrue(PeerFileRules.isAllowedFilename("payload.exe"));
        assertTrue(PeerFileRules.isAllowedFilename("noext"));
        assertTrue(PeerFileRules.isAllowedFilename("ok.txt.exe"));
        assertTrue(PeerFileRules.isAllowedFilename("archive.7z"));
        assertFalse(PeerFileRules.isAllowedFilename(".."));
        assertFalse(PeerFileRules.isAllowedFilename(""));
        assertEquals("folder", PeerFileRules.normalizeKind("FOLDER"));
        assertEquals("file", PeerFileRules.normalizeKind("bin"));
    }

    @Test
    public void mimeFor_usesExtensionNotClaimedType() {
        assertEquals("application/pdf", PeerFileRules.mimeFor("doc.pdf"));
        assertEquals("image/jpeg", PeerFileRules.mimeFor("x.jpg"));
        assertEquals("application/octet-stream", PeerFileRules.mimeFor("x.bin"));
    }

    @Test
    public void isAllowedSize_rejectsEmptyAndOverMax() {
        assertEquals(50L * 1024 * 1024, PeerFileRules.MAX_BYTES);
        assertEquals("50 MB", PeerFileRules.MAX_SIZE_LABEL);
        assertFalse(PeerFileRules.isAllowedSize(0));
        assertTrue(PeerFileRules.isAllowedSize(1));
        assertTrue(PeerFileRules.isAllowedSize(6L * 1024 * 1024));
        assertTrue(PeerFileRules.isAllowedSize(PeerFileRules.MAX_BYTES));
        assertFalse(PeerFileRules.isAllowedSize(PeerFileRules.MAX_BYTES + 1));
    }

    @Test
    public void encodeName_roundTripsUnicode() {
        String name = "備忘錄 1.txt";
        assertEquals(name, PeerFileRules.decodeName(PeerFileRules.encodeName(name)));
    }

    @Test
    public void normalizeClientId_unifiesMacNfdAndWindowsNfc() {
        String nfc = java.text.Normalizer.normalize("café-worker", java.text.Normalizer.Form.NFC);
        String nfd = java.text.Normalizer.normalize("café-worker", java.text.Normalizer.Form.NFD);
        assertFalse(nfc.equals(nfd));
        assertEquals(nfc, PeerFileRules.normalizeClientId(nfd));
        assertEquals(nfc, PeerFileRules.normalizeClientId("  " + nfc + "\uFEFF"));
    }

    @Test
    public void resolveSavePath_makesRelativeNamesAbsoluteUnderDownloads() {
        Path dest = PeerFileRules.resolveSavePath(Path.of("notes.txt"), "notes.txt");
        assertTrue(dest.isAbsolute());
        assertEquals("notes.txt", dest.getFileName().toString());
        assertEquals(PeerFileRules.defaultDownloadDirectory(), dest.getParent());
    }

    @Test
    public void formatSize_usesHumanUnits() {
        assertEquals("12 B", PeerFileRules.formatSize(12));
        assertEquals("1.0 KB", PeerFileRules.formatSize(1024));
        assertEquals("1.5 MB", PeerFileRules.formatSize((long) (1.5 * 1024 * 1024)));
    }

    @Test
    public void formatRemaining_usesHoursMinutesSeconds() {
        assertEquals("已過期", PeerFileRules.formatRemaining(0));
        assertEquals("12 秒", PeerFileRules.formatRemaining(12_000));
        assertEquals("2 分 5 秒", PeerFileRules.formatRemaining((2 * 60 + 5) * 1000L));
        assertEquals("1 時 3 分", PeerFileRules.formatRemaining((63 * 60) * 1000L));
        assertEquals("6 小時", PeerFileRules.OFFER_TTL_LABEL);
        assertEquals(6L * 60 * 60 * 1000, PeerFileRules.OFFER_TTL_MS);
    }
}
