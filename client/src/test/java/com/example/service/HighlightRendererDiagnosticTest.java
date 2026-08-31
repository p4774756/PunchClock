package com.example.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 手動診斷：mvn -pl client test -Dtest=HighlightRendererDiagnosticTest
 */
public class HighlightRendererDiagnosticTest {

    @Test
    public void diagnoseMsnFinanceHighlight() throws Exception {
        Path screenshot = Path.of("target/highlight-diagnostic.png");
        Files.createDirectories(screenshot.getParent());

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate("https://www.msn.com/zh-tw",
                    new Page.NavigateOptions()
                            .setTimeout(45_000)
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            String selector = "#finance";
            page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(15_000));

            Map<String, Object> before = probe(page, selector);
            System.out.println("BEFORE highlight: " + before);

            String renderError = null;
            try {
                HighlightRenderer.runSequence(page, selector, 2_000, 120);
            } catch (Exception ex) {
                renderError = ex.getMessage();
            }

            Map<String, Object> during = probe(page, selector);
            System.out.println("DURING highlight: " + during);
            if (renderError != null) {
                System.out.println("runSequence error: " + renderError);
            }

            page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(false));
            System.out.println("Screenshot: " + screenshot.toAbsolutePath());

            HighlightRenderer.remove(page, selector);
            browser.close();

            Object rootExists = during.get("rootExists");
            Object lineCount = during.get("lineCount");
            if (!Boolean.TRUE.equals(rootExists)) {
                throw new AssertionError("Highlight root not found. Probe: " + during);
            }
            if (lineCount instanceof Number && ((Number) lineCount).intValue() < 90) {
                throw new AssertionError("Expected ~96 manga lines, got: " + lineCount + ". Probe: " + during);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> probe(Page page, String selector) {
        try {
            Object result = page.locator(selector).first().evaluate("el => {\n"
                    + "  const doc = el.ownerDocument;\n"
                    + "  const root = doc.getElementById('punchclock-highlight-root');\n"
                    + "  const rect = el.getBoundingClientRect();\n"
                    + "  const style = doc.getElementById('punchclock-highlight-style');\n"
                    + "  const lines = root ? root.querySelectorAll('#punchclock-manga-focus line').length : 0;\n"
                    + "  return {\n"
                    + "    tag: el.tagName,\n"
                    + "    id: el.id,\n"
                    + "    rectW: rect.width,\n"
                    + "    rectH: rect.height,\n"
                    + "    inIframe: doc !== document,\n"
                    + "    rootExists: !!root,\n"
                    + "    styleExists: !!style,\n"
                    + "    lineCount: lines,\n"
                    + "    rootParent: root && root.parentElement ? root.parentElement.tagName : null\n"
                    + "  };\n"
                    + "}");
            return (Map<String, Object>) result;
        } catch (RuntimeException ex) {
            return Map.of("probeError", ex.getMessage());
        }
    }
}
