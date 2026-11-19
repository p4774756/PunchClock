package com.example.server.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoginNextPathTest {

    @Test
    public void acceptsSafeRelativePaths() {
        assertEquals("/dev-status", LoginNextPath.sanitize("/dev-status"));
        assertEquals("/dashboard", LoginNextPath.sanitize("/dashboard"));
    }

    @Test
    public void rejectsOpenRedirects() {
        assertNull(LoginNextPath.sanitize("//evil.com"));
        assertNull(LoginNextPath.sanitize("https://evil.com"));
        assertNull(LoginNextPath.sanitize("/\\evil"));
        assertNull(LoginNextPath.sanitize("/foo//bar"));
        assertNull(LoginNextPath.sanitize("evil.com"));
        assertNull(LoginNextPath.sanitize("/http://evil"));
    }

    @Test
    public void rejectsEncodedBypass() {
        assertNull(LoginNextPath.sanitize("/%2F%2Fevil.com"));
        assertNull(LoginNextPath.sanitize("/dev%5Cstatus"));
    }
}