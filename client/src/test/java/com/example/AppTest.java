package com.example;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppTest {

    @Test
    public void versionIsFilteredFromPom() {
        assertFalse(AppVersion.VERSION.contains("${"));
        assertTrue(AppVersion.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    public void releaseTimeIsFilteredFromGitOrBuild() {
        assertNotNull(Instant.parse(AppVersion.RELEASE_TIME_ISO));
        assertTrue(AppVersion.displayLabel().matches("v\\S+（(提交|建置) \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）"));
    }
}
