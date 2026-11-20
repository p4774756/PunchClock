package com.example.service;

import com.example.service.automation.CheckInPageDriver;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

final class PlaywrightPageDriver implements CheckInPageDriver {

    private final Page page;

    PlaywrightPageDriver(Page page) {
        this.page = page;
    }

    @Override
    public void navigate(String targetUrl, int timeoutMs) {
        page.navigate(targetUrl, new Page.NavigateOptions()
                .setTimeout(timeoutMs)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    @Override
    public void waitForSelectorVisible(String selector, int timeoutMs) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeoutMs));
    }

    @Override
    public void waitForLayoutStable(String selector, int stableMs, int pollMs, int maxMs) {
        HighlightRenderer.waitForLayoutStable(page, selector, stableMs, pollMs, maxMs);
    }

    @Override
    public void runHighlightSequence(String selector, int durationMs, int trackIntervalMs) {
        HighlightRenderer.runSequence(page, selector, durationMs, trackIntervalMs);
    }

    @Override
    public void removeHighlight(String selector) {
        HighlightRenderer.remove(page, selector);
    }

    @Override
    public void click(String selector, int timeoutMs) {
        page.click(selector, new Page.ClickOptions().setTimeout(timeoutMs));
    }

    @Override
    public void waitForTimeout(int timeoutMs) {
        page.waitForTimeout(timeoutMs);
    }
}