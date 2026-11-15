package com.example.server.store;

import com.example.MarqueeNotice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/** 跑馬燈開關與文字。寫入儲存路徑，重開伺服器還在。 */
public final class MarqueeStore {

    private static final Logger LOG = Logger.getLogger(MarqueeStore.class.getName());
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");

    private final Path savePath;
    private boolean enabled;
    private String text = "";

    public MarqueeStore() {
        this(logPath(defaultPath()));
    }

    public MarqueeStore(Path savePath) {
        this.savePath = savePath;
        load();
    }

    public static Path defaultPath() {
        String home = System.getProperty("user.home", ".");
        return resolvePath(
                null,
                System.getenv("MARQUEE_PATH"),
                Files.isDirectory(PERSISTENT_ROOT),
                PERSISTENT_ROOT.resolve("server-marquee.json"),
                Paths.get(home, ".punchclock", "server-marquee.json"));
    }

    /** 覆寫值 > 環境變數 > 永久磁碟 > 家目錄。空白覆寫與環境變數視為沒有。 */
    public static Path resolvePath(String override, String env, boolean persistentAvailable, Path persistent, Path home) {
        return StorageLocation.resolve(override, env, persistentAvailable, persistent, home);
    }

    private static Path logPath(Path path) {
        LOG.info("跑馬燈設定：" + path);
        return path;
    }

    public synchronized MarqueeNotice current() {
        return new MarqueeNotice(enabled, text);
    }

    public synchronized MarqueeNotice update(boolean enabled, String raw) {
        MarqueeNotice next = MarqueeNotice.normalize(enabled, raw);
        this.enabled = next.enabled;
        this.text = next.text;
        save();
        return current();
    }

    private void load() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }
        try {
            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            Saved saved = GSON.fromJson(json, Saved.class);
            if (saved == null) {
                return;
            }
            MarqueeNotice notice = MarqueeNotice.normalize(saved.enabled, saved.text);
            enabled = notice.enabled;
            text = notice.text;
        } catch (Exception ignored) {
            enabled = false;
            text = "";
        }
    }

    private void save() {
        if (savePath == null) {
            return;
        }
        try {
            Path parent = savePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Saved saved = new Saved();
            saved.enabled = enabled;
            saved.text = text;
            Files.writeString(savePath, GSON.toJson(saved), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 寫入失敗仍保留記憶體裡的內容，這次執行看得到
        }
    }

    private static final class Saved {
        boolean enabled;
        String text;
    }
}
