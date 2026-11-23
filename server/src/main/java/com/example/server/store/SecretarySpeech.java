package com.example.server.store;

import com.example.server.store.DevStatusModels.IssueRow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 從 issue 正文擷取「秘書說：…」口語（公開 API 只用消毒後的一句）。 */
public final class SecretarySpeech {

    private static final Pattern SECRETARY_LINE = Pattern.compile("^秘書說[:：]\\s*(.+)$");

    public static final String ACTIVE_FALLBACK = "正在處理一項工作";
    public static final String COMPLETED_FALLBACK = "完成一項工作";

    private SecretarySpeech() {
    }

    public static String extractFromIssueBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        for (String line : body.split("\\R")) {
            if (line == null) {
                continue;
            }
            Matcher m = SECRETARY_LINE.matcher(line.trim());
            if (m.matches()) {
                return sanitizePhrase(m.group(1));
            }
        }
        return null;
    }

    public static String phraseForActive(IssueRow issue) {
        if (issue == null) {
            return ACTIVE_FALLBACK;
        }
        String fromIssue = sanitizePhrase(issue.secretaryLine);
        if (fromIssue != null && !fromIssue.isEmpty()
                && !ACTIVE_FALLBACK.equals(fromIssue) && !COMPLETED_FALLBACK.equals(fromIssue)) {
            return fromIssue;
        }
        String title = issue.title == null ? "" : issue.title.trim();
        if (!title.isEmpty()) {
            String clean = sanitizePhrase(title);
            if (clean != null && !clean.isEmpty()) {
                return clean;
            }
        }
        return ACTIVE_FALLBACK;
    }

    public static String phraseForCompleted(IssueRow issue) {
        if (issue == null) {
            return COMPLETED_FALLBACK;
        }
        String fromIssue = sanitizePhrase(issue.secretaryLine);
        if (fromIssue != null) {
            if (fromIssue.startsWith("正在")) {
                fromIssue = fromIssue.substring(2).trim();
            }
            if (!fromIssue.isEmpty() && !COMPLETED_FALLBACK.equals(fromIssue) && !ACTIVE_FALLBACK.equals(fromIssue)) {
                return fromIssue;
            }
        }
        String title = issue.title == null ? "" : issue.title.trim();
        if (!title.isEmpty()) {
            return title;
        }
        return COMPLETED_FALLBACK;
    }

    public static String sanitizePhrase(String phrase) {
        if (phrase == null) {
            return null;
        }
        String s = phrase.trim();
        if (s.isEmpty()) {
            return null;
        }
        String lower = s.toLowerCase();
        if (s.contains("/Users/") || s.contains("PunchClock-oki/") || lower.contains("ai/issue-")) {
            return null;
        }
        if (lower.contains("battery") || s.contains("電量") || s.contains("充電")) {
            return null;
        }
        return s;
    }
}