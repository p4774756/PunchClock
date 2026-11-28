package com.example.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 站長 session 的 CSRF token（HMAC，不另存狀態）。 */
public final class CsrfTokens {

    public static final String HEADER = "X-Csrf-Token";
    public static final String FORM_FIELD = "csrfToken";

    private CsrfTokens() {
    }

    public static String tokenForSession(String sessionToken, String secret) {
        if (sessionToken == null || sessionToken.isEmpty() || secret == null || secret.isEmpty()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(sessionToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean constantTimeEquals(String expected, String submitted) {
        if (expected == null || submitted == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}