package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
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
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** 站長心得動態牆：文字、選用圖片、標籤；訪客留言存在各則底下。 */
public final class JournalStore {

    private static final Logger LOG = Logger.getLogger(JournalStore.class.getName());
    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");

    public static final int BODY_MAX = 1000;
    public static final int COMMENT_MAX = 200;
    public static final int NICKNAME_MAX = 12;
    public static final int TAG_MAX = 24;
    public static final int TAG_COUNT_MAX = 8;
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int RATE_GAP_MS = 30_000;
    public static final int RATE_DAILY_MAX = 20;
    public static final long RATE_DAY_MS = 24L * 60L * 60L * 1000L;
    public static final String ANONYMOUS = "路過的旅人";
    public static final String BODY_MESSAGE = "正文不可為空";
    public static final String BODY_LONG_MESSAGE = "正文太長";
    public static final String COMMENT_LONG_MESSAGE = "留言太長";
    public static final String NICKNAME_MESSAGE = "暱稱最多 12 個字";
    public static final String IMAGE_MESSAGE = "只接受 JPEG、PNG 或 WebP 圖片";
    public static final String SIZE_MESSAGE = "圖片太大了";
    public static final String RATE_MESSAGE = "留言太頻繁，請稍後再試";
    public static final String LINK_MESSAGE = "留言不可包含網址或連結";
    public static final String SPAM_MESSAGE = "留言內容無法刊登";
    public static final String HONEYPOT_MESSAGE = "無法送出留言，請再試一次";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)(https?://|www\\.|\\[[^\\]]+\\]\\([^)]+\\))");
    private static final String[] SPAM_WORDS = {
            "casino", "viagra", "bitcoin giveaway", "免費代購", "賭博", "色情"
    };

    private final Path directory;
    private final List<Post> posts = new ArrayList<>();
    private final Map<String, Deque<Long>> recentByIp = new LinkedHashMap<>();
    private final Map<String, Deque<Long>> dailyByIp = new LinkedHashMap<>();

    public JournalStore() {
        this(logDirectory(defaultDirectory()));
    }

    public JournalStore(Path directory) {
        this.directory = directory;
        load();
    }

    public static Path defaultDirectory() {
        String home = System.getProperty("user.home", ".");
        return resolveDirectory(
                System.getProperty("punchclock.journal.dir"),
                System.getenv("JOURNAL_STORAGE_DIR"),
                Files.isDirectory(PERSISTENT_ROOT),
                PERSISTENT_ROOT.resolve("journal"),
                Paths.get(home, ".punchclock", "journal"));
    }

    public static Path resolveDirectory(String override, String env, boolean persistentAvailable, Path persistent, Path home) {
        return StorageLocation.resolve(override, env, persistentAvailable, persistent, home);
    }

    private static Path logDirectory(Path directory) {
        LOG.info("心得牆目錄：" + directory);
        return directory;
    }

    public synchronized int count() {
        return posts.size();
    }

    public synchronized List<Post> list(Long before, int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 40);
        List<Post> page = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);
            if (before != null && item.createdAt >= before) {
                continue;
            }
            page.add(copyForRead(item));
            if (page.size() == size) {
                break;
            }
        }
        return page;
    }

    public synchronized boolean hasMore(long oldestCreatedAt) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).createdAt < oldestCreatedAt) {
                return true;
            }
        }
        return false;
    }

    public synchronized Post find(String id) {
        if (!validId(id)) {
            return null;
        }
        for (int i = 0; i < posts.size(); i++) {
            if (id.equals(posts.get(i).id)) {
                return copyForRead(posts.get(i));
            }
        }
        return null;
    }

    public synchronized byte[] readImage(String id, String ext) {
        if (!validId(id) || ext == null || directory == null) {
            return null;
        }
        String safe = safeImageExt(ext);
        if (safe == null) {
            return null;
        }
        Path file = directory.resolve(id + "." + safe);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            return null;
        }
    }

    public synchronized Result createPost(String body, List<String> tags, byte[] imageRaw, long now) {
        String text;
        try {
            text = normalizeBody(body);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        List<String> tagList;
        try {
            tagList = normalizeTags(tags);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        String imageExt = null;
        if (imageRaw != null && imageRaw.length > 0) {
            if (imageRaw.length > MAX_BYTES) {
                return Result.fail(413, SIZE_MESSAGE);
            }
            EncodedImage encoded = encodeImage(imageRaw);
            if (encoded == null) {
                return Result.fail(400, IMAGE_MESSAGE);
            }
            imageExt = encoded.ext;
            imageRaw = encoded.bytes;
        }
        Post post = new Post();
        post.id = newId();
        post.body = text;
        post.tags = tagList;
        post.createdAt = now;
        post.updatedAt = now;
        post.imageExt = imageExt;
        post.comments = new ArrayList<>();
        try {
            if (imageExt != null) {
                writeImage(post.id, imageExt, imageRaw);
            }
            posts.add(0, post);
            saveIndex();
        } catch (IOException ex) {
            posts.remove(post);
            deleteImageQuiet(post.id, imageExt);
            return Result.fail(500, "發文失敗，請再試一次");
        }
        return Result.okPost(post);
    }

    public synchronized Result updatePost(String id, String body, List<String> tags, byte[] imageRaw, boolean clearImage, long now) {
        Post post = findMutable(id);
        if (post == null) {
            return Result.fail(404, "找不到這篇心得");
        }
        String text;
        try {
            text = normalizeBody(body);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        List<String> tagList;
        try {
            tagList = normalizeTags(tags);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        String prevExt = post.imageExt;
        String imageExt = post.imageExt;
        byte[] toWrite = null;
        if (clearImage) {
            imageExt = null;
        } else if (imageRaw != null && imageRaw.length > 0) {
            if (imageRaw.length > MAX_BYTES) {
                return Result.fail(413, SIZE_MESSAGE);
            }
            EncodedImage encoded = encodeImage(imageRaw);
            if (encoded == null) {
                return Result.fail(400, IMAGE_MESSAGE);
            }
            imageExt = encoded.ext;
            toWrite = encoded.bytes;
        }
        post.body = text;
        post.tags = tagList;
        post.updatedAt = now;
        post.imageExt = imageExt;
        try {
            if (clearImage && prevExt != null) {
                deleteImageQuiet(post.id, prevExt);
            }
            if (toWrite != null) {
                if (prevExt != null && !prevExt.equals(imageExt)) {
                    deleteImageQuiet(post.id, prevExt);
                }
                writeImage(post.id, imageExt, toWrite);
            }
            saveIndex();
        } catch (IOException ex) {
            return Result.fail(500, "更新失敗，請再試一次");
        }
        return Result.okPost(post);
    }

    public synchronized boolean deletePost(String id) {
        if (!validId(id)) {
            return false;
        }
        Post removed = null;
        Iterator<Post> it = posts.iterator();
        while (it.hasNext()) {
            Post p = it.next();
            if (id.equals(p.id)) {
                removed = p;
                it.remove();
                break;
            }
        }
        if (removed == null) {
            return false;
        }
        deleteImageQuiet(removed.id, removed.imageExt);
        try {
            saveIndex();
        } catch (IOException ignored) {
            // 記憶體已刪
        }
        return true;
    }

    public synchronized Result addComment(String postId, String nickname, String body, String honeypot, String ip, long now) {
        if (honeypot != null && !honeypot.isBlank()) {
            return Result.fail(400, HONEYPOT_MESSAGE);
        }
        Post post = findMutable(postId);
        if (post == null) {
            return Result.fail(404, "找不到這篇心得");
        }
        String name;
        try {
            name = normalizeNickname(nickname);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        String text;
        try {
            text = normalizeComment(body);
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
        if (containsForbiddenLink(text)) {
            return Result.fail(400, LINK_MESSAGE);
        }
        if (containsSpam(text)) {
            return Result.fail(400, SPAM_MESSAGE);
        }
        String client = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        if (!allowComment(client, now)) {
            return Result.fail(429, RATE_MESSAGE);
        }
        Comment comment = new Comment();
        comment.id = newId();
        comment.nickname = name;
        comment.body = text;
        comment.createdAt = now;
        if (post.comments == null) {
            post.comments = new ArrayList<>();
        }
        post.comments.add(comment);
        try {
            saveIndex();
        } catch (IOException ex) {
            post.comments.remove(comment);
            return Result.fail(500, "留言沒能留下，請再試一次");
        }
        return Result.okComment(comment);
    }

    public synchronized boolean deleteComment(String postId, String commentId) {
        Post post = findMutable(postId);
        if (post == null || post.comments == null) {
            return false;
        }
        boolean removed = false;
        Iterator<Comment> it = post.comments.iterator();
        while (it.hasNext()) {
            if (commentId.equals(it.next().id)) {
                it.remove();
                removed = true;
            }
        }
        if (!removed) {
            return false;
        }
        try {
            saveIndex();
        } catch (IOException ignored) {
            // 記憶體已刪
        }
        return true;
    }

    public static String normalizeBody(String raw) {
        String trimmed = stripControls(raw).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(BODY_MESSAGE);
        }
        if (trimmed.codePointCount(0, trimmed.length()) > BODY_MAX) {
            throw new IllegalArgumentException(BODY_LONG_MESSAGE);
        }
        return trimmed;
    }

    public static String normalizeComment(String raw) {
        String trimmed = stripControls(raw).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("留言不可為空");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > COMMENT_MAX) {
            throw new IllegalArgumentException(COMMENT_LONG_MESSAGE);
        }
        return trimmed;
    }

    public static String normalizeNickname(String raw) {
        String trimmed = stripControls(raw).trim();
        if (trimmed.isEmpty()) {
            return ANONYMOUS;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > NICKNAME_MAX) {
            throw new IllegalArgumentException(NICKNAME_MESSAGE);
        }
        return trimmed;
    }

    public static List<String> normalizeTags(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (int i = 0; i < raw.size() && out.size() < TAG_COUNT_MAX; i++) {
            String t = stripControls(raw.get(i)).trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.codePointCount(0, t.length()) > TAG_MAX) {
                throw new IllegalArgumentException("標籤太長");
            }
            if (!out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean containsForbiddenLink(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return LINK_PATTERN.matcher(text).find();
    }

    public static boolean containsSpam(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < SPAM_WORDS.length; i++) {
            if (lower.contains(SPAM_WORDS[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean allowComment(String ip, long now) {
        Deque<Long> recent = recentByIp.get(ip);
        if (recent != null) {
            while (!recent.isEmpty() && now - recent.peekFirst() >= RATE_GAP_MS) {
                recent.removeFirst();
            }
            if (recent.isEmpty()) {
                recentByIp.remove(ip);
                recent = null;
            }
        }
        if (recent != null && !recent.isEmpty() && now - recent.peekLast() < RATE_GAP_MS) {
            return false;
        }
        Deque<Long> daily = dailyByIp.get(ip);
        if (daily != null) {
            while (!daily.isEmpty() && now - daily.peekFirst() >= RATE_DAY_MS) {
                daily.removeFirst();
            }
            if (daily.isEmpty()) {
                dailyByIp.remove(ip);
                daily = null;
            }
        }
        if (daily != null && daily.size() >= RATE_DAILY_MAX) {
            return false;
        }
        if (recent == null) {
            recent = new ArrayDeque<>();
            recentByIp.put(ip, recent);
        }
        recent.addLast(now);
        if (daily == null) {
            daily = new ArrayDeque<>();
            dailyByIp.put(ip, daily);
        }
        daily.addLast(now);
        return true;
    }

    private Post findMutable(String id) {
        if (!validId(id)) {
            return null;
        }
        for (int i = 0; i < posts.size(); i++) {
            if (id.equals(posts.get(i).id)) {
                return posts.get(i);
            }
        }
        return null;
    }

    private static Post copyForRead(Post source) {
        Post copy = new Post();
        copy.id = source.id;
        copy.body = source.body;
        copy.tags = source.tags == null ? new ArrayList<>() : new ArrayList<>(source.tags);
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        copy.imageExt = source.imageExt;
        copy.comments = new ArrayList<>();
        if (source.comments != null) {
            for (int i = 0; i < source.comments.size(); i++) {
                Comment c = source.comments.get(i);
                if (c == null) {
                    continue;
                }
                Comment cc = new Comment();
                cc.id = c.id;
                cc.nickname = c.nickname;
                cc.body = c.body;
                cc.createdAt = c.createdAt;
                copy.comments.add(cc);
            }
        }
        return copy;
    }

    private void load() {
        posts.clear();
        if (directory == null) {
            return;
        }
        Path index = directory.resolve("index.json");
        if (!Files.isRegularFile(index)) {
            return;
        }
        try {
            Saved saved = GSON.fromJson(Files.readString(index, StandardCharsets.UTF_8), Saved.class);
            if (saved == null || saved.posts == null) {
                return;
            }
            for (int i = 0; i < saved.posts.size(); i++) {
                Post p = saved.posts.get(i);
                if (p == null || !validId(p.id)) {
                    continue;
                }
                try {
                    p.body = normalizeBody(p.body);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                p.tags = normalizeTags(p.tags);
                if (p.comments == null) {
                    p.comments = new ArrayList<>();
                }
                posts.add(p);
            }
            posts.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        } catch (Exception ignored) {
            posts.clear();
        }
    }

    private void saveIndex() throws IOException {
        if (directory == null) {
            return;
        }
        Files.createDirectories(directory);
        Saved saved = new Saved();
        saved.posts = posts;
        Path dest = directory.resolve("index.json");
        Path tmp = directory.resolve("index.json.tmp");
        Files.writeString(tmp, GSON.toJson(saved), StandardCharsets.UTF_8);
        moveReplace(tmp, dest);
    }

    private void writeImage(String id, String ext, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        Path dest = directory.resolve(id + "." + ext);
        Path tmp = directory.resolve(id + "." + ext + ".tmp");
        Files.write(tmp, bytes);
        moveReplace(tmp, dest);
    }

    private void deleteImageQuiet(String id, String ext) {
        if (directory == null || id == null || ext == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(id + "." + safeImageExt(ext)));
        } catch (IOException ignored) {
            // 索引先拿掉
        }
    }

    private static void moveReplace(Path tmp, Path dest) throws IOException {
        try {
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static EncodedImage encodeImage(byte[] raw) {
        String format = detectFormat(raw);
        if (format == null) {
            return null;
        }
        BufferedImage decoded = decodeImage(raw, format);
        if (decoded == null) {
            return null;
        }
        int w = decoded.getWidth();
        int h = decoded.getHeight();
        int maxSide = Math.max(w, h);
        if (maxSide > 1600) {
            float scale = 1600f / maxSide;
            int nw = Math.max(1, Math.round(w * scale));
            int nh = Math.max(1, Math.round(h * scale));
            Image scaled = decoded.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            try {
                g.drawImage(scaled, 0, 0, null);
            } finally {
                g.dispose();
            }
            decoded = resized;
        }
        String outFormat = format.equals("jpeg") ? "jpg" : format;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(decoded, outFormat, out)) {
                return null;
            }
        } catch (IOException ex) {
            return null;
        }
        if (out.size() > MAX_BYTES) {
            return null;
        }
        EncodedImage encoded = new EncodedImage();
        encoded.ext = outFormat;
        encoded.bytes = out.toByteArray();
        return encoded;
    }

    private static String detectFormat(byte[] raw) {
        if (raw.length >= 3 && raw[0] == (byte) 0xFF && raw[1] == (byte) 0xD8) {
            return "jpeg";
        }
        if (raw.length >= 8 && raw[0] == (byte) 0x89 && raw[1] == 0x50 && raw[2] == 0x4E && raw[3] == 0x47) {
            return "png";
        }
        if (raw.length >= 12
                && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F'
                && raw[8] == 'W' && raw[9] == 'E' && raw[10] == 'B' && raw[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static BufferedImage decodeImage(byte[] raw, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                return null;
            }
            reader.setInput(input, true, true);
            return reader.read(0);
        } catch (IOException ex) {
            return null;
        } finally {
            reader.dispose();
        }
    }

    public static String safeImageExt(String ext) {
        if (ext == null) {
            return null;
        }
        String e = ext.toLowerCase(Locale.ROOT);
        if ("jpg".equals(e) || "jpeg".equals(e)) {
            return "jpg";
        }
        if ("png".equals(e) || "webp".equals(e)) {
            return e;
        }
        return null;
    }

    public static String contentTypeForExt(String ext) {
        String safe = safeImageExt(ext);
        if ("jpg".equals(safe)) {
            return "image/jpeg";
        }
        if ("png".equals(safe)) {
            return "image/png";
        }
        if ("webp".equals(safe)) {
            return "image/webp";
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

    public static final class Post {
        public String id;
        public String body;
        public List<String> tags;
        public long createdAt;
        public long updatedAt;
        public String imageExt;
        public List<Comment> comments;
    }

    public static final class Comment {
        public String id;
        public String nickname;
        public String body;
        public long createdAt;
    }

    public static final class Result {
        public final boolean ok;
        public final int status;
        public final String message;
        public final Post post;
        public final Comment comment;

        private Result(boolean ok, int status, String message, Post post, Comment comment) {
            this.ok = ok;
            this.status = status;
            this.message = message;
            this.post = post;
            this.comment = comment;
        }

        static Result okPost(Post post) {
            return new Result(true, 200, "已發佈", post, null);
        }

        static Result okComment(Comment comment) {
            return new Result(true, 200, "留言已送出", null, comment);
        }

        static Result fail(int status, String message) {
            return new Result(false, status, message, null, null);
        }
    }

    private static final class Saved {
        List<Post> posts;
    }

    private static final class EncodedImage {
        String ext;
        byte[] bytes;
    }
}