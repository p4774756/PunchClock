package com.example.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NetworkProbeServiceTest {

    private HttpServer httpServer;

    @After
    public void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Test
    public void detectOs_classifiesWindowsMacLinux() {
        assertEquals(NetworkProbeService.OsFamily.WINDOWS, NetworkProbeService.detectOs("Windows 10"));
        assertEquals(NetworkProbeService.OsFamily.WINDOWS, NetworkProbeService.detectOs("Windows 11"));
        assertEquals(NetworkProbeService.OsFamily.MAC, NetworkProbeService.detectOs("Mac OS X"));
        assertEquals(NetworkProbeService.OsFamily.MAC, NetworkProbeService.detectOs("macOS"));
        assertEquals(NetworkProbeService.OsFamily.LINUX, NetworkProbeService.detectOs("Linux"));
    }

    @Test
    public void extractHostAndPort_fromUrlAndHostPort() {
        assertEquals("example.com", NetworkProbeService.extractHost("https://example.com/ping"));
        assertEquals(443, NetworkProbeService.extractPort("https://example.com/ping", 80));
        assertEquals("example.com", NetworkProbeService.extractHost("example.com:8080"));
        assertEquals(8080, NetworkProbeService.extractPort("example.com:8080", 443));
        assertEquals("127.0.0.1", NetworkProbeService.extractHost("http://127.0.0.1:3000/ping"));
        assertEquals(3000, NetworkProbeService.extractPort("http://127.0.0.1:3000/ping", 80));
    }

    @Test
    public void isSafeHost_rejectsInjection() {
        assertTrue(NetworkProbeService.isSafeHost("google.com"));
        assertTrue(NetworkProbeService.isSafeHost("127.0.0.1"));
        assertTrue(NetworkProbeService.isSafeHost("localhost"));
        assertFalse(NetworkProbeService.isSafeHost("google.com; rm -rf /"));
        assertFalse(NetworkProbeService.isSafeHost("host && calc"));
        assertFalse(NetworkProbeService.isSafeHost("host|whoami"));
        assertFalse(NetworkProbeService.isSafeHost(""));
        assertFalse(NetworkProbeService.isSafeHost("host with space"));
    }

    @Test
    public void toHttpUri_defaultsHttpsAndRejectsOtherSchemes() {
        assertEquals("https://example.com", NetworkProbeService.toHttpUri("example.com").toString());
        try {
            NetworkProbeService.toHttpUri("file:///etc/passwd");
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("http"));
        }
    }

    @Test
    public void pingCommand_isOsSpecific() {
        assertEquals(
                java.util.Arrays.asList("ping", "-n", "2", "-w", "2000", "8.8.8.8"),
                NetworkProbeService.pingCommand(NetworkProbeService.OsFamily.WINDOWS, "8.8.8.8"));
        assertEquals(
                java.util.Arrays.asList("ping", "-c", "2", "8.8.8.8"),
                NetworkProbeService.pingCommand(NetworkProbeService.OsFamily.MAC, "8.8.8.8"));
    }

    @Test
    public void osProxyCommand_isOsSpecific() {
        assertEquals(
                java.util.Arrays.asList("netsh", "winhttp", "show", "proxy"),
                NetworkProbeService.osProxyCommand(NetworkProbeService.OsFamily.WINDOWS));
        assertEquals(
                java.util.Arrays.asList("scutil", "--proxy"),
                NetworkProbeService.osProxyCommand(NetworkProbeService.OsFamily.MAC));
        assertTrue(NetworkProbeService.osProxyCommand(NetworkProbeService.OsFamily.LINUX).isEmpty());
    }

    @Test
    public void snapshotEnvironment_readsProxyEnvAndCommandOutput() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HTTP_PROXY", "http://user:secret@proxy.company.com:8080");
        env.put("NO_PROXY", "localhost");
        Properties props = new Properties();
        props.setProperty("os.name", "Windows 11");
        props.setProperty("https.proxyHost", "proxy.company.com");
        AtomicReference<List<String>> seen = new AtomicReference<>();
        NetworkProbeService service = new NetworkProbeService((command, timeout) -> {
            seen.set(new ArrayList<>(command));
            return new NetworkProbeService.CommandOutput(0, "Current WinHTTP proxy settings:\n    Proxy Server: proxy.company.com:8080", false, "");
        }, env, props);

        NetworkProbeService.EnvironmentSnapshot snapshot =
                service.snapshotEnvironment(NetworkProbeService.OsFamily.WINDOWS);

        assertEquals(NetworkProbeService.OsFamily.WINDOWS, snapshot.os);
        assertTrue(snapshot.proxyEnvironment.get("HTTP_PROXY").contains("proxy.company.com"));
        assertFalse(snapshot.proxyEnvironment.get("HTTP_PROXY").contains("secret"));
        assertEquals("localhost", snapshot.proxyEnvironment.get("NO_PROXY"));
        assertEquals("proxy.company.com", snapshot.jvmProxyProperties.get("https.proxyHost"));
        assertTrue(snapshot.osProxyText.contains("proxy.company.com:8080"));
        assertEquals(java.util.Arrays.asList("netsh", "winhttp", "show", "proxy"), seen.get());
        String formatted = snapshot.format();
        assertTrue(formatted.contains("Java HttpClient"));
        assertTrue(formatted.contains("Windows"));
    }

    @Test
    public void dnsLookup_resolvesLocalhost() {
        NetworkProbeService service = new NetworkProbeService();
        NetworkProbeService.ProbeResult result = service.dnsLookup("localhost");
        assertTrue(result.format(), result.ok);
        assertTrue(result.detail.contains("127.0.0.1") || result.detail.contains("::1"));
    }

    @Test
    public void dnsLookup_rejectsUnsafeHost() {
        NetworkProbeService service = new NetworkProbeService();
        NetworkProbeService.ProbeResult result = service.dnsLookup("evil.com; whoami");
        assertFalse(result.ok);
        assertTrue(result.detail.contains("拒絕"));
    }

    @Test
    public void tcpConnect_reachesLocalServerSocket() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = server.getLocalPort();
            Thread acceptor = new Thread(() -> {
                try {
                    server.accept().close();
                } catch (Exception ignored) {
                    // test cleanup
                }
            }, "tcp-accept");
            acceptor.setDaemon(true);
            acceptor.start();

            NetworkProbeService service = new NetworkProbeService();
            NetworkProbeService.ProbeResult result = service.tcpConnect("127.0.0.1:" + port, 443);
            assertTrue(result.format(), result.ok);
            assertTrue(result.title.contains(String.valueOf(port)));
        }
    }

    @Test
    public void httpGet_directHitsLocalPingEndpoint() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/ping", exchange -> {
            byte[] body = "{\"message\":\"pong\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        String url = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/ping";

        NetworkProbeService service = new NetworkProbeService();
        NetworkProbeService.ProbeResult result =
                service.httpGet(url, NetworkProbeService.MODE_DIRECT, "", 8080);
        assertTrue(result.format(), result.ok);
        assertTrue(result.detail.contains("HTTP 200"));
        assertTrue(result.detail.contains("pong"));
        assertTrue(result.detail.contains("直連"));
    }

    @Test
    public void icmpPing_usesInjectedCommandAndWindowsArgs() {
        AtomicReference<List<String>> seen = new AtomicReference<>();
        NetworkProbeService service = new NetworkProbeService((command, timeout) -> {
            seen.set(new ArrayList<>(command));
            assertTrue(timeout.toMillis() > 0);
            return new NetworkProbeService.CommandOutput(0, "Reply from 127.0.0.1: bytes=32", false, "");
        }, Map.of(), new Properties());

        NetworkProbeService.ProbeResult result =
                service.icmpPing("127.0.0.1", NetworkProbeService.OsFamily.WINDOWS);
        assertTrue(result.ok);
        assertEquals(java.util.Arrays.asList("ping", "-n", "2", "-w", "2000", "127.0.0.1"), seen.get());
        assertTrue(result.detail.contains("Reply from 127.0.0.1"));
    }

    @Test
    public void icmpPing_doesNotRunUnsafeHost() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        NetworkProbeService service = new NetworkProbeService((command, timeout) -> {
            ran.set(true);
            return new NetworkProbeService.CommandOutput(0, "ok", false, "");
        }, Map.of(), new Properties());
        NetworkProbeService.ProbeResult result =
                service.icmpPing("x.com & calc", NetworkProbeService.OsFamily.MAC);
        assertFalse(result.ok);
        assertFalse(ran.get());
    }

    @Test
    public void cheatSheet_containsWindowsAndMacCommands() {
        NetworkProbeService service = new NetworkProbeService();
        String windows = service.buildCheatSheet(
                NetworkProbeService.OsFamily.WINDOWS, "https://example.com", "10.0.0.1", 3128);
        assertTrue(windows.contains("netsh winhttp show proxy"));
        assertTrue(windows.contains("Test-NetConnection example.com -Port 443"));
        assertTrue(windows.contains("curl.exe -x http://10.0.0.1:3128"));
        assertTrue(windows.contains("-Dhttps.proxyHost=10.0.0.1"));
        assertTrue(windows.contains("java.net.useSystemProxies=true"));

        String mac = service.buildCheatSheet(
                NetworkProbeService.OsFamily.MAC, "https://example.com", "10.0.0.1", 3128);
        assertTrue(mac.contains("scutil --proxy"));
        assertTrue(mac.contains("networksetup -getwebproxy Wi-Fi"));
        assertTrue(mac.contains("nc -vz example.com 443"));
        assertTrue(mac.contains("curl -x http://10.0.0.1:3128"));
    }

    @Test
    public void applyJvmProxy_setsAndClearsProperties() {
        Properties props = new Properties();
        NetworkProbeService service = new NetworkProbeService(
                (command, timeout) -> new NetworkProbeService.CommandOutput(0, "", false, ""),
                Map.of(), props);

        NetworkProbeService.JvmProxyApplyResult custom =
                service.applyJvmProxy(NetworkProbeService.MODE_CUSTOM, "proxy.company.com", 8080,
                        "alice", "secret");
        assertTrue(custom.applied);
        assertEquals("proxy.company.com", props.getProperty("https.proxyHost"));
        assertEquals("8080", props.getProperty("https.proxyPort"));
        assertEquals("false", props.getProperty("java.net.useSystemProxies"));
        assertEquals("alice", props.getProperty("http.proxyUser"));
        assertEquals("secret", props.getProperty("http.proxyPassword"));
        assertEquals("", props.getProperty("jdk.http.auth.tunneling.disabledSchemes"));
        assertNotNull(java.net.Authenticator.getDefault());

        NetworkProbeService.JvmProxyApplyResult system =
                service.applyJvmProxy(NetworkProbeService.MODE_SYSTEM, "", 8080);
        assertTrue(system.applied);
        assertTrue(system.summary.contains("useSystemProxies"));
        assertEquals("true", props.getProperty("java.net.useSystemProxies"));
        assertFalse(props.containsKey("https.proxyHost"));
        assertFalse(props.containsKey("http.proxyUser"));
        assertNull(java.net.Authenticator.getDefault());

        NetworkProbeService.JvmProxyApplyResult direct =
                service.applyJvmProxy(NetworkProbeService.MODE_DIRECT, "", 8080);
        assertTrue(direct.applied);
        assertEquals("false", props.getProperty("java.net.useSystemProxies"));
        assertFalse(props.containsKey("http.proxyHost"));
        assertNull(java.net.Authenticator.getDefault());
    }

    @Test
    public void applyJvmProxy_rejectsBadHost() {
        NetworkProbeService service = new NetworkProbeService();
        NetworkProbeService.JvmProxyApplyResult result =
                service.applyJvmProxy(NetworkProbeService.MODE_CUSTOM, "bad host", 8080);
        assertFalse(result.applied);
    }

    @Test
    public void joinUrl_andModeHelpers() {
        assertEquals("https://srv.example/ping", NetworkProbeService.joinUrl("https://srv.example/", "/ping"));
        assertEquals("系統 Proxy", NetworkProbeService.modeLabel("SYSTEM"));
        assertEquals("直連（略過 Proxy）", NetworkProbeService.modeLabel("DIRECT"));
        assertEquals(8080, NetworkProbeService.clampProxyPort(0));
        assertEquals(3128, NetworkProbeService.clampProxyPort(3128));
        assertEquals(NetworkProbeService.MODE_SYSTEM, NetworkProbeService.normalizeProxyMode("nope"));
    }

    @Test
    public void suggestions_coverClosedNetworkCases() {
        NetworkProbeService.ProbeResult dnsFail = new NetworkProbeService.ProbeResult(false, "DNS", "fail", 1);
        NetworkProbeService.ProbeResult tcpFail = new NetworkProbeService.ProbeResult(false, "TCP", "fail", 1);
        NetworkProbeService.ProbeResult httpSsl = new NetworkProbeService.ProbeResult(false, "HTTP", "PKIX path building", 1);
        NetworkProbeService.ProbeResult pingFail = new NetworkProbeService.ProbeResult(false, "Ping", "fail", 1);
        NetworkProbeService.ProbeResult httpOk = new NetworkProbeService.ProbeResult(true, "HTTP", "200", 1);

        Map<String, String> envMap = new LinkedHashMap<>();
        envMap.put("HTTP_PROXY", "（未設定）");
        NetworkProbeService.EnvironmentSnapshot empty = new NetworkProbeService.EnvironmentSnapshot(
                NetworkProbeService.OsFamily.WINDOWS, "Windows", "10", "11", "",
                envMap, Map.of("http.proxyHost", "（未設定）"), "");
        String closed = NetworkProbeService.suggestions(dnsFail, tcpFail, httpSsl, pingFail, empty);
        assertTrue(closed.contains("必須走 Proxy"));
        assertTrue(closed.contains("SSL"));

        String pingOnly = NetworkProbeService.suggestions(
                new NetworkProbeService.ProbeResult(true, "DNS", "ok", 1),
                new NetworkProbeService.ProbeResult(true, "TCP", "ok", 1),
                httpOk, pingFail, empty);
        assertTrue(pingOnly.contains("忽略 ICMP"));
    }

    @Test
    public void runProcess_canTimeout() throws Exception {
        Duration timeout = Duration.ofMillis(200);
        NetworkProbeService.CommandOutput output =
                NetworkProbeService.runProcess(java.util.Arrays.asList("sleep", "2"), timeout);
        assertTrue(output.timedOut);
    }
}
