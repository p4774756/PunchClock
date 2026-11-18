package com.example.server.auth;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DevStatusTokenAuthTest {

    @Test
    public void constantTimeEqualsRejectsMismatch() {
        assertTrue(DevStatusTokenAuth.constantTimeEquals("abc", "abc"));
        assertFalse(DevStatusTokenAuth.constantTimeEquals("abc", "abd"));
        assertFalse(DevStatusTokenAuth.constantTimeEquals("abc", "abcd"));
        assertFalse(DevStatusTokenAuth.constantTimeEquals(null, "a"));
    }

    @Test
    public void configuredWhenNonEmpty() {
        DevStatusTokenAuth auth = new DevStatusTokenAuth("test-token");
        assertTrue(auth.isConfigured());
        assertFalse(new DevStatusTokenAuth("").isConfigured());
        assertFalse(new DevStatusTokenAuth((String) null).isConfigured());
    }
}