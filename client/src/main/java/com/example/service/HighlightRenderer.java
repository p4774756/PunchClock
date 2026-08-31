package com.example.service;

import com.microsoft.playwright.Page;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 在網頁上標示即將點擊的按鈕（漫畫集中線、寬鬆紅框、指向箭頭）。
 * 使用 Playwright locator 綁定元素，避免 querySelector 在 iframe 內找不到目標。
 */
final class HighlightRenderer {

    private static final String RENDER_SCRIPT = loadScript("highlight-render.js");

    private HighlightRenderer() {
    }

    static void waitForLayoutStable(Page page, String selector,
                                    int stableMs, int pollMs, int maxMs) {
        page.locator(selector).first().evaluate("el => new Promise(resolve => {\n"
                + "  const STABLE_MS = " + stableMs + ";\n"
                + "  const POLL_MS = " + pollMs + ";\n"
                + "  const MAX_MS = " + maxMs + ";\n"
                + "  let lastTop = -1, lastLeft = -1, stableSince = 0;\n"
                + "  const start = Date.now();\n"
                + "  const tick = () => {\n"
                + "    if (!el.isConnected) { resolve(false); return; }\n"
                + "    const r = el.getBoundingClientRect();\n"
                + "    const moved = lastTop >= 0\n"
                + "      && (Math.abs(r.top - lastTop) > 1 || Math.abs(r.left - lastLeft) > 1);\n"
                + "    if (!moved && lastTop >= 0) {\n"
                + "      if (stableSince === 0) stableSince = Date.now();\n"
                + "      if (Date.now() - stableSince >= STABLE_MS) { resolve(true); return; }\n"
                + "    } else {\n"
                + "      stableSince = 0;\n"
                + "    }\n"
                + "    lastTop = r.top;\n"
                + "    lastLeft = r.left;\n"
                + "    if (Date.now() - start >= MAX_MS) { resolve(true); return; }\n"
                + "    setTimeout(tick, POLL_MS);\n"
                + "  };\n"
                + "  tick();\n"
                + "})");
        page.locator(selector).first().evaluate(
                "el => el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'auto' })");
    }

    static void runSequence(Page page, String selector, int durationMs, int trackIntervalMs) {
        long highlightEnd = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < highlightEnd) {
            try {
                renderOnTarget(page, selector);
            } catch (RuntimeException ignored) {
                // 元素可能暫時重繪，下一輪再試
            }
            page.waitForTimeout(trackIntervalMs);
        }
    }

    static void remove(Page page, String selector) {
        try {
            page.locator(selector).first().evaluate("el => {\n"
                    + "  const doc = el.ownerDocument;\n"
                    + "  const root = doc.getElementById('punchclock-highlight-root');\n"
                    + "  if (root) root.remove();\n"
                    + "}");
        } catch (RuntimeException ignored) {
            page.evaluate("() => {\n"
                    + "  const root = document.getElementById('punchclock-highlight-root');\n"
                    + "  if (root) root.remove();\n"
                    + "}");
        }
    }

    private static void renderOnTarget(Page page, String selector) {
        page.locator(selector).first().evaluate(RENDER_SCRIPT);
    }

    private static String loadScript(String resourceName) {
        try (InputStream in = HighlightRenderer.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + resourceName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + resourceName, ex);
        }
    }
}
