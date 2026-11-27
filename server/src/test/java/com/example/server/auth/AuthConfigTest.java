package com.example.server.auth;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

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

    @Test
    public void renderRejectsMissingOrDefaultSecrets() {
        Map<String, String> missingAll = new HashMap<>();
        missingAll.put("RENDER", "true");
        try {
            AuthConfig.from(missingAll);
            fail("expected production guard");
        } catch (IllegalStateException expected) {
            // ok
        }

        Map<String, String> defaultAdmin = new HashMap<>();
        defaultAdmin.put("RENDER", "true");
        defaultAdmin.put("HEARTBEAT_SECRET", "prod-heartbeat-only");
        try {
            AuthConfig.from(defaultAdmin);
            fail("expected default ADMIN_PASSWORD rejection");
        } catch (IllegalStateException expected) {
            // ok
        }
    }

    @Test
    public void renderAcceptsNonDefaultSecrets() {
        Map<String, String> env = new HashMap<>();
        env.put("RENDER", "true");
        env.put("ADMIN_PASSWORD", "render-admin-pw");
        env.put("HEARTBEAT_SECRET", "render-heartbeat-pw");
        AuthConfig config = AuthConfig.from(env);
        assertEquals("render-admin-pw", config.adminPassword());
        assertEquals("render-heartbeat-pw", config.heartbeatSecret());
    }
}