package com.example.server.util;

import com.example.DonateLink;
import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.Map;

/** 把贊助連結帶進 HTML 模板。 */
public final class DonatePageTokens {

    private static final Gson GSON = new Gson();

    private DonatePageTokens() {
    }

    public static String apply(String html) {
        String url = DonateLink.resolve();
        boolean configured = DonateLink.isConfigured(url);
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("url", url);
        script.put("configured", configured);
        return html
                .replace("{{DONATE_URL}}", HtmlEscape.escape(url))
                .replace("{{DONATE_CONFIGURED}}", configured ? "true" : "false")
                .replace("{{DONATE_SCRIPT}}", GSON.toJson(script));
    }
}