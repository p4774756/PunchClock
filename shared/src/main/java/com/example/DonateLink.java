package com.example;

import java.net.URI;
import java.util.Locale;
import java.util.function.Function;

/** 贊助頁網址：桌面端與網站共用同一來源。 */
public final class DonateLink {

  // TODO: 註冊 Portaly / Ko-fi 後換成真的贊助頁
  public static final String PLACEHOLDER_URL = "https://example.com/donate";

  public static final int MAX_URL_LENGTH = 300;

  private static final String PROP_KEY = "punchclock.donateUrl";
  private static final String ENV_KEY = "PUNCHCLOCK_DONATE_URL";

  private static volatile Function<String, String> envLookup = System::getenv;

  private DonateLink() {}

  /** 測試用：注入環境變數讀取。傳 null 則還原為 {@link System#getenv}。 */
  public static void setEnvLookupForTests(Function<String, String> lookup) {
    envLookup = lookup == null ? System::getenv : lookup;
  }

  public static String resolve() {
    String fromProp = System.getProperty(PROP_KEY, "").trim();
    if (!fromProp.isEmpty()) {
      return acceptOrPlaceholder(fromProp);
    }
    Function<String, String> lookup = envLookup;
    String fromEnv = lookup == null ? null : lookup.apply(ENV_KEY);
    if (fromEnv != null && !fromEnv.trim().isEmpty()) {
      return acceptOrPlaceholder(fromEnv.trim());
    }
    return PLACEHOLDER_URL;
  }

  /** 佔位值或 example.com 網域視為尚未設定。 */
  public static boolean isConfigured(String url) {
    if (url == null || url.isEmpty()) {
      return false;
    }
    if (PLACEHOLDER_URL.equals(url)) {
      return false;
    }
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      if (host == null) {
        return false;
      }
      host = host.toLowerCase(Locale.ROOT);
      return !host.equals("example.com") && !host.endsWith(".example.com");
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  static String acceptOrPlaceholder(String raw) {
    return isValidHttpsUrl(raw) ? raw : PLACEHOLDER_URL;
  }

  static boolean isValidHttpsUrl(String raw) {
    if (raw == null || raw.isEmpty() || raw.length() > MAX_URL_LENGTH) {
      return false;
    }
    if (!raw.regionMatches(true, 0, "https://", 0, 8)) {
      return false;
    }
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (Character.isWhitespace(c) || c < 32 || c == 127) {
        return false;
      }
      if (c == '"' || c == '\'' || c == '<' || c == '>') {
        return false;
      }
    }
    return true;
  }
}