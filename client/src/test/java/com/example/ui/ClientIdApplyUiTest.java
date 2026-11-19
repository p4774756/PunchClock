package com.example.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientIdApplyUiTest {

    @Test
    public void applyDisabledWhenBlankOrSameAsEffective() {
        assertFalse(ClientIdApplyUi.isApplyEnabled(null, "company-worker"));
        assertFalse(ClientIdApplyUi.isApplyEnabled("", "company-worker"));
        assertFalse(ClientIdApplyUi.isApplyEnabled("   ", "company-worker"));
        assertFalse(ClientIdApplyUi.isApplyEnabled("company-worker", "company-worker"));
        assertFalse(ClientIdApplyUi.isApplyEnabled("  company-worker  ", "company-worker"));
    }

    @Test
    public void applyEnabledWhenDraftDiffersFromEffective() {
        assertTrue(ClientIdApplyUi.isApplyEnabled("company-worker2", "company-worker"));
        assertTrue(ClientIdApplyUi.isApplyEnabled("皮卡丘", "company-worker"));
    }

    @Test
    public void applyUsesNfcNormalizationLikeHeartbeat() {
        String nfd = "e\u0301"; // NFC 為 é
        String nfc = "\u00e9";
        assertFalse(ClientIdApplyUi.isApplyEnabled(nfd, nfc));
        assertFalse(ClientIdApplyUi.isApplyEnabled(nfc, nfd));
    }
}