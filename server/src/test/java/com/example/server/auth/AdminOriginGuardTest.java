package com.example.server.auth;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminOriginGuardTest {

    @Test
    public void parseAllowedOriginsCsv() {
        Set<String> out = AdminOriginGuard.parseAllowedOriginsList("http://a.test, http://b.test");
        assertTrue(out.contains("http://a.test"));
        assertTrue(out.contains("http://b.test"));
    }

    @Test
    public void normalizeOriginStripsTrailingSlash() {
        assertEquals("http://127.0.0.1:8080", AdminOriginGuard.normalizeOrigin("http://127.0.0.1:8080/"));
    }
}