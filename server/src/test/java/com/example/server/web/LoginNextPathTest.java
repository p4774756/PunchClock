package com.example.server.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoginNextPathTest {

    @Test
    public void acceptsWhitelistedPaths() {
        assertEquals("/dev-status", LoginNextPath.sanitize("/dev-status"));
        assertEquals("/office", LoginNextPath.sanitize("/office"));
    }

    @Test
    public void rejectsEverythingElse() {
        assertNull(LoginNextPath.sanitize("/dashboard"));
        assertNull(LoginNextPath.sanitize("//evil.com"));
        assertNull(LoginNextPath.sanitize("https://evil.com"));
        assertNull(LoginNextPath.sanitize("/\\evil.com"));
        assertNull(LoginNextPath.sanitize("/foo//bar"));
        assertNull(LoginNextPath.sanitize("evil.com"));
        assertNull(LoginNextPath.sanitize("/http://evil"));
        assertNull(LoginNextPath.sanitize("/%2F%2Fevil.com"));
        assertNull(LoginNextPath.sanitize("/dev%5Cstatus"));
        assertNull(LoginNextPath.sanitize("/\t/evil.com"));
        assertNull(LoginNextPath.sanitize("/\n/evil.com"));
        assertNull(LoginNextPath.sanitize("/\r/evil.com"));
        assertNull(LoginNextPath.sanitize("/ /evil.com"));
        assertNull(LoginNextPath.sanitize("/%09/evil.com"));
        assertNull(LoginNextPath.sanitize("/\u0000x"));
        assertNull(LoginNextPath.sanitize("/開發"));
        assertNull(LoginNextPath.sanitize("javascript:alert(1)"));
    }
}