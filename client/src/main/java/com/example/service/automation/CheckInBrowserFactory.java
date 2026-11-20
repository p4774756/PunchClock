package com.example.service.automation;

import java.util.function.Consumer;

/**
 * 建立可替換的打卡瀏覽器工作階段（正式環境用 Playwright）。
 */
public interface CheckInBrowserFactory {

    CheckInSession openSession(String browserChoice, Consumer<String> logger);

    /** 應用程式關閉時釋放殘留資源。 */
    void shutdown();
}