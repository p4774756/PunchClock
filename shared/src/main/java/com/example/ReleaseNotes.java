package com.example;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 打包進桌面端 jar 的近期提交說明。伺服器依對方目前的版本時間挑出「這次新增」的項目，
 * 更新詢問視窗才看得到改了什麼。
 *
 * <p>檔案一行一筆，格式為 {@code epochSeconds<TAB>主旨}，新的在前。沒有 git 時寫空檔，不讓建置失敗。</p>
 */
public final class ReleaseNotes {

    public static final int MAX_STORED = 40;
    public static final int MAX_SHOWN = 12;
    public static final int MAX_SUBJECT_CHARS = 72;
    public static final String RESOURCE = "release-notes.txt";

    private ReleaseNotes() {}

    public static final class Selection {
        public final List<String> subjects;
        /** 超過顯示上限、沒放進 {@link #subjects} 的筆數。 */
        public final int hidden;

        Selection(List<String> subjects, int hidden) {
            this.subjects = subjects;
            this.hidden = hidden;
        }
    }

    /** 給更新詢問視窗的段落；沒有項目時回空字串。 */
    public static String dialogSection(List<String> subjects, int hidden) {
        if (subjects == null || subjects.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("這次修改：");
        for (String subject : subjects) {
            String line = cleanSubject(subject);
            if (!line.isEmpty()) {
                text.append("\n· ").append(line);
            }
        }
        if (text.indexOf("\n") < 0) {
            return "";
        }
        if (hidden > 0) {
            text.append("\n· …還有 ").append(hidden).append(" 項");
        }
        return text.toString();
    }

    /**
     * 挑出比 {@code clientReleaseTimeIso} 新的提交。時間空或解析不了時，改列檔案裡最新的幾筆。
     */
    public static Selection since(String notes, String clientReleaseTimeIso) {
        List<Entry> entries = parse(notes);
        Instant clientTime = parseInstant(clientReleaseTimeIso);
        List<String> subjects = new ArrayList<>();
        int hidden = 0;
        for (Entry entry : entries) {
            if (clientTime != null && entry.epochSeconds <= clientTime.getEpochSecond()) {
                continue;
            }
            if (subjects.size() >= MAX_SHOWN) {
                hidden++;
                continue;
            }
            subjects.add(entry.subject);
        }
        return new Selection(Collections.unmodifiableList(subjects), hidden);
    }

    public static List<Entry> parse(String notes) {
        if (notes == null || notes.isBlank()) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (String rawLine : notes.split("\\R", -1)) {
            if (entries.size() >= MAX_STORED) {
                break;
            }
            Entry entry = parseLine(rawLine);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** 單行主旨：去掉換行與多餘空白，過長截斷。空字串表示這行不採用。 */
    public static String cleanSubject(String raw) {
        if (raw == null) {
            return "";
        }
        String flat = raw.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        StringBuilder collapsed = new StringBuilder(flat.length());
        boolean space = false;
        for (int i = 0; i < flat.length(); i++) {
            char ch = flat.charAt(i);
            if (ch < ' ' || ch == 127) {
                continue;
            }
            if (ch == ' ') {
                if (collapsed.length() == 0 || space) {
                    continue;
                }
                space = true;
                collapsed.append(' ');
                continue;
            }
            space = false;
            collapsed.append(ch);
        }
        String subject = collapsed.toString().trim();
        if (subject.length() <= MAX_SUBJECT_CHARS) {
            return subject;
        }
        return subject.substring(0, MAX_SUBJECT_CHARS - 1) + "…";
    }

    /**
     * 從 repo 讀最近幾筆會影響桌面端的提交，寫進輸出檔。git 不在或沒有紀錄時寫空檔。
     * 用法：{@code ReleaseNotes <repo-root> <output-file>}
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: ReleaseNotes <repo-root> <output-file>");
            System.exit(2);
        }
        Path out = Path.of(args[1]);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, collect(Path.of(args[0])), StandardCharsets.UTF_8);
    }

    /** 只收 client／shared 原始碼與對應 pom 的提交，和原始碼摘要涵蓋的範圍相同。 */
    static String collect(Path root) {
        if (root == null) {
            return "";
        }
        ProcessBuilder builder = new ProcessBuilder(
                "git", "-c", "safe.directory=*",
                "-C", root.toAbsolutePath().toString(),
                "log", "-n", Integer.toString(MAX_STORED),
                "--no-merges",
                "--pretty=format:%ct%x09%s",
                "--",
                "client/src/main", "shared/src/main", "client/pom.xml", "shared/pom.xml");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) {
                return "";
            }
            return normalize(output);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String normalize(String raw) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : parse(raw)) {
            text.append(entry.epochSeconds).append('\t').append(entry.subject).append('\n');
        }
        return text.toString();
    }

    private static Entry parseLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        int tab = rawLine.indexOf('\t');
        if (tab <= 0) {
            return null;
        }
        long epoch;
        try {
            epoch = Long.parseLong(rawLine.substring(0, tab).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        if (epoch <= 0) {
            return null;
        }
        String subject = cleanSubject(rawLine.substring(tab + 1));
        if (subject.isEmpty()) {
            return null;
        }
        return new Entry(epoch, subject);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static final class Entry {
        final long epochSeconds;
        final String subject;

        Entry(long epochSeconds, String subject) {
            this.epochSeconds = epochSeconds;
            this.subject = subject;
        }
    }
}
