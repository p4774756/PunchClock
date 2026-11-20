package com.example.service.automation;

/**
 * 單次打卡的瀏覽器工作階段；結束後必須 close。
 */
public interface CheckInSession extends AutoCloseable {

    CheckInPageDriver newPage();
}