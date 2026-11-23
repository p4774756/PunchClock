package com.example.server.store;

import com.example.server.auth.AuthConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** accounts.json：帳號生命週期與密碼驗證。 */
public final class AccountStore {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MEMBER = "member";
    private static final Pattern USERNAME = Pattern.compile("^[a-z][a-z0-9_-]{0,31}$");
    private static final String FORBIDDEN_PASSWORD = "secret";

    public static final class Account {
        public String username;
        public String passwordSalt;
        public String passwordHash;
        public String role;
        public boolean enabled = true;
        public boolean mustChangePassword;
        public String createdAt;
        public String passwordUpdatedAt;
    }

    private static final class Document {
        List<Account> accounts = new ArrayList<>();
    }

    public enum PasswordCheck {
        OK,
        TOO_SHORT,
        SAME_AS_USERNAME,
        FORBIDDEN
    }

    public enum OperationResult {
        OK,
        NOT_FOUND,
        CONFLICT,
        LAST_ADMIN,
        HAS_DEVICES,
        NOT_DISABLED,
        BAD_PASSWORD,
        BAD_USERNAME,
        BAD_ROLE
    }

    private final JsonDocumentFile file;
    private final Gson gson;
    private final SecureRandom random = new SecureRandom();
    private Document document = new Document();
    private final Object lock = new Object();

    public AccountStore(Path directory, AuthConfig seedConfig) {
        this(directory, seedConfig, new GsonBuilder().disableHtmlEscaping().create());
    }

    AccountStore(Path directory, AuthConfig seedConfig, Gson gson) {
        this.gson = gson;
        java.nio.file.Path dir = directory == null ? AccountDataPaths.defaultDirectory() : directory;
        this.file = new JsonDocumentFile(
                dir.resolve("accounts.json"),
                json -> gson.fromJson(json, Document.class),
                doc -> gson.toJson(doc));
        synchronized (lock) {
            Document loaded = file.readOr(new Document());
            if (loaded.accounts == null) {
                loaded.accounts = new ArrayList<>();
            }
            if (loaded.accounts.isEmpty()) {
                Account root = newAccount("root", seedConfig.adminPassword(), ROLE_ADMIN, false);
                loaded.accounts.add(root);
                persistLocked(loaded);
            }
            document = loaded;
        }
    }

    public List<Account> listAccountsCopy() {
        synchronized (lock) {
            List<Account> out = new ArrayList<>();
            for (Account a : document.accounts) {
                out.add(copyPublic(a));
            }
            return Collections.unmodifiableList(out);
        }
    }

    public Optional<Account> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            for (Account a : document.accounts) {
                if (username.equals(a.username)) {
                    return Optional.of(a);
                }
            }
            return Optional.empty();
        }
    }

    public boolean verifyPassword(String username, String password) {
        Optional<Account> account = find(username);
        if (account.isEmpty() || !account.get().enabled) {
            return false;
        }
        return passwordMatches(account.get(), password);
    }

    public OperationResult createAccount(String username, String role, String tempPassword, boolean mustChangePassword) {
        if (!USERNAME.matcher(username).matches()) {
            return OperationResult.BAD_USERNAME;
        }
        if (!ROLE_ADMIN.equals(role) && !ROLE_MEMBER.equals(role)) {
            return OperationResult.BAD_ROLE;
        }
        PasswordCheck check = validatePassword(username, tempPassword);
        if (check != PasswordCheck.OK) {
            return OperationResult.BAD_PASSWORD;
        }
        synchronized (lock) {
            if (find(username).isPresent()) {
                return OperationResult.CONFLICT;
            }
            document.accounts.add(newAccount(username, tempPassword, role, mustChangePassword));
            persistLocked(document);
            return OperationResult.OK;
        }
    }

    public OperationResult setPassword(String username, String newPassword, boolean clearMustChange) {
        PasswordCheck check = validatePassword(username, newPassword);
        if (check != PasswordCheck.OK) {
            return OperationResult.BAD_PASSWORD;
        }
        synchronized (lock) {
            Optional<Account> found = find(username);
            if (found.isEmpty()) {
                return OperationResult.NOT_FOUND;
            }
            Account a = found.get();
            applyPassword(a, newPassword);
            if (clearMustChange) {
                a.mustChangePassword = false;
            }
            persistLocked(document);
            return OperationResult.OK;
        }
    }

    public OperationResult setRole(String username, String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_MEMBER.equals(role)) {
            return OperationResult.BAD_ROLE;
        }
        synchronized (lock) {
            Optional<Account> found = find(username);
            if (found.isEmpty()) {
                return OperationResult.NOT_FOUND;
            }
            Account a = found.get();
            if (ROLE_ADMIN.equals(a.role) && ROLE_MEMBER.equals(role) && countEnabledAdmins() <= 1) {
                return OperationResult.LAST_ADMIN;
            }
            a.role = role;
            persistLocked(document);
            return OperationResult.OK;
        }
    }

    public OperationResult setEnabled(String username, boolean enabled) {
        synchronized (lock) {
            Optional<Account> found = find(username);
            if (found.isEmpty()) {
                return OperationResult.NOT_FOUND;
            }
            Account a = found.get();
            if (!enabled && ROLE_ADMIN.equals(a.role) && countEnabledAdmins() <= 1) {
                return OperationResult.LAST_ADMIN;
            }
            a.enabled = enabled;
            persistLocked(document);
            return OperationResult.OK;
        }
    }

    public OperationResult deleteAccount(String username) {
        synchronized (lock) {
            Optional<Account> found = find(username);
            if (found.isEmpty()) {
                return OperationResult.NOT_FOUND;
            }
            Account a = found.get();
            if (ROLE_ADMIN.equals(a.role) && countEnabledAdmins() <= 1) {
                return OperationResult.LAST_ADMIN;
            }
            if (a.enabled) {
                return OperationResult.NOT_DISABLED;
            }
            document.accounts.remove(a);
            persistLocked(document);
            return OperationResult.OK;
        }
    }

    public static PasswordCheck validatePassword(String username, String password) {
        if (password == null || password.length() < 8) {
            return PasswordCheck.TOO_SHORT;
        }
        if (username != null && username.equalsIgnoreCase(password)) {
            return PasswordCheck.SAME_AS_USERNAME;
        }
        if (FORBIDDEN_PASSWORD.equals(password)) {
            return PasswordCheck.FORBIDDEN;
        }
        return PasswordCheck.OK;
    }

    public int countEnabledAdmins() {
        int n = 0;
        for (Account a : document.accounts) {
            if (a.enabled && ROLE_ADMIN.equals(a.role)) {
                n++;
            }
        }
        return n;
    }

    JsonDocumentFile fileForTests() {
        return file;
    }

    private void persistLocked(Document doc) {
        try {
            file.write(doc);
        } catch (Exception ex) {
            throw new IllegalStateException("無法寫入 accounts.json", ex);
        }
    }

    private Account newAccount(String username, String password, String role, boolean mustChangePassword) {
        Account a = new Account();
        a.username = username;
        a.role = role;
        a.enabled = true;
        a.mustChangePassword = mustChangePassword;
        a.createdAt = Instant.now().toString();
        applyPassword(a, password);
        return a;
    }

    private void applyPassword(Account account, String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        account.passwordSalt = Base64.getEncoder().encodeToString(salt);
        account.passwordHash = hashPassword(salt, password);
        account.passwordUpdatedAt = Instant.now().toString();
    }

    private boolean passwordMatches(Account account, String password) {
        if (account.passwordSalt == null || account.passwordHash == null) {
            return false;
        }
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(account.passwordSalt);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String hash = hashPassword(salt, password);
        return constantTimeEquals(hash, account.passwordHash);
    }

    private static String hashPassword(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static Account copyPublic(Account source) {
        Account c = new Account();
        c.username = source.username;
        c.role = source.role;
        c.enabled = source.enabled;
        c.mustChangePassword = source.mustChangePassword;
        c.createdAt = source.createdAt;
        c.passwordUpdatedAt = source.passwordUpdatedAt;
        return c;
    }
}