package com.example.server.store;

import com.example.server.auth.AuthConfig;
import com.example.server.auth.AuthService;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountStoreTest {

    private Path tempDir;

    private AccountStore newStore(String adminPassword) throws Exception {
        tempDir = Files.createTempDirectory("account-store-test");
        AuthConfig config = AuthConfig.from(Map.of("ADMIN_PASSWORD", adminPassword));
        return new AccountStore(tempDir, config);
    }

    @After
    public void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    public void seedsRootWhenMissing() throws Exception {
        AccountStore store = newStore("seed-password-1");
        assertTrue(store.find("root").isPresent());
        assertEquals(AccountStore.ROLE_ADMIN, store.find("root").get().role);
        assertFalse(store.find("root").get().mustChangePassword);
        assertTrue(store.verifyPassword("root", "seed-password-1"));
    }

    @Test
    public void atomicWriteSurvivesCorruptMainFile() throws Exception {
        AccountStore store = newStore("atomic-test-pw!!");
        store.createAccount("member1", AccountStore.ROLE_MEMBER, "member1-pw!!", true);
        Path file = store.fileForTests().file();
        Files.copy(file, file.resolveSibling("accounts.json.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(file, "{not-json");
        AccountStore reloaded = new AccountStore(tempDir, AuthConfig.from(Map.of("ADMIN_PASSWORD", "ignored")));
        assertTrue(reloaded.find("root").isPresent());
        assertTrue(reloaded.find("member1").isPresent() || reloaded.verifyPassword("root", "atomic-test-pw!!"));
    }

    @Test
    public void lastAdminCannotBeDisabledOrDemoted() throws Exception {
        AccountStore store = newStore("last-admin-pw!!");
        assertEquals(AccountStore.OperationResult.LAST_ADMIN, store.setEnabled("root", false));
        assertEquals(AccountStore.OperationResult.LAST_ADMIN, store.setRole("root", AccountStore.ROLE_MEMBER));
        assertEquals(AccountStore.OperationResult.LAST_ADMIN, store.deleteAccount("root"));
    }

    @Test
    public void passwordRulesRejectWeakPasswords() {
        assertEquals(AccountStore.PasswordCheck.TOO_SHORT, AccountStore.validatePassword("alice", "short"));
        assertEquals(AccountStore.PasswordCheck.SAME_AS_USERNAME, AccountStore.validatePassword("alicebee", "AliceBee"));
        assertEquals(AccountStore.PasswordCheck.OK, AccountStore.validatePassword("alice", "goodpass"));
    }
}