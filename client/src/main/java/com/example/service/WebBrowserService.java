package com.example.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 簡易瀏覽：用 Playwright 內建 Chromium 開一般瀏覽器視窗（有網址列、可開分頁）。
 * <p>
 * 不用本機 Edge／Chrome，公司對它們下的 Proxy／網址原則管不到內建 Chromium；
 * 可選 {@code --no-proxy-server} 直連，走跟心跳相同的路線。
 * Playwright 物件不可跨執行緒，所有操作都在專屬 worker 執行緒上。
 */
public class WebBrowserService {

    private static final int NAVIGATE_TIMEOUT_MS = 45_000;
    private static final String PROFILE_DIR = "browser-profile";

    private final Path profileDir;
    private final BlockingQueue<String> pendingUrls = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean stopRequested;
    private Thread worker;

    public WebBrowserService() {
        this(Paths.get(System.getProperty("user.home", "."), ".punchclock", PROFILE_DIR));
    }

    WebBrowserService(Path profileDir) {
        this.profileDir = profileDir;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 瀏覽器已開啟時在新分頁開網址；否則啟動瀏覽器。
     *
     * @param direct            true 時略過系統 Proxy 直連
     * @param ignoreHttpsErrors true 時忽略憑證錯誤（公司 SSL 攔截）
     * @param onStateChanged    啟動／關閉時回呼（在 worker 執行緒，UI 需自行切回 EDT）
     */
    public synchronized void open(String rawUrl, boolean direct, boolean ignoreHttpsErrors,
                                  Consumer<String> logger, Runnable onStateChanged) {
        String url = normalizeUrl(rawUrl);
        if (url.isEmpty()) {
            log(logger, "[瀏覽] 網址無效（僅支援 http／https）");
            return;
        }
        if (running.get()) {
            pendingUrls.add(url);
            return;
        }
        pendingUrls.clear();
        stopRequested = false;
        running.set(true);
        notifyState(onStateChanged);
        worker = new Thread(() -> runBrowser(url, direct, ignoreHttpsErrors, logger, onStateChanged),
                "web-browser");
        worker.setDaemon(true);
        worker.start();
    }

    public void close() {
        stopRequested = true;
    }

    /** 程式結束時呼叫：要求關閉並等待瀏覽器收掉。 */
    public void shutdown() {
        close();
        Thread current = worker;
        if (current != null) {
            try {
                current.join(5_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runBrowser(String url, boolean direct, boolean ignoreHttpsErrors,
                            Consumer<String> logger, Runnable onStateChanged) {
        try (Playwright playwright = Playwright.create()) {
            log(logger, "[瀏覽] 啟動內建 Chromium"
                    + (direct ? "（直連，略過系統 Proxy）" : "（系統 Proxy）")
                    + (ignoreHttpsErrors ? " [忽略憑證錯誤]" : ""));
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(false)
                    .setViewportSize(null)
                    .setIgnoreHTTPSErrors(ignoreHttpsErrors);
            if (direct) {
                options.setArgs(Collections.singletonList("--no-proxy-server"));
            }
            BrowserContext context = playwright.chromium().launchPersistentContext(profileDir, options);
            AtomicBoolean closed = new AtomicBoolean(false);
            context.onClose(c -> closed.set(true));

            Page first = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            navigate(first, url, logger);

            // macOS 關掉最後一個視窗時 Chromium 不會結束，所以沒有分頁也視為關閉
            while (!closed.get() && !stopRequested && !context.pages().isEmpty()) {
                context.waitForCondition(
                        () -> closed.get() || stopRequested || !pendingUrls.isEmpty() || context.pages().isEmpty(),
                        new BrowserContext.WaitForConditionOptions().setTimeout(0));
                String next;
                while (!closed.get() && (next = pendingUrls.poll()) != null) {
                    navigate(context.newPage(), next, logger);
                }
            }
            if (!closed.get()) {
                context.close();
            }
            log(logger, "[瀏覽] 瀏覽器已關閉");
        } catch (Exception ex) {
            log(logger, "[瀏覽] 失敗：" + describeError(ex));
        } finally {
            pendingUrls.clear();
            running.set(false);
            notifyState(onStateChanged);
        }
    }

    private static void navigate(Page page, String url, Consumer<String> logger) {
        log(logger, "[瀏覽] 開啟：" + url);
        try {
            page.bringToFront();
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(NAVIGATE_TIMEOUT_MS)
                    .setWaitUntil(WaitUntilState.COMMIT));
        } catch (PlaywrightException ex) {
            log(logger, "[瀏覽] 載入失敗：" + describeError(ex));
        }
    }

    /** 補上 https://；只接受 http／https，其餘回空字串。 */
    public static String normalizeUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.contains(" ")) {
            return "";
        }
        int scheme = trimmed.indexOf("://");
        if (scheme < 0) {
            return "https://" + trimmed;
        }
        String prefix = trimmed.substring(0, scheme).toLowerCase(Locale.ROOT);
        if (!prefix.equals("http") && !prefix.equals("https")) {
            return "";
        }
        return trimmed.length() > scheme + 3 ? trimmed : "";
    }

    static String describeError(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        if (message.contains("Executable doesn't exist")) {
            return "找不到 Playwright 內建 Chromium（首次執行需下載瀏覽器，公司網路可能擋下載）";
        }
        if (message.contains("ProcessSingleton") || message.contains("profile appears to be in use")) {
            return "瀏覽器設定檔被占用，請先關閉其他由本程式開啟的瀏覽器視窗";
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline).trim() : message.trim();
    }

    private static void notifyState(Runnable onStateChanged) {
        if (onStateChanged != null) {
            try {
                onStateChanged.run();
            } catch (Exception ignored) {
            }
        }
    }

    private static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
