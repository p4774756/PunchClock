package com.example.service;

import com.example.service.automation.CheckInBrowserFactory;
import com.example.service.automation.CheckInPageDriver;
import com.example.service.automation.CheckInSession;
import com.microsoft.playwright.TimeoutError;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class AutomationServiceTest {

    @Test
    public void executeCheckIn_success_clicksTargetButton() {
        FakeCheckInPage page = new FakeCheckInPage();
        AutomationService service = automationWithPage(page);

        boolean ok = service.executeCheckIn("https://example.com/punch", "punch-btn", "chromium", null);

        assertTrue(ok);
        assertEquals(1, page.navigateCount);
        assertTrue(page.clickedSelectors.contains("#punch-btn"));
        assertEquals(5000, page.postClickWaitMs);
    }

    @Test
    public void executeCheckIn_loginPageMissingButton_returnsFalse() {
        FakeCheckInPage page = new FakeCheckInPage();
        page.selectorTimeout = new TimeoutError("Timeout 15000ms exceeded");
        AutomationService service = automationWithPage(page);

        boolean ok = service.executeCheckIn("https://example.com/login", "#btn", "chromium", logCapture(new ArrayList<>()));

        assertFalse(ok);
        assertEquals(0, page.clickCount);
    }

    @Test
    public void executeCheckIn_alreadyCheckedIn_noVisibleButton_returnsFalse() {
        FakeCheckInPage page = new FakeCheckInPage();
        page.selectorTimeout = new TimeoutError("Timeout waiting for #btn");
        AutomationService service = automationWithPage(page);

        List<String> logs = new ArrayList<>();
        boolean ok = service.executeCheckIn(
                "https://example.com/done", "#btn", "msedge", logs::add);

        assertFalse(ok);
        assertTrue(logs.stream().anyMatch(m -> m.contains("找不到可見的目標按鈕")));
    }

    @Test
    public void executeCheckIn_emptySelector_returnsFalseWithoutClick() {
        FakeCheckInPage page = new FakeCheckInPage();
        AutomationService service = automationWithPage(page);

        boolean ok = service.executeCheckIn("https://example.com", "  ", "chromium", null);

        assertFalse(ok);
        assertEquals(0, page.clickCount);
    }

    @Test
    public void navigateWithRetry_retriesOnTimeoutThenSucceeds() {
        FakeCheckInPage page = new FakeCheckInPage();
        page.navigateFailures.add(new TimeoutError("timeout on navigate"));
        page.navigateFailures.add(new TimeoutError("timeout again"));
        AutomationService service = automationWithPage(page);

        boolean ok = service.executeCheckIn("https://example.com", "#x", "chromium", null);

        assertTrue(ok);
        assertEquals(3, page.navigateCount);
        assertEquals(2, page.navigateRetryWaitMs.size());
        assertEquals(2, page.navigateRetryWaitMs.stream().filter(ms -> ms == 5000).count());
    }

    @Test
    public void navigateWithRetry_exhaustedRetries_reportsConnectionError() {
        FakeCheckInPage page = new FakeCheckInPage();
        RuntimeException netErr = new RuntimeException("net::ERR_CONNECTION_REFUSED at https://example.com");
        page.navigateFailures.add(netErr);
        page.navigateFailures.add(netErr);
        page.navigateFailures.add(netErr);
        AutomationService service = automationWithPage(page);

        List<String> logs = new ArrayList<>();
        try {
            service.executeCheckIn("https://example.com", "#x", "chromium", logs::add);
            fail("expected RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("無法連線到打卡網站"));
            assertTrue(ex.getMessage().contains("net::ERR_CONNECTION_REFUSED"));
            assertTrue(ex.getMessage().contains("已重試 3 次"));
        }
        assertEquals(3, page.navigateCount);
        assertTrue(logs.stream().anyMatch(m -> m.contains("[警告] 網頁導向失敗")));
    }

    @Test
    public void isRetryableNavigateError_classifiesTimeoutAndNetErrors() {
        assertTrue(AutomationService.isRetryableNavigateError(new TimeoutError("t")));
        assertTrue(AutomationService.isRetryableNavigateError(
                new RuntimeException("net::ERR_NAME_NOT_RESOLVED")));
        assertFalse(AutomationService.isRetryableNavigateError(new RuntimeException("selector not found")));
    }

    @Test
    public void formatUserError_navigateTimeout_usesNavigateTimeoutSeconds() {
        String msg = AutomationService.formatUserError(new TimeoutError("Timeout 45000ms exceeded"));
        assertEquals("[失敗] 網頁載入逾時（45 秒）。廣告多的網站可改選 Edge / Chrome（本機）。", msg);
    }

    @Test
    public void executeCheckIn_success_closesSessionBeforeReturn() {
        FakeCheckInPage page = new FakeCheckInPage();
        CleanupFactory factory = new CleanupFactory(page, null);
        AutomationService service = new AutomationService(factory);

        assertTrue(service.executeCheckIn("https://example.com/punch", "punch-btn", "chromium", null));

        assertEquals(1, factory.sessionCloseCount);
        assertFalse(factory.contextOpen);
        assertFalse(factory.browserOpen);
        assertFalse(factory.playwrightOpen);
    }

    @Test
    public void executeCheckIn_openSessionFailsAfterLaunch_closesDriverAndBrowserWhenCheckInEnds() {
        CleanupFactory factory = new CleanupFactory(
                new FakeCheckInPage(), new IllegalStateException("newContext failed"));
        AutomationService service = new AutomationService(factory);
        List<String> logs = new ArrayList<>();

        try {
            service.executeCheckIn("https://example.com", "#x", "chrome", msg -> {
                logs.add(msg);
                if (msg.contains("執行過程中發生錯誤")) {
                    assertTrue(factory.playwrightOpen);
                    assertTrue(factory.browserOpen);
                }
            });
            fail("expected RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("執行過程中發生錯誤"));
            assertTrue(ex.getMessage().contains("newContext failed"));
        }

        assertEquals(0, factory.sessionCloseCount);
        assertFalse(factory.playwrightOpen);
        assertFalse(factory.browserOpen);
        assertTrue(logs.stream().anyMatch(m -> m.contains("執行過程中發生錯誤")));
    }

    private static Consumer<String> logCapture(List<String> logs) {
        return logs::add;
    }

    private static AutomationService automationWithPage(FakeCheckInPage page) {
        return new AutomationService(new FakeBrowserFactory(page));
    }

    private static final class FakeBrowserFactory implements CheckInBrowserFactory {

        private final FakeCheckInPage page;

        private FakeBrowserFactory(FakeCheckInPage page) {
            this.page = page;
        }

        @Override
        public CheckInSession openSession(String browserChoice, Consumer<String> logger) {
            return new CheckInSession() {
                @Override
                public CheckInPageDriver newPage() {
                    return page;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void shutdown() {
        }
    }

    /**
     * 模擬 openSession 已建立 driver／瀏覽器後才失敗。
     * shutdown 只關 browser 與 Playwright，不關 context（與正式 factory 相同）。
     */
    private static final class CleanupFactory implements CheckInBrowserFactory {

        private final FakeCheckInPage page;
        private final RuntimeException openFailure;
        boolean playwrightOpen;
        boolean browserOpen;
        boolean contextOpen;
        int sessionCloseCount;

        private CleanupFactory(FakeCheckInPage page, RuntimeException openFailure) {
            this.page = page;
            this.openFailure = openFailure;
        }

        @Override
        public CheckInSession openSession(String browserChoice, Consumer<String> logger) {
            playwrightOpen = true;
            browserOpen = true;
            if (openFailure != null) {
                throw openFailure;
            }
            contextOpen = true;
            return new CheckInSession() {
                @Override
                public CheckInPageDriver newPage() {
                    return page;
                }

                @Override
                public void close() {
                    sessionCloseCount++;
                    contextOpen = false;
                    browserOpen = false;
                    playwrightOpen = false;
                }
            };
        }

        @Override
        public void shutdown() {
            browserOpen = false;
            playwrightOpen = false;
        }
    }

    static final class FakeCheckInPage implements CheckInPageDriver {

        int navigateCount;
        int clickCount;
        int postClickWaitMs;
        final Queue<RuntimeException> navigateFailures = new LinkedList<>();
        final List<Integer> navigateRetryWaitMs = new ArrayList<>();
        final List<String> clickedSelectors = new ArrayList<>();
        RuntimeException selectorTimeout;

        @Override
        public void navigate(String targetUrl, int timeoutMs) {
            navigateCount++;
            if (!navigateFailures.isEmpty()) {
                throw navigateFailures.remove();
            }
        }

        @Override
        public void waitForSelectorVisible(String selector, int timeoutMs) {
            if (selectorTimeout != null) {
                throw selectorTimeout;
            }
        }

        @Override
        public void waitForLayoutStable(String selector, int stableMs, int pollMs, int maxMs) {
        }

        @Override
        public void runHighlightSequence(String selector, int durationMs, int trackIntervalMs) {
        }

        @Override
        public void removeHighlight(String selector) {
        }

        @Override
        public void click(String selector, int timeoutMs) {
            clickCount++;
            clickedSelectors.add(selector);
        }

        @Override
        public void waitForTimeout(int timeoutMs) {
            if (clickCount > 0) {
                postClickWaitMs = timeoutMs;
            } else {
                navigateRetryWaitMs.add(timeoutMs);
            }
        }
    }
}