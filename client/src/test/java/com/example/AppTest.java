package com.example;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppTest {

    @Test
    public void versionIsFilteredFromPom() {
        assertFalse(AppVersion.VERSION.contains("${"));
        assertTrue(AppVersion.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
    }
}
