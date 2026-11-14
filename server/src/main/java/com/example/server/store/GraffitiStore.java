package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 訪客塗鴉。每幅一個 PNG，索引在 ~/.punchclock/graffiti/index.json。寫入先落暫存檔再搬移。 */
public final class GraffitiStore {

    public static final int WIDTH = 512;
    public static final int HEIGHT = 256;
    public static final int MAX_ITEMS = 1000;
    public static final int MAX_BYTES = 300 * 1024;
    public static final int SIGNATURE_MAX = 12;
    public static final int RATE_MAX = 3;
    public static final long RATE_WINDOW_MS = 10L * 60L * 1000L;
    public static final String ANONYMOUS = "路過的旅人";
    public static final String FULL_MESSAGE = "塗鴉牆已經滿了";
    public static final String RATE_MESSAGE = "畫太快了，十分鐘內最多三幅";
    public static final String SIZE_MESSAGE = "塗鴉太大了";
    public static final String IMAGE_MESSAGE = "只接受 512×256 的 PNG";
    public static final String SIGNATURE_MESSAGE = "署名最多 12 個字";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final byte[] PNG_MAGIC = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

    private final Path directory;
    private final int maxItems;
    private final List<Item> items = new ArrayList<>();
    private final Map<String, Deque<Long>> recentByIp = new LinkedHashMap<>();

    public GraffitiStore() {
        this(defaultDirectory(), MAX_ITEMS);
    }

    public GraffitiStore(Path directory) {
        this(directory, MAX_ITEMS);
    }

    public GraffitiStore(Path directory, int maxItems) {
        this.directory = directory;
        this.maxItems = maxItems > 0 ? maxItems : MAX_ITEMS;
        load();
    }

    public static Path defaultDirectory() {
        String override = System.getProperty("punchclock.graffiti.dir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, ".punchclock", "graffiti");
    }

    public synchronized int count() {
        return items.size();
    }

    /** 新到舊。before 是上一頁最後一筆的建立時間，不含該筆。 */
    public synchronized List<Item> list(Long before, int limit) {
        int size = limit <= 0 ? 24 : Math.min(limit, 60);
        List<Item> page = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (before != null && item.createdAt >= before) {
                continue;
            }
            page.add(item);
            if (page.size() == size) {
                break;
            }
        }
        return page;
    }

    public synchronized boolean hasMore(long oldestCreatedAt) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).createdAt < oldestCreatedAt) {
                return true;
            }
        }
        return false;
    }

    public synchronized byte[] readPng(String id) {
        if (!validId(id) || directory == null) {
            return null;
        }
        Path file = directory.resolve(id + ".png");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            return null;
        }
    }

    public synchronized Result add(byte[] raw, String signature, String ip, long now) {
        if (raw == null || raw.length > MAX_BYTES) {
            return Result.fail(413, SIZE_MESSAGE);
        }
        BufferedImage decoded = decodePng(raw);
        if (decoded == null || decoded.getWidth() != WIDTH || decoded.getHeight() != HEIGHT) {
            return Result.fail(400, IMAGE_MESSAGE);
        }
        String name;
        try {
            name = normalizeSignature(signature);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, SIGNATURE_MESSAGE);
        }
        if (items.size() >= maxItems) {
            return Result.fail(409, FULL_MESSAGE);
        }
        String client = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        if (!allow(client, now)) {
            return Result.fail(429, RATE_MESSAGE);
        }
        byte[] encoded = reencode(decoded);
        if (encoded == null) {
            return Result.fail(400, IMAGE_MESSAGE);
        }
        Item item = new Item();
        item.id = newId();
        item.signature = name;
        item.createdAt = now;
        try {
            writePng(item.id, encoded);
            items.add(0, item);
            saveIndex();
        } catch (IOException ex) {
            items.remove(item);
            return Result.fail(500, "塗鴉沒能留住，請再試一次");
        }
        return Result.ok(item);
    }

    public synchronized boolean delete(String id) {
        if (!validId(id)) {
            return false;
        }
        boolean removed = false;
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            if (id.equals(it.next().id)) {
                it.remove();
                removed = true;
            }
        }
        if (directory != null) {
            try {
                Files.deleteIfExists(directory.resolve(id + ".png"));
            } catch (IOException ignored) {
                // 索引先拿掉，檔案下次再清
            }
        }
        if (removed) {
            try {
                saveIndex();
            } catch (IOException ignored) {
                // 記憶體已刪，這次執行看得到
            }
        }
        return removed;
    }

    /** 去掉控制字元與頭尾空白。空白署名用「路過的旅人」。超過 12 字丟 IllegalArgumentException。 */
    public static String normalizeSignature(String raw) {
        String source = raw == null ? "" : raw;
        StringBuilder kept = new StringBuilder();
        source.codePoints().forEach(cp -> {
            if (cp <= 0x1F || cp == 0x7F) {
                return;
            }
            kept.appendCodePoint(cp);
        });
        String trimmed = kept.toString().trim();
        if (trimmed.isEmpty()) {
            return ANONYMOUS;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > SIGNATURE_MAX) {
            throw new IllegalArgumentException(SIGNATURE_MESSAGE);
        }
        return trimmed;
    }

    private boolean allow(String ip, long now) {
        Deque<Long> stamps = recentByIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        while (!stamps.isEmpty() && now - stamps.peekFirst() >= RATE_WINDOW_MS) {
            stamps.removeFirst();
        }
        if (stamps.size() >= RATE_MAX) {
            return false;
        }
        stamps.addLast(now);
        return true;
    }

    private void load() {
        items.clear();
        if (directory == null) {
            return;
        }
        Path index = directory.resolve("index.json");
        if (!Files.isRegularFile(index)) {
            return;
        }
        try {
            Saved saved = GSON.fromJson(Files.readString(index, StandardCharsets.UTF_8), Saved.class);
            if (saved == null || saved.items == null) {
                return;
            }
            for (int i = 0; i < saved.items.size(); i++) {
                Item item = saved.items.get(i);
                if (item == null || !validId(item.id)) {
                    continue;
                }
                item.signature = safeStoredSignature(item.signature);
                items.add(item);
            }
            items.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        } catch (Exception ignored) {
            items.clear();
        }
    }

    private static String safeStoredSignature(String raw) {
        try {
            return normalizeSignature(raw);
        } catch (IllegalArgumentException ex) {
            return ANONYMOUS;
        }
    }

    private void writePng(String id, byte[] png) throws IOException {
        Files.createDirectories(directory);
        Path dest = directory.resolve(id + ".png");
        Path tmp = directory.resolve(id + ".png.tmp");
        Files.write(tmp, png);
        moveReplace(tmp, dest);
    }

    private void saveIndex() throws IOException {
        if (directory == null) {
            return;
        }
        Files.createDirectories(directory);
        Saved saved = new Saved();
        saved.items = items;
        Path dest = directory.resolve("index.json");
        Path tmp = directory.resolve("index.json.tmp");
        Files.writeString(tmp, GSON.toJson(saved), StandardCharsets.UTF_8);
        moveReplace(tmp, dest);
    }

    private static void moveReplace(Path tmp, Path dest) throws IOException {
        try {
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static BufferedImage decodePng(byte[] raw) {
        if (raw.length < PNG_MAGIC.length) {
            return null;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (raw[i] != PNG_MAGIC[i]) {
                return null;
            }
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException ex) {
            return null;
        }
    }

    private static byte[] reencode(BufferedImage source) {
        BufferedImage clean = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = clean.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(clean, "png", out)) {
                return null;
            }
        } catch (IOException ex) {
            return null;
        }
        return out.toByteArray();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean validId(String id) {
        if (id == null || id.length() < 8 || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public static final class Item {
        public String id;
        public String signature;
        public long createdAt;
    }

    public static final class Result {
        public final boolean ok;
        public final int status;
        public final String message;
        public final Item item;

        private Result(boolean ok, int status, String message, Item item) {
            this.ok = ok;
            this.status = status;
            this.message = message;
            this.item = item;
        }

        static Result ok(Item item) {
            return new Result(true, 200, "已留在牆上", item);
        }

        static Result fail(int status, String message) {
            return new Result(false, status, message, null);
        }
    }

    private static final class Saved {
        List<Item> items;
    }
}
