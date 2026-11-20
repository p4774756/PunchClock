package com.example.service.automation;

/**
 * 打卡流程使用的頁面操作（可替換為假實作以便單元測試）。
 */
public interface CheckInPageDriver {

    void navigate(String targetUrl, int timeoutMs);

    void waitForSelectorVisible(String selector, int timeoutMs);

    void waitForLayoutStable(String selector, int stableMs, int pollMs, int maxMs);

    void runHighlightSequence(String selector, int durationMs, int trackIntervalMs);

    void removeHighlight(String selector);

    void click(String selector, int timeoutMs);

    void waitForTimeout(int timeoutMs);
}