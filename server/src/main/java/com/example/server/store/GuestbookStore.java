package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** 訪客簽名簿：留言、表情、選填塗鴉 PNG。索引在 guestbook/index.json。 */
public final class GuestbookStore {

    private static final Logger LOG = Logger.getLogger(GuestbookStore.class.getName());
    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");

    public static final int DOODLE_WIDTH = 240;
    public static final int DOODLE_HEIGHT = 120;
    public static final int MAX_ITEMS = 5000;
    public static final int DOODLE_MAX_BYTES = 50 * 1024;
    public static final int NICKNAME_MAX = 20;
    public static final int MESSAGE_MAX = 200;
    public static final int RATE_PER_MINUTE = 1;
    public static final long MINUTE_MS = 60_000L;
    public static final int DAILY_MAX = 30;
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    public static final String NICKNAME_MESSAGE = "暱稱必填，最多 20 字";
    public static final String MESSAGE_MESSAGE = "留言必填，最多 200 字";
    public static final String EMOJI_MESSAGE = "表情代碼不正確";
    public static final String DOODLE_MESSAGE = "只接受 " + DOODLE_WIDTH + "×" + DOODLE_HEIGHT + " 的 PNG，且不超過 50 KB";
    public static final String RATE_MINUTE_MESSAGE = "一分鐘內只能留一則，請稍後再試";
    public static final String RATE_DAY_MESSAGE = "今日留言已達上限（" + DAILY_MAX + " 則），明天再來";
    public static final String FULL_MESSAGE = "簽名簿已滿";

    public static final Set<String> EMOJIS = Set.of("smile", "heart", "thumbs", "angry");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final byte[] PNG_MAGIC = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

    private final Path directory;
    private final int maxItems;
    private final List<Item> items = new ArrayList<>();
    private final Map<String, Deque<Long>> recentByIp = new LinkedHashMap<>();

    public GuestbookStore() {
        this(logDirectory(defaultDirectory()), MAX_ITEMS);
    }

    public GuestbookStore(Path directory) {
        this(directory, MAX_ITEMS);
    }

    public GuestbookStore(Path directory, int maxItems) {
        this.directory = directory;
        this.maxItems = maxItems > 0 ? maxItems : MAX_ITEMS;
        load();
    }

    public static Path defaultDirectory() {
        String home = System.getProperty("user.home", ".");
        return resolveDirectory(
                System.getProperty("punchclock.guestbook.dir"),
                System.getenv("GUESTBOOK_STORAGE_DIR"),
                Files.isDirectory(PERSISTENT_ROOT),
                PERSISTENT_ROOT.resolve("guestbook"),
                Paths.get(home, ".punchclock", "guestbook"));
    }

    public static Path resolveDirectory(String override, String env, boolean persistentAvailable, Path persistent, Path home) {
        return StorageLocation.resolve(override, env, persistentAvailable, persistent, home);
    }

    private static Path logDirectory(Path directory) {
        LOG.info("簽名簿目錄：" + directory);
        return directory;
    }

    public synchronized int countVisible() {
        int n = 0;
        for (Item item : items) {
            if (!item.hidden) {
                n++;
            }
        }
        return n;
    }

    public synchronized int countSince(long sinceMs) {
        int n = 0;
        for (Item item : items) {
            if (!item.hidden && item.createdAt >= sinceMs) {
                n++;
            }
        }
        return n;
    }

  /** 新到舊。page 從 1 起算。includeHidden 僅管理員。 */
    public synchronized Page list(int page, int limit, boolean includeHidden) {
        int perPage = limit <= 0 ? 5 : Math.min(limit, 20);
        int pg = page <= 0 ? 1 : page;
        List<Item> visible = new ArrayList<>();
        for (Item item : items) {
            if (!includeHidden && item.hidden) {
                continue;
            }
            visible.add(item);
        }
        int total = visible.size();
        int totalPages = total == 0 ? 1 : (total + perPage - 1) / perPage;
        if (pg > totalPages) {
            pg = totalPages;
        }
        int from = (pg - 1) * perPage;
        int to = Math.min(from + perPage, total);
        List<Item> slice = new ArrayList<>();
        for (int i = from; i < to; i++) {
            slice.add(visible.get(i));
        }
        return new Page(slice, pg, totalPages, total);
    }

    public synchronized byte[] readDoodlePng(String id) {
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

    public synchronized Result add(AddRequest req, String ip, long now) {
        if (req == null) {
            return Result.fail(400, MESSAGE_MESSAGE);
        }
        if (req.honeypotFilled) {
            return Result.fail(400, "無法送出");
        }
        String nickname;
        String message;
        String emoji;
        try {
            nickname = normalizeNickname(req.nickname);
            message = normalizeMessage(req.message);
            emoji = normalizeEmoji(req.emoji);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        if (items.size() >= maxItems) {
            return Result.fail(409, FULL_MESSAGE);
        }
        String client = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        RateCheck rate = checkRate(client, now);
        if (!rate.ok) {
            return Result.fail(429, rate.message);
        }
        byte[] doodleBytes = null;
        if (req.doodleRaw != null && req.doodleRaw.length > 0) {
            if (req.doodleRaw.length > DOODLE_MAX_BYTES) {
                return Result.fail(413, DOODLE_MESSAGE);
            }
            BufferedImage decoded = decodePng(req.doodleRaw);
            if (decoded == null || decoded.getWidth() != DOODLE_WIDTH || decoded.getHeight() != DOODLE_HEIGHT) {
                return Result.fail(400, DOODLE_MESSAGE);
            }
            doodleBytes = reencode(decoded);
            if (doodleBytes == null || doodleBytes.length > DOODLE_MAX_BYTES) {
                return Result.fail(400, DOODLE_MESSAGE);
            }
        }
        Item item = new Item();
        item.id = newId();
        item.nickname = nickname;
        item.message = message;
        item.emoji = emoji;
        item.createdAt = now;
        item.hidden = false;
        item.doodleId = doodleBytes != null ? item.id : null;
        try {
            if (doodleBytes != null) {
                writePng(item.id, doodleBytes);
            }
            items.add(0, item);
            saveIndex();
            recordRate(client, now);
        } catch (IOException ex) {
            items.remove(item);
            return Result.fail(500, "留言沒能留住，請再試一次");
        }
        return Result.ok(item);
    }

    public synchronized boolean hide(String id) {
        Item item = find(id);
        if (item == null) {
            return false;
        }
        item.hidden = true;
        try {
            saveIndex();
        } catch (IOException ignored) {
        }
        return true;
    }

    public synchronized boolean delete(String id) {
        if (!validId(id)) {
            return false;
        }
        boolean removed = false;
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (id.equals(item.id)) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (directory != null) {
            try {
                Files.deleteIfExists(directory.resolve(id + ".png"));
            } catch (IOException ignored) {
            }
        }
        if (removed) {
            try {
                saveIndex();
            } catch (IOException ignored) {
            }
        }
        return removed;
    }

    public static String normalizeNickname(String raw) {
        String trimmed = stripControls(raw).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(NICKNAME_MESSAGE);
        }
        if (trimmed.codePointCount(0, trimmed.length()) > NICKNAME_MAX) {
            throw new IllegalArgumentException(NICKNAME_MESSAGE);
        }
        return trimmed;
    }

    public static String normalizeMessage(String raw) {
        String trimmed = stripControls(raw).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(MESSAGE_MESSAGE);
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MESSAGE_MAX) {
            throw new IllegalArgumentException(MESSAGE_MESSAGE);
        }
        return trimmed;
    }

    public static String normalizeEmoji(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String key = raw.trim();
        if (!EMOJIS.contains(key)) {
            throw new IllegalArgumentException(EMOJI_MESSAGE);
        }
        return key;
    }

    /** 解 data URL 或純 base64；非 PNG 回 null。 */
    public static byte[] decodeDoodlePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String src = raw.trim();
        if (src.startsWith("data:")) {
            int comma = src.indexOf(',');
            if (comma < 0) {
                return null;
            }
            String meta = src.substring(0, comma).toLowerCase();
            if (!meta.contains("image/png")) {
                return null;
            }
            src = src.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(src);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Item find(String id) {
        if (!validId(id)) {
            return null;
        }
        for (Item item : items) {
            if (id.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    private static String stripControls(String raw) {
        String source = raw == null ? "" : raw;
        StringBuilder kept = new StringBuilder();
        source.codePoints().forEach(cp -> {
            if (cp <= 0x1F || cp == 0x7F) {
                return;
            }
            kept.appendCodePoint(cp);
        });
        return kept.toString();
    }

    private RateCheck checkRate(String ip, long now) {
        Deque<Long> stamps = recentByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        while (!stamps.isEmpty() && now - stamps.peekFirst() >= DAY_MS) {
            stamps.removeFirst();
        }
        if (stamps.isEmpty()) {
            recentByIp.remove(ip);
            stamps = recentByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        }
        int today = stamps.size();
        if (today >= DAILY_MAX) {
            return RateCheck.block(RATE_DAY_MESSAGE);
        }
        if (!stamps.isEmpty()) {
            long last = stamps.peekLast();
            if (now - last < MINUTE_MS) {
                return RateCheck.block(RATE_MINUTE_MESSAGE);
            }
        }
        return RateCheck.ok();
    }

    private void recordRate(String ip, long now) {
        Deque<Long> stamps = recentByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        stamps.addLast(now);
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
            for (Item item : saved.items) {
                if (item == null || !validId(item.id)) {
                    continue;
                }
                try {
                    item.nickname = normalizeNickname(item.nickname);
                    item.message = normalizeMessage(item.message);
                    if (item.emoji != null && !item.emoji.isBlank()) {
                        item.emoji = normalizeEmoji(item.emoji);
                    } else {
                        item.emoji = "";
                    }
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                items.add(item);
            }
            items.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        } catch (Exception ignored) {
            items.clear();
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
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                return null;
            }
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width != DOODLE_WIDTH || height != DOODLE_HEIGHT) {
                return null;
            }
            return reader.read(0);
        } catch (IOException ex) {
            return null;
        } finally {
            reader.dispose();
        }
    }

    private static byte[] reencode(BufferedImage source) {
        BufferedImage clean = new BufferedImage(DOODLE_WIDTH, DOODLE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
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
        public String nickname;
        public String message;
        public String emoji;
        public long createdAt;
        public boolean hidden;
        /** 有塗鴉時與 id 相同，否則 null。 */
        public String doodleId;
    }

    public static final class Page {
        public final List<Item> items;
        public final int page;
        public final int totalPages;
        public final int total;

        Page(List<Item> items, int page, int totalPages, int total) {
            this.items = items;
            this.page = page;
            this.totalPages = totalPages;
            this.total = total;
        }
    }

    public static final class AddRequest {
        public String nickname;
        public String message;
        public String emoji;
        public byte[] doodleRaw;
        public boolean honeypotFilled;
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
            return new Result(true, 201, "已簽名", item);
        }

        static Result fail(int status, String message) {
            return new Result(false, status, message, null);
        }
    }

    private static final class RateCheck {
        final boolean ok;
        final String message;

        private RateCheck(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static RateCheck ok() {
            return new RateCheck(true, null);
        }

        static RateCheck block(String message) {
            return new RateCheck(false, message);
        }
    }

    private static final class Saved {
        List<Item> items;
    }
}