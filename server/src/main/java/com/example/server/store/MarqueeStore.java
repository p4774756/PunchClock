package com.example.server.store;

import com.example.MarqueeNotice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 跑馬燈開關與文字。寫在 ~/.punchclock/server-marquee.json，重開伺服器還在。 */
public final class MarqueeStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path savePath;
    private boolean enabled;
    private String text = "";

    public MarqueeStore() {
        this(defaultPath());
    }

    public MarqueeStore(Path savePath) {
        this.savePath = savePath;
        load();
    }

    public static Path defaultPath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, ".punchclock", "server-marquee.json");
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
