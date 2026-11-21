package com.example.server.auth;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AuthConfigTest {

    @Test
    public void fromMapUsesExplicitPasswords() {
        Map<String, String> env = new HashMap<>();
        env.put("ADMIN_PASSWORD", "inject-admin");
        env.put("HEARTBEAT_SECRET", "inject-heartbeat");
        AuthConfig config = AuthConfig.from(env);
        assertEquals("inject-admin", config.adminPassword());
        assertEquals("inject-heartbeat", config.heartbeatSecret());
    }

    @Test
    public void fromMapFallsBackWhenMissing() {
        AuthConfig config = AuthConfig.from(Map.of());
        assertEquals("secret", config.adminPassword());
        assertEquals("punchclock-dev-secret", config.heartbeatSecret());
    }

    @Test
    public void differentAdminPasswordsProduceDifferentCookieValues() {
        AuthService a = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "alpha")));
        AuthService b = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "beta")));
        assertNotEquals(a.authCookieValue(), b.authCookieValue());
    }
}