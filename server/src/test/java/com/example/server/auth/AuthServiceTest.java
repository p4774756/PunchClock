package com.example.server.auth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthServiceTest {

    @Test
    public void isAuthFromCookieHeader_matchesAuthCookie() {
        AuthService auth = new AuthService();
        String header = "foo=1; auth=" + auth.authCookieValue() + "; bar=2";
        assertTrue(auth.isAuthFromCookieHeader(header));
        assertFalse(auth.isAuthFromCookieHeader("auth=wrong"));
        assertFalse(auth.isAuthFromCookieHeader(null));
    }
}
