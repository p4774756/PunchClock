package com.example.server.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoginNextPathTest {

    @Test
    public void acceptsWhitelistedPaths() {
        assertEquals("/office", LoginNextPath.sanitize("/office"));
        assertEquals("/console", LoginNextPath.sanitize("/console"));
        assertEquals("/", LoginNextPath.sanitize("/"));
        assertEquals("/console#devices", LoginNextPath.sanitize("/console#devices"));
        assertEquals("/console?open=roadmap", LoginNextPath.sanitize("/console?open=roadmap"));
        assertEquals("/?open=roadmap", LoginNextPath.sanitize("/?open=roadmap"));
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
        assertNull(LoginNextPath.sanitize("/console?next=/evil"));
        assertNull(LoginNextPath.sanitize("/console#evil"));
        assertNull(LoginNextPath.sanitize("/dev-status"));
        assertNull(LoginNextPath.sanitize("/dev-status?open=roadmap"));
    }
}