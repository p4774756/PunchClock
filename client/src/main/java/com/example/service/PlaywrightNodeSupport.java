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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 正式 jar 不含任何平台的 Playwright driver（{@code driver/**} 於 shade 排除）。
 * 首次需要 Playwright 時自微軟 CDN 下載並解壓至 {@code ~/.punchclock/playwright-driver/<版本>/}，
 * 以 {@code playwright.cli.dir} 指向；開發／測試 classpath 若仍內嵌 driver 則不另外下載。
 */
public final class PlaywrightNodeSupport {

    static final String NODEJS_PATH_PROPERTY = "playwright.nodejs.path";
    static final String CLI_DIR_PROPERTY = "playwright.cli.dir";
    static final String INSTALL_MARKER = "driver.zip.sha256";

    private static final Pattern VERSION_FIELD = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    private static final long MIN_NODE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_COMPRESSED_BYTES = 80L * 1024 * 1024;
    private static final int DOWNLOAD_BUFFER = 64 * 1024;

    private PlaywrightNodeSupport() {}

    public static void ensureNodeForPackagedApp(Consumer<String> logger) {
        if (embeddedNodePresent()) {
            return;
        }
        String existingCli = System.getProperty(CLI_DIR_PROPERTY, "").trim();
        if (!existingCli.isEmpty()) {
            try {
                Path existing = Path.of(existingCli);
                if (isDriverInstalled(existing, platformId())) {
                    return;
                }
            } catch (IOException ignored) {
            }
        }
        if (!System.getProperty(NODEJS_PATH_PROPERTY, "").trim().isEmpty()) {
            return;
        }
        try {
            Path home = Path.of(System.getProperty("user.home", "."));
            String platform = platformId();
            String version = resolvedPlaywrightVersion();
            Path installDir = driverInstallDir(home, version, platform);
            if (isDriverInstalled(installDir, platform) && hasValidMarker(installDir)) {
                applyCliDir(installDir, logger);
                return;
            }
            if (logger != null) {
                logger.accept("[初始化] 正在下載瀏覽器驅動（首次需連網，約數十 MB）…");
            }
            downloadAndInstallDriver(home, version, platform, installDir, logger);
            applyCliDir(installDir, logger);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "無法準備瀏覽器驅動：" + e.getMessage()
                            + "。請確認可連線至 playwright.azureedge.net 或 cdn.playwright.dev。",
                    e);
        }
    }

    private static void applyCliDir(Path installDir, Consumer<String> logger) {
        String absolute = installDir.toAbsolutePath().normalize().toString();
        System.setProperty(CLI_DIR_PROPERTY, absolute);
        if (logger != null) {
            logger.accept("[初始化] 瀏覽器驅動就緒：" + absolute);
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

    static Path driverInstallDir(Path home, String version, String platform) {
        return home.resolve(".punchclock")
                .resolve("playwright-driver")
                .resolve(version)
                .resolve(platform);
    }

    static Path nodeDestination(Path home, String version, String platform) {
        return driverInstallDir(home, version, platform).resolve(nodeFileName(platform));
    }

    static URI driverZipUrl(String version, String platform, boolean cdnFallback) {
        if (version == null || !version.matches("[0-9A-Za-z][0-9A-Za-z._-]{0,40}")
                || platform == null || !platform.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("瀏覽器驅動版本或平台無法使用");
        }
        String host = cdnFallback
                ? "https://cdn.playwright.dev/builds/driver"
                : "https://playwright.azureedge.net/builds/driver";
        return URI.create(host + "/playwright-" + version + "-" + platform + ".zip");
    }

    static String resolvedPlaywrightVersion() throws IOException {
        if (embeddedNodePresent()) {
            return playwrightVersion(platformId());
        }
        return readBundledPlaywrightVersion();
    }

    static String readBundledPlaywrightVersion() throws IOException {
        String resource = "/META-INF/maven/com.microsoft.playwright/playwright/pom.properties";
        try (InputStream in = PlaywrightNodeSupport.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("找不到 Playwright 版本");
            }
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version", "").trim();
            if (!version.matches("[0-9A-Za-z][0-9A-Za-z._-]{0,40}")) {
                throw new IOException("Playwright 版本無法使用");
            }
            return version;
        }
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

    static boolean isDriverInstalled(Path dir, String platform) throws IOException {
        Path node = dir.resolve(nodeFileName(platform));
        if (!Files.isRegularFile(node) || Files.size(node) < MIN_NODE_BYTES) {
            return false;
        }
        return Files.isRegularFile(dir.resolve("package").resolve("cli.js"));
    }

    static boolean hasValidMarker(Path installDir) throws IOException {
        Path marker = installDir.resolve(INSTALL_MARKER);
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        String stored = Files.readString(marker, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        return stored.matches("[0-9a-f]{64}");
    }

    static void verifySha256Hex(String expected, String actual) throws IOException {
        if (expected == null || actual == null
                || !expected.equalsIgnoreCase(actual.trim())) {
            throw new IOException("瀏覽器驅動完整性驗證失敗（SHA-256 不符）");
        }
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256Hex(InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[DOWNLOAD_BUFFER];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] digest) {
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static void downloadAndInstallDriver(
            Path home, String version, String platform, Path installDir, Consumer<String> logger)
            throws IOException {
        IOException primaryFailure = null;
        for (boolean fallback : new boolean[] {false, true}) {
            try {
                downloadAndInstallFrom(installDir, version, platform, fallback, logger);
                return;
            } catch (IOException e) {
                if (primaryFailure == null) {
                    primaryFailure = e;
                }
            }
        }
        throw primaryFailure != null ? primaryFailure : new IOException("無法下載瀏覽器驅動");
    }

    private static void downloadAndInstallFrom(
            Path installDir, String version, String platform, boolean cdnFallback, Consumer<String> logger)
            throws IOException {
        URI uri = driverZipUrl(version, platform, cdnFallback);
        Path parent = installDir.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmpZip = Files.createTempFile(parent != null ? parent : installDir, "pw-driver-", ".zip");
        try {
            HttpURLConnection connection = openDownload(uri);
            try {
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("下載失敗（HTTP " + code + "）：" + uri);
                }
                long total = connection.getContentLengthLong();
                try (InputStream in = connection.getInputStream();
                     OutputStream out = Files.newOutputStream(tmpZip)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[DOWNLOAD_BUFFER];
                    long done = 0;
                    int lastPct = -1;
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                            out.write(buffer, 0, read);
                            done += read;
                            if (logger != null && total > 0) {
                                int pct = (int) Math.min(100, (done * 100) / total);
                                if (pct >= lastPct + 10 || pct == 100) {
                                    lastPct = pct;
                                    logger.accept("[初始化] 瀏覽器驅動下載中… " + pct + "%");
                                }
                            }
                        }
                    }
                    String hash = toHex(digest.digest());
                    if (Files.isDirectory(installDir)) {
                        cleanInstallDir(installDir);
                    }
                    Files.createDirectories(installDir);
                    extractZip(tmpZip, installDir);
                    Path node = installDir.resolve(nodeFileName(platform));
                    if (!Files.isRegularFile(node) || Files.size(node) < MIN_NODE_BYTES) {
                        throw new IOException("解壓後找不到瀏覽器驅動執行檔");
                    }
                    node.toFile().setExecutable(true);
                    if (!Files.isRegularFile(installDir.resolve("package").resolve("cli.js"))) {
                        throw new IOException("解壓後瀏覽器驅動不完整");
                    }
                    Files.writeString(installDir.resolve(INSTALL_MARKER), hash + "\n", StandardCharsets.UTF_8);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            } finally {
                connection.disconnect();
            }
        } finally {
            Files.deleteIfExists(tmpZip);
        }
    }

    private static void cleanInstallDir(Path installDir) throws IOException {
        if (!Files.isDirectory(installDir)) {
            return;
        }
        try (var stream = Files.walk(installDir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .filter(path -> !path.equals(installDir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private static void extractZip(Path zip, Path dest) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(dest.resolve(entry.getName()));
                } else {
                    Path target = dest.resolve(entry.getName()).normalize();
                    if (!target.startsWith(dest)) {
                        throw new IOException("驅動壓縮檔路徑異常");
                    }
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                in.closeEntry();
            }
        }
    }

    private static HttpURLConnection openDownload(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(600_000);
        connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SKLbaby");
        return connection;
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
            HttpURLConnection connection = openRange(null, "HEAD");
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
            HttpURLConnection probe = openRange("bytes=0-0", "GET");
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
            HttpURLConnection connection = openRange("bytes=" + startInclusive + "-" + endInclusive, "GET");
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

        private HttpURLConnection openRange(String range, String method) throws IOException {
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