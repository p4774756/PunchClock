package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** app-tokens.json：桌面端登入 token（只存雜湊）。 */
public final class AppTokenStore {

    public static final class TokenRecord {
        public String tokenHash;
        public String username;
        public String createdAt;
    }

    private static final class Document {
        List<TokenRecord> tokens = new ArrayList<>();
    }

    private final JsonDocumentFile file;
    private final Gson gson;
    private final SecureRandom random = new SecureRandom();
    private Document document = new Document();
    private final Object lock = new Object();

    public AppTokenStore(Path directory) {
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        Path dir = directory == null ? AccountDataPaths.defaultDirectory() : directory;
        this.file = new JsonDocumentFile(
                dir.resolve("app-tokens.json"),
                json -> gson.fromJson(json, Document.class),
                doc -> gson.toJson(doc));
        synchronized (lock) {
            Document loaded = file.readOr(new Document());
            if (loaded.tokens == null) {
                loaded.tokens = new ArrayList<>();
            }
            document = loaded;
        }
    }

  public String issueToken(String username) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        TokenRecord record = new TokenRecord();
        record.tokenHash = sha256Hex(token);
        record.username = username;
        record.createdAt = Instant.now().toString();
        synchronized (lock) {
            document.tokens.add(record);
            persistLocked();
        }
        return token;
    }

    public Optional<String> resolveUsername(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256Hex(token.trim());
        synchronized (lock) {
            for (TokenRecord r : document.tokens) {
                if (hash.equals(r.tokenHash)) {
                    return Optional.ofNullable(r.username);
                }
            }
            return Optional.empty();
        }
    }

    public void revokeAllForUser(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        synchronized (lock) {
            Iterator<TokenRecord> it = document.tokens.iterator();
            boolean changed = false;
            while (it.hasNext()) {
                if (username.equals(it.next().username)) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                persistLocked();
            }
        }
    }

    private void persistLocked() {
        try {
            file.write(document);
        } catch (Exception ex) {
            throw new IllegalStateException("無法寫入 app-tokens.json", ex);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}