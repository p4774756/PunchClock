package com.example.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 封閉網路／Proxy 除錯用探測：DNS、TCP、HTTP、Ping、本機 Proxy 設定。
 * Windows 公司電腦與 macOS 本機都能跑；指令依作業系統切換。
 */
public class NetworkProbeService {

    public static final String MODE_SYSTEM = "SYSTEM";
    public static final String MODE_DIRECT = "DIRECT";
    public static final String MODE_CUSTOM = "CUSTOM";

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);
    public static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    public static final int DEFAULT_PROXY_PORT = 8080;
    public static final int BODY_PREVIEW_CHARS = 400;

    static final List<String> PROXY_ENV_KEYS = Collections.unmodifiableList(Arrays.asList(
            "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "SOCKS_PROXY",
            "http_proxy", "https_proxy", "all_proxy", "no_proxy", "socks_proxy"
    ));

    static final List<String> JVM_PROXY_KEYS = Collections.unmodifiableList(Arrays.asList(
            "http.proxyHost", "http.proxyPort", "http.nonProxyHosts",
            "https.proxyHost", "https.proxyPort",
            "socksProxyHost", "socksProxyPort",
            "java.net.useSystemProxies"
    ));

    private static final Set<String> PROXY_MODES = Set.of(MODE_SYSTEM, MODE_DIRECT, MODE_CUSTOM);

    public enum OsFamily {
        WINDOWS, MAC, LINUX, OTHER
    }

    public interface CommandRunner {
        CommandOutput run(List<String> command, Duration timeout) throws IOException;
    }

    public static final class CommandOutput {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;
        public final String error;

        public CommandOutput(int exitCode, String output, boolean timedOut, String error) {
            this.exitCode = exitCode;
            this.output = output != null ? output : "";
            this.timedOut = timedOut;
            this.error = error != null ? error : "";
        }
    }

    public static final class ProbeResult {
        public final boolean ok;
        public final String title;
        public final String detail;
        public final long elapsedMs;

        public ProbeResult(boolean ok, String title, String detail, long elapsedMs) {
            this.ok = ok;
            this.title = title != null ? title : "";
            this.detail = detail != null ? detail : "";
            this.elapsedMs = elapsedMs;
        }

        public String format() {
            String status = ok ? "成功" : "失敗";
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(status).append("] ").append(title);
            if (elapsedMs >= 0) {
                sb.append("  ").append(elapsedMs).append("ms");
            }
            if (!detail.isBlank()) {
                sb.append("\n").append(indent(detail));
            }
            return sb.toString();
        }
    }

    public static final class EnvironmentSnapshot {
        public final OsFamily os;
        public final String osName;
        public final String osVersion;
        public final String javaVersion;
        public final String javaHome;
        public final Map<String, String> proxyEnvironment;
        public final Map<String, String> jvmProxyProperties;
        public final String osProxyText;

        public EnvironmentSnapshot(OsFamily os, String osName, String osVersion,
                                   String javaVersion, String javaHome,
                                   Map<String, String> proxyEnvironment,
                                   Map<String, String> jvmProxyProperties,
                                   String osProxyText) {
            this.os = os;
            this.osName = osName != null ? osName : "";
            this.osVersion = osVersion != null ? osVersion : "";
            this.javaVersion = javaVersion != null ? javaVersion : "";
            this.javaHome = javaHome != null ? javaHome : "";
            this.proxyEnvironment = proxyEnvironment;
            this.jvmProxyProperties = jvmProxyProperties;
            this.osProxyText = osProxyText != null ? osProxyText : "";
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("作業系統：").append(osName);
            if (!osVersion.isBlank()) {
                sb.append(" ").append(osVersion);
            }
            sb.append("  (").append(os.name()).append(")\n");
            sb.append("Java：").append(javaVersion);
            if (!javaHome.isBlank()) {
                sb.append("\nJAVA_HOME：").append(javaHome);
            }
            sb.append("\n\n環境變數（Java HttpClient 預設不會自動讀這些）：\n");
            appendMap(sb, proxyEnvironment);
            sb.append("\nJVM 系統屬性：\n");
            appendMap(sb, jvmProxyProperties);
            sb.append("\n作業系統 Proxy：\n");
            sb.append(osProxyText.isBlank() ? "  （無法讀取）" : indent(osProxyText.trim()));
            sb.append("\n\n提示：公司 Windows 常見「有設系統 Proxy，但 Java 沒走」。");
            sb.append("請用下方「自訂 HTTP Proxy」測試，或啟動時加 -Dhttps.proxyHost / -Djava.net.useSystemProxies=true。");
            sb.append("java.net.useSystemProxies 必須在 JVM 啟動時就設定，程式跑起來後再改通常無效。");
            return sb.toString();
        }
    }

    public static final class JvmProxyApplyResult {
        public final boolean applied;
        public final String summary;

        public JvmProxyApplyResult(boolean applied, String summary) {
            this.applied = applied;
            this.summary = summary != null ? summary : "";
        }
    }

    private final CommandRunner commandRunner;
    private final Map<String, String> environment;
    private final Properties systemProperties;

    public NetworkProbeService() {
        this(NetworkProbeService::runProcess, System.getenv(), System.getProperties());
    }

    NetworkProbeService(CommandRunner commandRunner, Map<String, String> environment, Properties systemProperties) {
        this.commandRunner = commandRunner;
        this.environment = environment != null ? environment : Collections.emptyMap();
        this.systemProperties = systemProperties != null ? systemProperties : new Properties();
    }

    public static OsFamily detectOs() {
        return detectOs(System.getProperty("os.name", ""));
    }

    static OsFamily detectOs(String osName) {
        String value = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (value.contains("win")) {
            return OsFamily.WINDOWS;
        }
        if (value.contains("mac") || value.contains("darwin")) {
            return OsFamily.MAC;
        }
        if (value.contains("nux") || value.contains("nix") || value.contains("aix")) {
            return OsFamily.LINUX;
        }
        return OsFamily.OTHER;
    }

    public static String normalizeProxyMode(String mode) {
        if (mode == null) {
            return MODE_SYSTEM;
        }
        String trimmed = mode.trim().toUpperCase(Locale.ROOT);
        return PROXY_MODES.contains(trimmed) ? trimmed : MODE_SYSTEM;
    }

    public static int clampProxyPort(int port) {
        if (port < 1 || port > 65535) {
            return DEFAULT_PROXY_PORT;
        }
        return port;
    }

    public static boolean isSafeHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.trim();
        if (value.isEmpty() || value.length() > 253) {
            return false;
        }
        if (value.equalsIgnoreCase("localhost")) {
            return true;
        }
        return value.matches("^[A-Za-z0-9._:\\[\\]-]+$") && !value.contains("..");
    }

    public static String extractHost(String target) {
        if (target == null) {
            return "";
        }
        String raw = target.trim();
        if (raw.isEmpty()) {
            return "";
        }
        try {
            if (raw.contains("://")) {
                URI uri = URI.create(raw);
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    return uri.getHost();
                }
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        String withoutPath = raw;
        int slash = raw.indexOf('/');
        if (slash >= 0) {
            withoutPath = raw.substring(0, slash);
        }
        if (withoutPath.startsWith("[") && withoutPath.contains("]")) {
            int end = withoutPath.indexOf(']');
            return withoutPath.substring(1, end);
        }
        int colon = withoutPath.lastIndexOf(':');
        if (colon > 0 && withoutPath.indexOf(':') == colon && looksLikePort(withoutPath.substring(colon + 1))) {
            return withoutPath.substring(0, colon);
        }
        return withoutPath;
    }

    public static int extractPort(String target, int fallback) {
        if (target == null) {
            return fallback;
        }
        String raw = target.trim();
        try {
            if (raw.contains("://")) {
                URI uri = URI.create(raw);
                if (uri.getPort() > 0) {
                    return uri.getPort();
                }
                if ("http".equalsIgnoreCase(uri.getScheme())) {
                    return 80;
                }
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    return 443;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
        String hostPort = raw;
        int slash = raw.indexOf('/');
        if (slash >= 0) {
            hostPort = raw.substring(0, slash);
        }
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0 && hostPort.indexOf(':') == colon && looksLikePort(hostPort.substring(colon + 1))) {
            return Integer.parseInt(hostPort.substring(colon + 1));
        }
        return fallback;
    }

    public static URI toHttpUri(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("請輸入網址或主機名稱");
        }
        String raw = target.trim();
        if (!raw.contains("://")) {
            raw = "https://" + raw;
        }
        URI uri = URI.create(raw);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("只允許 http / https");
        }
        if (uri.getHost() == null || !isSafeHost(uri.getHost())) {
            throw new IllegalArgumentException("主機名稱無效：" + raw);
        }
        return uri;
    }

    public EnvironmentSnapshot snapshotEnvironment() {
        return snapshotEnvironment(detectOs(osNameProperty()));
    }

    EnvironmentSnapshot snapshotEnvironment(OsFamily os) {
        Map<String, String> envMap = new LinkedHashMap<>();
        for (String key : PROXY_ENV_KEYS) {
            String value = firstNonBlank(environment.get(key), getenvIgnoreCase(key));
            envMap.put(key, value == null || value.isBlank() ? "（未設定）" : redactUserInfo(value));
        }
        Map<String, String> jvmMap = new LinkedHashMap<>();
        for (String key : JVM_PROXY_KEYS) {
            String value = systemProperties.getProperty(key);
            jvmMap.put(key, value == null || value.isBlank() ? "（未設定）" : value);
        }
        return new EnvironmentSnapshot(
                os,
                osNameProperty(),
                System.getProperty("os.version", ""),
                System.getProperty("java.version", ""),
                System.getProperty("java.home", ""),
                envMap,
                jvmMap,
                readOsProxy(os)
        );
    }

    public ProbeResult dnsLookup(String target) {
        long start = System.nanoTime();
        String host = extractHost(target);
        if (!isSafeHost(host)) {
            return new ProbeResult(false, "DNS " + target, "主機名稱無效，已拒絕執行（避免命令注入）", elapsedMs(start));
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            StringBuilder sb = new StringBuilder();
            sb.append("主機：").append(host).append("\n解析到 ").append(addresses.length).append(" 筆：");
            for (InetAddress address : addresses) {
                sb.append("\n  ").append(address.getHostAddress());
                if (address.getHostName() != null && !address.getHostName().equals(address.getHostAddress())) {
                    sb.append("  (").append(address.getHostName()).append(")");
                }
            }
            return new ProbeResult(true, "DNS " + host, sb.toString(), elapsedMs(start));
        } catch (Exception ex) {
            return new ProbeResult(false, "DNS " + host, explain(ex), elapsedMs(start));
        }
    }

    public ProbeResult tcpConnect(String target, int fallbackPort) {
        long start = System.nanoTime();
        String host = extractHost(target);
        int port = extractPort(target, fallbackPort);
        if (!isSafeHost(host)) {
            return new ProbeResult(false, "TCP " + target, "主機名稱無效", elapsedMs(start));
        }
        if (port < 1 || port > 65535) {
            return new ProbeResult(false, "TCP " + host, "埠號無效：" + port, elapsedMs(start));
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) DEFAULT_TIMEOUT.toMillis());
            String local = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort();
            return new ProbeResult(true, "TCP " + host + ":" + port,
                    "已連線\n本機：" + local, elapsedMs(start));
        } catch (Exception ex) {
            return new ProbeResult(false, "TCP " + host + ":" + port, explain(ex), elapsedMs(start));
        }
    }

    public ProbeResult httpGet(String target, String proxyMode, String proxyHost, int proxyPort) {
        return httpGet(target, proxyMode, proxyHost, proxyPort, "", "");
    }

    public ProbeResult httpGet(String target, String proxyMode, String proxyHost, int proxyPort,
                               String proxyUser, String proxyPassword) {
        long start = System.nanoTime();
        URI uri;
        try {
            uri = toHttpUri(target);
        } catch (IllegalArgumentException ex) {
            return new ProbeResult(false, "HTTP GET", ex.getMessage(), elapsedMs(start));
        }
        String mode = normalizeProxyMode(proxyMode);
        String user = normalizeCredential(proxyUser);
        boolean hasAuth = !user.isEmpty();
        try {
            if (hasAuth) {
                enableProxyBasicAuthSchemes();
            }
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(DEFAULT_TIMEOUT)
                    .proxy(selectorFor(mode, proxyHost, proxyPort));
            Authenticator authenticator = proxyAuthenticator(user, proxyPassword);
            if (authenticator != null) {
                builder.authenticator(authenticator);
            }
            HttpClient client = builder.build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", "PunchClock-NetworkProbe")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String preview = readPreview(response.body());
            StringBuilder sb = new StringBuilder();
            sb.append(uri).append("\n");
            sb.append("HTTP ").append(response.statusCode()).append("\n");
            sb.append("Proxy 模式：").append(modeLabel(mode)).append("\n");
            if (hasAuth) {
                sb.append("Proxy 帳號：").append(user).append("\n");
            }
            copyHeader(sb, response, "server");
            copyHeader(sb, response, "via");
            copyHeader(sb, response, "x-cache");
            copyHeader(sb, response, "location");
            copyHeader(sb, response, "content-type");
            if (response.statusCode() == 407) {
                if (hasAuth) {
                    sb.append("Proxy 仍回 407：帳密可能不對，或公司要求 NTLM／Kerberos（此 App 僅送 Basic）\n");
                } else {
                    sb.append("Proxy 要求認證：請填帳號密碼後再測，或改用系統已登入的 Proxy／PAC\n");
                }
            }
            if (!preview.isBlank()) {
                sb.append("內容預覽：\n").append(preview);
            }
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
            return new ProbeResult(ok, "HTTP GET " + uri.getHost(), sb.toString(), elapsedMs(start));
        } catch (Exception ex) {
            String extra = "";
            if (mode.equals(MODE_CUSTOM)) {
                extra = "\n目前走自訂 Proxy " + proxyHost + ":" + clampProxyPort(proxyPort);
                if (hasAuth) {
                    extra += "（有帶帳號）";
                }
            } else if (mode.equals(MODE_DIRECT)) {
                extra = "\n目前為直連（略過系統 Proxy）";
            }
            return new ProbeResult(false, "HTTP GET " + uri.getHost(), explain(ex) + extra, elapsedMs(start));
        }
    }

    public ProbeResult icmpPing(String target) {
        return icmpPing(target, detectOs(osNameProperty()));
    }

    ProbeResult icmpPing(String target, OsFamily os) {
        long start = System.nanoTime();
        String host = extractHost(target);
        if (!isSafeHost(host)) {
            return new ProbeResult(false, "Ping " + target, "主機名稱無效，已拒絕執行", elapsedMs(start));
        }
        List<String> command = pingCommand(os, host);
        try {
            CommandOutput output = commandRunner.run(command, COMMAND_TIMEOUT);
            boolean ok = !output.timedOut && output.exitCode == 0;
            StringBuilder sb = new StringBuilder();
            sb.append(String.join(" ", command)).append("\n");
            if (output.timedOut) {
                sb.append("指令逾時（公司網路常封鎖 ICMP）\n");
            } else {
                sb.append("exit=").append(output.exitCode).append("\n");
            }
            if (!output.output.isBlank()) {
                sb.append(output.output.trim());
            }
            if (!output.error.isBlank()) {
                sb.append("\n").append(output.error.trim());
            }
            if (!ok) {
                sb.append("\nICMP Ping 失敗不一定代表出不了網；很多公司防火牆只放行 80/443。");
            }
            return new ProbeResult(ok, "Ping " + host, sb.toString(), elapsedMs(start));
        } catch (IOException ex) {
            return new ProbeResult(false, "Ping " + host,
                    "無法執行 ping：" + ex.getMessage() + "\n公司電腦有時會移除 ping，可改測 TCP / HTTP。",
                    elapsedMs(start));
        }
    }

    public String diagnose(String target, String proxyMode, String proxyHost, int proxyPort, String cloudServerUrl) {
        return diagnose(target, proxyMode, proxyHost, proxyPort, "", "", cloudServerUrl);
    }

    public String diagnose(String target, String proxyMode, String proxyHost, int proxyPort,
                           String proxyUser, String proxyPassword, String cloudServerUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("一鍵診斷  ").append(Instant.now()).append("\n");
        sb.append("目標：").append(target).append("\n");
        sb.append("Proxy：").append(modeLabel(normalizeProxyMode(proxyMode)));
        if (MODE_CUSTOM.equals(normalizeProxyMode(proxyMode))) {
            sb.append("  ").append(proxyHost).append(":").append(clampProxyPort(proxyPort));
        }
        String user = normalizeCredential(proxyUser);
        if (!user.isEmpty()) {
            sb.append("  帳號=").append(user);
        }
        sb.append("\n");
        sb.append("═".repeat(32)).append("\n\n");

        EnvironmentSnapshot env = snapshotEnvironment();
        sb.append("【本機環境】\n").append(env.format()).append("\n\n");

        ProbeResult dns = dnsLookup(target);
        sb.append("【DNS】\n").append(dns.format()).append("\n\n");

        ProbeResult tcp = tcpConnect(target, 443);
        sb.append("【TCP】\n").append(tcp.format()).append("\n\n");

        ProbeResult http = httpGet(target, proxyMode, proxyHost, proxyPort, proxyUser, proxyPassword);
        sb.append("【HTTP】\n").append(http.format()).append("\n\n");

        ProbeResult ping = icmpPing(target);
        sb.append("【Ping】\n").append(ping.format()).append("\n\n");

        if (cloudServerUrl != null && !cloudServerUrl.isBlank()) {
            String pingUrl = joinUrl(cloudServerUrl.trim(), "/ping");
            ProbeResult server = httpGet(pingUrl, proxyMode, proxyHost, proxyPort, proxyUser, proxyPassword);
            sb.append("【雲端 Server /ping】\n").append(server.format()).append("\n\n");
        }

        sb.append("【建議】\n").append(suggestions(dns, tcp, http, ping, env));
        return sb.toString();
    }

    public String buildCheatSheet(String target, String proxyHost, int proxyPort) {
        return buildCheatSheet(detectOs(osNameProperty()), target, proxyHost, proxyPort);
    }

    String buildCheatSheet(OsFamily os, String target, String proxyHost, int proxyPort) {
        String host = extractHost(target);
        if (!isSafeHost(host)) {
            host = "www.google.com";
        }
        int port = extractPort(target, 443);
        String proxy = (proxyHost == null || proxyHost.isBlank())
                ? "proxy.company.com"
                : proxyHost.trim();
        int pport = clampProxyPort(proxyPort);
        StringBuilder sb = new StringBuilder();
        sb.append("指令備忘（").append(osTitle(os)).append("）\n");
        sb.append("把 proxy.company.com 換成公司 Proxy。Java 預設不讀 HTTP_PROXY。\n\n");
        if (os == OsFamily.WINDOWS) {
            sb.append("# 系統 Proxy（公司常由網域原則下發）\n");
            sb.append("netsh winhttp show proxy\n");
            sb.append("reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\"\n\n");
            sb.append("# DNS / TCP / HTTP\n");
            sb.append("nslookup ").append(host).append("\n");
            sb.append("Test-NetConnection ").append(host).append(" -Port ").append(port).append("\n");
            sb.append("curl.exe -v https://").append(host).append("\n");
            sb.append("curl.exe -x http://").append(proxy).append(":").append(pport)
                    .append(" -v https://").append(host).append("\n\n");
            sb.append("# Ping（常被封鎖）\n");
            sb.append("ping -n 4 ").append(host).append("\n");
        } else if (os == OsFamily.MAC) {
            sb.append("# 系統 Proxy\n");
            sb.append("scutil --proxy\n");
            sb.append("networksetup -getwebproxy Wi-Fi\n");
            sb.append("networksetup -getsecurewebproxy Wi-Fi\n");
            sb.append("networksetup -getautoproxyurl Wi-Fi\n\n");
            sb.append("# DNS / TCP / HTTP\n");
            sb.append("dscacheutil -q host -a name ").append(host).append("\n");
            sb.append("nc -vz ").append(host).append(" ").append(port).append("\n");
            sb.append("curl -v https://").append(host).append("\n");
            sb.append("curl -x http://").append(proxy).append(":").append(pport)
                    .append(" -v https://").append(host).append("\n\n");
            sb.append("# Ping（常被封鎖）\n");
            sb.append("ping -c 4 ").append(host).append("\n");
        } else {
            sb.append("# 環境變數與 HTTP\n");
            sb.append("env | grep -i proxy\n");
            sb.append("getent hosts ").append(host).append("\n");
            sb.append("nc -vz ").append(host).append(" ").append(port).append("\n");
            sb.append("curl -v https://").append(host).append("\n");
            sb.append("curl -x http://").append(proxy).append(":").append(pport)
                    .append(" -v https://").append(host).append("\n");
            sb.append("ping -c 4 ").append(host).append("\n");
        }
        sb.append("\n# 讓 PunchClock 桌面端走 Proxy（啟動參數）\n");
        sb.append("java -Dhttps.proxyHost=").append(proxy)
                .append(" -Dhttps.proxyPort=").append(pport)
                .append(" -Dhttp.proxyHost=").append(proxy)
                .append(" -Dhttp.proxyPort=").append(pport)
                .append(" -Dhttp.nonProxyHosts=\"localhost|127.0.0.1\"")
                .append(" -jar punchclock-client-standalone.jar\n\n");
        sb.append("# 或嘗試使用作業系統 Proxy（需啟動時就加，Windows 公司機常用）\n");
        sb.append("java -Djava.net.useSystemProxies=true -jar punchclock-client-standalone.jar\n\n");
        sb.append("自動執行用的 Playwright 瀏覽器不一定跟隨 JVM Proxy；");
        sb.append("若排程任務打不開網頁，請在系統或瀏覽器設定 Proxy，或設定 HTTP_PROXY 環境變數後重開 App。");
        return sb.toString();
    }

    public JvmProxyApplyResult applyJvmProxy(String proxyMode, String proxyHost, int proxyPort) {
        return applyJvmProxy(proxyMode, proxyHost, proxyPort, "", "");
    }

    public JvmProxyApplyResult applyJvmProxy(String proxyMode, String proxyHost, int proxyPort,
                                             String proxyUser, String proxyPassword) {
        String mode = normalizeProxyMode(proxyMode);
        if (MODE_DIRECT.equals(mode)) {
            clearJvmProxyProperties();
            systemProperties.setProperty("java.net.useSystemProxies", "false");
            clearInstalledProxyCredentials();
            return new JvmProxyApplyResult(true,
                    "已清除 JVM Proxy，改為直連。對已建立的連線可能要停用再啟用「雲端狀態回報」。");
        }
        if (MODE_SYSTEM.equals(mode)) {
            clearJvmProxyHostProperties();
            systemProperties.setProperty("java.net.useSystemProxies", "true");
            // 系統 Proxy 仍可用本分頁帳密（若公司 Proxy 要 Basic）
            installProxyCredentials(proxyUser, proxyPassword);
            String authNote = normalizeCredential(proxyUser).isEmpty()
                    ? ""
                    : " 已安裝 Proxy 帳號（Basic）。";
            return new JvmProxyApplyResult(true,
                    "已設定 java.net.useSystemProxies=true。" + authNote
                            + "此屬性通常必須在啟動 JVM 時就存在才有效；若還是沒走 Proxy，請用啟動參數或改「自訂 HTTP Proxy」。");
        }
        if (!isSafeHost(proxyHost)) {
            return new JvmProxyApplyResult(false, "Proxy 主機無效");
        }
        int port = clampProxyPort(proxyPort);
        systemProperties.setProperty("http.proxyHost", proxyHost.trim());
        systemProperties.setProperty("http.proxyPort", String.valueOf(port));
        systemProperties.setProperty("https.proxyHost", proxyHost.trim());
        systemProperties.setProperty("https.proxyPort", String.valueOf(port));
        systemProperties.setProperty("http.nonProxyHosts", "localhost|127.0.0.1|*.local");
        systemProperties.setProperty("java.net.useSystemProxies", "false");
        installProxyCredentials(proxyUser, proxyPassword);
        String user = normalizeCredential(proxyUser);
        String authNote = user.isEmpty() ? "" : "（帳號 " + user + "）";
        return new JvmProxyApplyResult(true,
                "已套用 JVM HTTP/HTTPS Proxy " + proxyHost.trim() + ":" + port + authNote
                        + "。請停用再啟用「雲端狀態回報」讓心跳改走新設定。Playwright 瀏覽器不會自動跟著改。");
    }

    public static String joinUrl(String base, String path) {
        if (base == null || base.isBlank()) {
            return path;
        }
        String root = base.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (path == null || path.isBlank()) {
            return root;
        }
        return path.startsWith("/") ? root + path : root + "/" + path;
    }

    public static String modeLabel(String mode) {
        switch (normalizeProxyMode(mode)) {
            case MODE_DIRECT:
                return "直連（略過 Proxy）";
            case MODE_CUSTOM:
                return "自訂 HTTP Proxy";
            default:
                return "系統 Proxy";
        }
    }

    static List<String> pingCommand(OsFamily os, String host) {
        if (os == OsFamily.WINDOWS) {
            return Arrays.asList("ping", "-n", "2", "-w", "2000", host);
        }
        return Arrays.asList("ping", "-c", "2", host);
    }

    static List<String> osProxyCommand(OsFamily os) {
        if (os == OsFamily.WINDOWS) {
            return Arrays.asList("netsh", "winhttp", "show", "proxy");
        }
        if (os == OsFamily.MAC) {
            return Arrays.asList("scutil", "--proxy");
        }
        return Collections.emptyList();
    }

    static String suggestions(ProbeResult dns, ProbeResult tcp, ProbeResult http, ProbeResult ping,
                              EnvironmentSnapshot env) {
        List<String> lines = new ArrayList<>();
        boolean envHasProxy = envHasConfiguredProxy(env);
        if (!dns.ok && !tcp.ok) {
            lines.add("DNS 與 TCP 都失敗：可能沒有出網，或必須走 Proxy。請填公司 Proxy 後改「自訂 HTTP Proxy」再測一次。");
        } else if (!dns.ok && tcp.ok) {
            lines.add("DNS 失敗但 TCP 成功：名稱解析被攔。可改連 IP，或把 DNS 設成公司內部位址。");
        } else if (dns.ok && !tcp.ok) {
            lines.add("DNS 成功但 TCP 失敗：防火牆可能擋住目標埠。公司常見只開放 80/443，且強制走 Proxy。");
        }
        if (!http.ok) {
            if (http.detail.contains("407")) {
                lines.add("HTTP 407：Proxy 要帳密。請在網路測試填帳號密碼後再測／套用；若仍失敗，公司可能要求 NTLM／Kerberos（此 App 僅 Basic）。");
            } else if (http.detail.toLowerCase(Locale.ROOT).contains("pkix")
                    || http.detail.toLowerCase(Locale.ROOT).contains("ssl")
                    || http.detail.toLowerCase(Locale.ROOT).contains("certificate")) {
                lines.add("SSL 失敗：公司常有 HTTPS 解密。可到「雲端設定」暫開「信任所有 SSL（除錯）」，或匯入公司根憑證。");
            } else if (envHasProxy && MODE_SYSTEM.equals(guessModeFrom(env))) {
                lines.add("系統／環境有 Proxy，但 Java 請求仍失敗：HttpClient 不會讀 HTTP_PROXY。請用「自訂 HTTP Proxy」或啟動參數 -Dhttps.proxyHost。");
            } else {
                lines.add("HTTP 失敗：先用「直連」與「自訂 Proxy」各測一次，看哪一種通。");
            }
        } else {
            lines.add("HTTP 成功：這條路徑出得了網。若心跳仍失敗，多半是 Token／網址，不是網路。");
        }
        if (!ping.ok && http.ok) {
            lines.add("Ping 失敗但 HTTP 成功：正常，忽略 ICMP 即可。");
        }
        if (!envHasProxy && !http.ok) {
            lines.add("本機看不到 Proxy 設定。Windows 可跑 netsh winhttp show proxy；Mac 可跑 scutil --proxy。有 PAC 網址時瀏覽器通、Java 不一定通。");
        }
        lines.add("Mac 與 Windows 的系統 Proxy 位置不同；換電腦請按「重新掃描環境」。");
        return String.join("\n", lines);
    }

    private String readOsProxy(OsFamily os) {
        List<String> command = osProxyCommand(os);
        if (command.isEmpty()) {
            return "Linux／其他：請看上方環境變數，或由桌面環境設定 Proxy。";
        }
        try {
            CommandOutput output = commandRunner.run(command, COMMAND_TIMEOUT);
            if (output.timedOut) {
                return String.join(" ", command) + "\n（逾時）";
            }
            String text = output.output.isBlank() ? output.error : output.output;
            if (text.isBlank()) {
                return String.join(" ", command) + "\nexit=" + output.exitCode + "（無輸出）";
            }
            return text.trim();
        } catch (IOException ex) {
            return "無法執行 " + String.join(" ", command) + "：" + ex.getMessage();
        }
    }

    private ProxySelector selectorFor(String mode, String proxyHost, int proxyPort) {
        if (MODE_DIRECT.equals(mode)) {
            return new FixedProxySelector(Proxy.NO_PROXY);
        }
        if (MODE_CUSTOM.equals(mode)) {
            if (!isSafeHost(proxyHost)) {
                throw new IllegalArgumentException("Proxy 主機無效");
            }
            InetSocketAddress address = new InetSocketAddress(proxyHost.trim(), clampProxyPort(proxyPort));
            return ProxySelector.of(address);
        }
        ProxySelector system = ProxySelector.getDefault();
        return system != null ? system : new FixedProxySelector(Proxy.NO_PROXY);
    }

    private void clearJvmProxyProperties() {
        clearJvmProxyHostProperties();
        systemProperties.remove("java.net.useSystemProxies");
    }

    private void clearJvmProxyHostProperties() {
        systemProperties.remove("http.proxyHost");
        systemProperties.remove("http.proxyPort");
        systemProperties.remove("https.proxyHost");
        systemProperties.remove("https.proxyPort");
        systemProperties.remove("socksProxyHost");
        systemProperties.remove("socksProxyPort");
        systemProperties.remove("http.proxyUser");
        systemProperties.remove("http.proxyPassword");
        systemProperties.remove("https.proxyUser");
        systemProperties.remove("https.proxyPassword");
    }

    static String normalizeCredential(String value) {
        return value == null ? "" : value.trim();
    }

    static Authenticator proxyAuthenticator(String proxyUser, String proxyPassword) {
        String user = normalizeCredential(proxyUser);
        if (user.isEmpty()) {
            return null;
        }
        char[] password = proxyPassword != null ? proxyPassword.toCharArray() : new char[0];
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(user, password);
                }
                return null;
            }
        };
    }

    private void installProxyCredentials(String proxyUser, String proxyPassword) {
        String user = normalizeCredential(proxyUser);
        if (user.isEmpty()) {
            clearInstalledProxyCredentials();
            return;
        }
        String password = proxyPassword != null ? proxyPassword : "";
        systemProperties.setProperty("http.proxyUser", user);
        systemProperties.setProperty("http.proxyPassword", password);
        systemProperties.setProperty("https.proxyUser", user);
        systemProperties.setProperty("https.proxyPassword", password);
        enableProxyBasicAuthSchemes();
        Authenticator.setDefault(proxyAuthenticator(user, password));
    }

    private void clearInstalledProxyCredentials() {
        systemProperties.remove("http.proxyUser");
        systemProperties.remove("http.proxyPassword");
        systemProperties.remove("https.proxyUser");
        systemProperties.remove("https.proxyPassword");
        Authenticator.setDefault(null);
    }

    /**
     * JDK 預設常關閉 HTTPS CONNECT 的 Basic，導致帳密填了仍 407。
     */
    private void enableProxyBasicAuthSchemes() {
        systemProperties.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        systemProperties.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
    }

    private String getenvIgnoreCase(String key) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String osNameProperty() {
        String fromProps = systemProperties.getProperty("os.name");
        return fromProps != null ? fromProps : System.getProperty("os.name", "");
    }

    static CommandOutput runProcess(List<String> command, Duration timeout) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> copyQuietly(process.getInputStream(), buffer), "network-probe-cmd");
        reader.setDaemon(true);
        reader.start();
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandOutput(-1, buffer.toString(StandardCharsets.UTF_8), true, "interrupted");
        }
        if (!finished) {
            process.destroyForcibly();
            try {
                reader.join(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new CommandOutput(-1, buffer.toString(StandardCharsets.UTF_8), true, "timed out");
        }
        try {
            reader.join(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return new CommandOutput(process.exitValue(), buffer.toString(StandardCharsets.UTF_8), false, "");
    }

    private static void copyQuietly(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                if (out.size() > 64 * 1024) {
                    break;
                }
            }
        } catch (IOException ignored) {
            // process closed
        }
    }

    private static String readPreview(InputStream body) throws IOException {
        if (body == null) {
            return "";
        }
        byte[] buf = new byte[2048];
        int n = body.read(buf);
        try {
            body.close();
        } catch (IOException ignored) {
            // ignore
        }
        if (n <= 0) {
            return "";
        }
        String text = new String(buf, 0, n, StandardCharsets.UTF_8).replace("\r", "");
        if (text.length() > BODY_PREVIEW_CHARS) {
            return text.substring(0, BODY_PREVIEW_CHARS) + "…";
        }
        return text;
    }

    private static void copyHeader(StringBuilder sb, HttpResponse<?> response, String name) {
        response.headers().firstValue(name).ifPresent(value ->
                sb.append(name).append(": ").append(value).append("\n"));
    }

    static String explain(Throwable ex) {
        Throwable current = ex;
        List<String> parts = new ArrayList<>();
        while (current != null && parts.size() < 4) {
            String name = current.getClass().getSimpleName();
            String msg = current.getMessage();
            parts.add(msg == null || msg.isBlank() ? name : name + ": " + msg);
            current = current.getCause();
        }
        String joined = String.join("\n", parts);
        String lower = joined.toLowerCase(Locale.ROOT);
        if (lower.contains("unknownhost")) {
            return joined + "\n名稱解析失敗（DNS 或主機不存在）";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return joined + "\n連線逾時：可能被防火牆丟包，或必須走 Proxy";
        }
        if (lower.contains("pkix") || lower.contains("certificate") || lower.contains("sslhandshake")) {
            return joined + "\nSSL 憑證不被信任：公司常有 HTTPS 攔截";
        }
        if (lower.contains("connection refused")) {
            return joined + "\n連線被拒：對方沒開埠，或 Proxy 位址打錯";
        }
        return joined;
    }

    static String redactUserInfo(String value) {
        int scheme = value.indexOf("://");
        int at = value.indexOf('@');
        if (scheme >= 0 && at > scheme) {
            return value.substring(0, scheme + 3) + "***@" + value.substring(at + 1);
        }
        return value;
    }

    private static boolean looksLikePort(String value) {
        if (value == null || value.isEmpty() || value.length() > 5) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65535;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static void appendMap(StringBuilder sb, Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            sb.append("  （無）\n");
            return;
        }
        Map<String, String> sorted = new TreeMap<>(map);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append("  ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
    }

    private static String indent(String text) {
        String[] lines = text.replace("\r", "").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("  ").append(lines[i]);
        }
        return sb.toString();
    }

    private static String osTitle(OsFamily os) {
        switch (os) {
            case WINDOWS:
                return "Windows";
            case MAC:
                return "macOS";
            case LINUX:
                return "Linux";
            default:
                return "其他";
        }
    }

    private static boolean envHasConfiguredProxy(EnvironmentSnapshot env) {
        for (String value : env.proxyEnvironment.values()) {
            if (value != null && !value.contains("未設定")) {
                return true;
            }
        }
        for (Map.Entry<String, String> entry : env.jvmProxyProperties.entrySet()) {
            if (entry.getKey().contains("proxyHost") && entry.getValue() != null && !entry.getValue().contains("未設定")) {
                return true;
            }
        }
        String osText = env.osProxyText.toLowerCase(Locale.ROOT);
        return osText.contains("proxyserver") || osText.contains("httpenable")
                || osText.contains("proxyenable") || osText.contains("http.proxy")
                || osText.contains("httpsenable");
    }

    private static String guessModeFrom(EnvironmentSnapshot env) {
        String host = env.jvmProxyProperties.get("http.proxyHost");
        if (host != null && !host.contains("未設定")) {
            return MODE_CUSTOM;
        }
        return MODE_SYSTEM;
    }

    private static long elapsedMs(long startNano) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final List<Proxy> proxies;

        private FixedProxySelector(Proxy proxy) {
            this.proxies = Collections.singletonList(proxy);
        }

        @Override
        public List<Proxy> select(URI uri) {
            return proxies;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            // no-op
        }
    }
}
