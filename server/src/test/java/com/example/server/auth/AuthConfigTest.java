package com.example.server.auth;

import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AuthConfigTest {

    static String bcryptForTest(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(4));
    }

    @Test
    public void fromMapUsesExplicitPasswords() {
        String plain = "inject-admin";
        Map<String, String> env = new HashMap<>();
        env.put("ADMIN_PASSWORD", plain);
        env.put("HEARTBEAT_SECRET", "inject-heartbeat");
        AuthConfig config = AuthConfig.from(env);
        AuthService auth = new AuthService(config);
        assertTrue(auth.checkAdminPassword(plain));
        assertFalse(auth.checkAdminPassword("wrong"));
        assertTrue(BCrypt.checkpw(plain, config.adminPasswordHash()));
        assertEqualsSecret("inject-heartbeat", config.heartbeatSecret());
    }

    private static void assertEqualsSecret(String expected, String actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }

    @Test
    public void fromMapFallsBackWhenMissing() {
        AuthConfig config = AuthConfig.from(Map.of());
        AuthService auth = new AuthService(config);
        assertTrue(auth.checkAdminPassword("secret"));
        assertEqualsSecret("punchclock-dev-secret", config.heartbeatSecret());
    }

    @Test
    public void twoLoginsFromSameConfigYieldDifferentSessionTokens() {
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "alpha")));
        String a = auth.createSessionToken();
        String b = auth.createSessionToken();
        org.junit.Assert.assertNotEquals(a, b);
        assertTrue(auth.isAuthFromCookieHeader("auth=" + a));
        assertTrue(auth.isAuthFromCookieHeader("auth=" + b));
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
    public void renderRejectsPlaintextAdminPassword() {
        Map<String, String> env = new HashMap<>();
        env.put("RENDER", "true");
        env.put("ADMIN_PASSWORD", "render-admin-pw");
        env.put("HEARTBEAT_SECRET", "render-heartbeat-pw");
        try {
            AuthConfig.from(env);
            fail("expected plaintext rejection");
        } catch (IllegalStateException expected) {
            // ok
        }
    }

    @Test
    public void renderAcceptsBcryptAdminPassword() {
        String plain = "render-admin-pw";
        Map<String, String> env = new HashMap<>();
        env.put("RENDER", "true");
        env.put("ADMIN_PASSWORD", bcryptForTest(plain));
        env.put("HEARTBEAT_SECRET", "render-heartbeat-pw");
        AuthConfig config = AuthConfig.from(env);
        AuthService auth = new AuthService(config);
        assertTrue(auth.checkAdminPassword(plain));
        assertEqualsSecret("render-heartbeat-pw", config.heartbeatSecret());
    }
}