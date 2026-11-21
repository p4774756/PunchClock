package com.example.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

/**
 * 安裝包不附帶 Playwright 的 node 執行檔（單平台就約 35MB，會把安裝檔撐過 60MB）。
 * 驅動的 JavaScript 仍留在 jar 裡；第一次打卡時只下載該平台的 node。
 * 一般 {@code java -jar} 的完整 jar 已含 node，不會再下載。
 */
public final class PlaywrightNodeSupport {

    static final String NODEJS_PATH_PROPERTY = "playwright.nodejs.path";
    private static final Pattern VERSION_FIELD = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    private static final long MIN_NODE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_COMPRESSED_BYTES = 80L * 1024 * 1024;

    private PlaywrightNodeSupport() {}

    public static void ensureNodeForPackagedApp(Consumer<String> logger) {
        if (embeddedNodePresent()) {
            return;
        }
        if (!System.getProperty(NODEJS_PATH_PROPERTY, "").trim().isEmpty()) {
            return;
        }
        try {
            if (logger != null) {
                logger.accept("[初始化] 正在準備瀏覽器驅動…");
            }
            Path node = downloadNode(Path.of(System.getProperty("user.home", ".")));
            System.setProperty(NODEJS_PATH_PROPERTY, node.toAbsolutePath().normalize().toString());
        } catch (IOException e) {
            throw new IllegalStateException("無法準備瀏覽器驅動", e);
        }
    }

    static boolean embeddedNodePresent() {
        String platform = platformId();
        String resource = "/driver/" + platform + "/" + nodeFileName(platform);
        return PlaywrightNodeSupport.class.getResource(resource) != null;
    }

    static String platformId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        if (os.contains("win")) {
            return "win32_x64";
        }
        if (os.contains("mac")) {
            return arm ? "mac-arm64" : "mac";
        }
        return arm ? "linux-arm64" : "linux";
    }

    static String nodeFileName(String platform) {
        return "win32_x64".equals(platform) ? "node.exe" : "node";
    }

    static Path nodeDestination(Path home, String version, String platform) {
        return home.resolve(".sklbaby")
                .resolve("playwright")
                .resolve(version)
                .resolve(platform)
                .resolve(nodeFileName(platform));
    }

    static String playwrightVersion(String platform) throws IOException {
        String resource = "/driver/" + platform + "/package/package.json";
        try (InputStream in = PlaywrightNodeSupport.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("找不到瀏覽器驅動版本");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = VERSION_FIELD.matcher(json);
            if (!matcher.find()) {
                throw new IOException("找不到瀏覽器驅動版本");
            }
            String version = matcher.group(1).trim();
            if (!version.matches("[0-9A-Za-z][0-9A-Za-z._-]{0,40}")) {
                throw new IOException("瀏覽器驅動版本無法使用");
            }
            return version;
        }
    }

    static Path downloadNode(Path home) throws IOException {
        String platform = platformId();
        String version = playwrightVersion(platform);
        Path dest = nodeDestination(home, version, platform);
        if (Files.isRegularFile(dest) && Files.size(dest) >= MIN_NODE_BYTES) {
            dest.toFile().setExecutable(true);
            return dest;
        }
        String entry = "driver/" + platform + "/" + nodeFileName(platform);
        URI archive = mavenDriverBundle(version);
        extractZipEntry(new HttpRangeSource(archive), entry, dest);
        dest.toFile().setExecutable(true);
        return dest;
    }

    static URI mavenDriverBundle(String version) {
        if (version == null || !version.matches("[0-9A-Za-z][0-9A-Za-z._-]{0,40}")) {
            throw new IllegalArgumentException("瀏覽器驅動版本無法使用");
        }
        return URI.create("https://repo1.maven.org/maven2/com/microsoft/playwright/driver-bundle/"
                + version + "/driver-bundle-" + version + ".jar");
    }

    static void extractZipEntry(RangeSource source, String entryName, Path dest) throws IOException {
        long length = source.length();
        if (length < 22) {
            throw new IOException("驅動壓縮檔不完整");
        }
        int tailLen = (int) Math.min(length, 65557L);
        byte[] tail = source.read(length - tailLen, length - 1);
        int eocd = findEocd(tail);
        long cdSize = u32(tail, eocd + 12);
        long cdOffset = u32(tail, eocd + 16);
        if (cdSize <= 0 || cdSize > 20L * 1024 * 1024 || cdOffset < 0 || cdOffset >= length) {
            throw new IOException("驅動壓縮檔目錄無法讀取");
        }
        byte[] central = source.read(cdOffset, cdOffset + cdSize - 1);
        ZipCentral entry = findCentral(central, entryName);
        if (entry.compressedSize <= 0 || entry.compressedSize > MAX_COMPRESSED_BYTES) {
            throw new IOException("驅動檔大小異常");
        }
        byte[] localPrefix = source.read(entry.localOffset, entry.localOffset + 29);
        if (u32(localPrefix, 0) != 0x04034b50L) {
            throw new IOException("驅動壓縮檔內容無法讀取");
        }
        int nameLen = u16(localPrefix, 26);
        int extraLen = u16(localPrefix, 28);
        long dataStart = entry.localOffset + 30L + nameLen + extraLen;
        byte[] compressed = source.read(dataStart, dataStart + entry.compressedSize - 1);
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = dest.resolveSibling(dest.getFileName().toString() + ".partial");
        try {
            writeInflated(tmp, compressed, entry.method, entry.uncompressedSize);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private static void writeInflated(Path tmp, byte[] compressed, int method, long uncompressedSize) throws IOException {
        if (method == ZipEntry.STORED) {
            Files.write(tmp, compressed);
        } else if (method == ZipEntry.DEFLATED) {
            Inflater inflater = new Inflater(true);
            try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed), inflater);
                 OutputStream out = Files.newOutputStream(tmp)) {
                in.transferTo(out);
            } finally {
                inflater.end();
            }
        } else {
            throw new IOException("不支援的驅動壓縮方式");
        }
        long actual = Files.size(tmp);
        if (uncompressedSize > 0 && actual != uncompressedSize) {
            throw new IOException("驅動檔大小不符");
        }
    }

    private static int findEocd(byte[] tail) throws IOException {
        for (int i = tail.length - 22; i >= 0; i--) {
            if (u32(tail, i) == 0x06054b50L) {
                int commentLen = u16(tail, i + 20);
                if (i + 22 + commentLen == tail.length) {
                    return i;
                }
            }
        }
        throw new IOException("驅動壓縮檔目錄無法讀取");
    }

    private static ZipCentral findCentral(byte[] central, String entryName) throws IOException {
        int pos = 0;
        while (pos + 46 <= central.length) {
            if (u32(central, pos) != 0x02014b50L) {
                break;
            }
            long compressed = u32(central, pos + 20);
            long uncompressed = u32(central, pos + 24);
            int nameLen = u16(central, pos + 28);
            int extraLen = u16(central, pos + 30);
            int commentLen = u16(central, pos + 32);
            long localOffset = u32(central, pos + 42);
            if (compressed == 0xffffffffL || uncompressed == 0xffffffffL || localOffset == 0xffffffffL) {
                throw new IOException("不支援的驅動壓縮格式");
            }
            int nameStart = pos + 46;
            if (nameStart + nameLen > central.length) {
                break;
            }
            String name = new String(central, nameStart, nameLen, StandardCharsets.UTF_8);
            if (name.equals(entryName)) {
                return new ZipCentral(u16(central, pos + 10), compressed, uncompressed, localOffset);
            }
            pos = nameStart + nameLen + extraLen + commentLen;
        }
        throw new IOException("驅動檔裡沒有需要的執行檔");
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static long u32(byte[] data, int offset) {
        return (data[offset] & 0xffL)
                | ((data[offset + 1] & 0xffL) << 8)
                | ((data[offset + 2] & 0xffL) << 16)
                | ((data[offset + 3] & 0xffL) << 24);
    }

    private static final class ZipCentral {
        final int method;
        final long compressedSize;
        final long uncompressedSize;
        final long localOffset;

        ZipCentral(int method, long compressedSize, long uncompressedSize, long localOffset) {
            this.method = method;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.localOffset = localOffset;
        }
    }

    interface RangeSource {
        long length() throws IOException;

        byte[] read(long startInclusive, long endInclusive) throws IOException;
    }

    static final class FileRangeSource implements RangeSource {
        private final Path path;
        private final long length;

        FileRangeSource(Path path) throws IOException {
            this.path = path;
            this.length = Files.size(path);
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public byte[] read(long startInclusive, long endInclusive) throws IOException {
            int size = Math.toIntExact(endInclusive - startInclusive + 1);
            byte[] buffer = new byte[size];
            try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
                file.seek(startInclusive);
                file.readFully(buffer);
            }
            return buffer;
        }
    }

    static final class HttpRangeSource implements RangeSource {
        private final URI uri;
        private long length = -1;

        HttpRangeSource(URI uri) {
            this.uri = uri;
        }

        @Override
        public long length() throws IOException {
            if (length >= 0) {
                return length;
            }
            HttpURLConnection connection = open(null, "HEAD");
            try {
                int code = connection.getResponseCode();
                long reported = connection.getContentLengthLong();
                if (code < 400 && reported > 0) {
                    length = reported;
                    return length;
                }
            } finally {
                connection.disconnect();
            }
            HttpURLConnection probe = open("bytes=0-0", "GET");
            try {
                int code = probe.getResponseCode();
                if (code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("無法取得驅動檔大小（HTTP " + code + "）");
                }
                String header = probe.getHeaderField("Content-Range");
                if (header == null || !header.contains("/")) {
                    throw new IOException("無法取得驅動檔大小");
                }
                long total = Long.parseLong(header.substring(header.lastIndexOf('/') + 1).trim());
                if (total <= 0) {
                    throw new IOException("無法取得驅動檔大小");
                }
                length = total;
                return length;
            } finally {
                probe.disconnect();
            }
        }

        @Override
        public byte[] read(long startInclusive, long endInclusive) throws IOException {
            HttpURLConnection connection = open("bytes=" + startInclusive + "-" + endInclusive, "GET");
            try {
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("無法分段下載驅動檔（HTTP " + code + "）");
                }
                try (InputStream in = connection.getInputStream()) {
                    return in.readAllBytes();
                }
            } finally {
                connection.disconnect();
            }
        }

        private HttpURLConnection open(String range, String method) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(180_000);
            connection.setUseCaches(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", "SKLbaby");
            if (range != null) {
                connection.setRequestProperty("Range", range);
            }
            return connection;
        }
    }
}
