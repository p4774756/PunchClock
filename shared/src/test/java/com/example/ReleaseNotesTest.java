package com.example;

import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseNotesTest {

    @Test
    public void sinceKeepsOnlyCommitsNewerThanTheRunningClient() {
        long after = Instant.parse("2026-10-21T00:00:00Z").getEpochSecond();
        long before = Instant.parse("2026-10-19T00:00:00Z").getEpochSecond();
        String notes = after + "\t聊天視窗只晃一下\n" + before + "\t比較舊的修改\n";

        ReleaseNotes.Selection newer = ReleaseNotes.since(notes, "2026-10-20T00:00:00Z");
        assertEquals(1, newer.subjects.size());
        assertEquals("聊天視窗只晃一下", newer.subjects.get(0));
        assertEquals(0, newer.hidden);

        ReleaseNotes.Selection all = ReleaseNotes.since(notes, "");
        assertEquals(2, all.subjects.size());
        assertEquals("比較舊的修改", all.subjects.get(1));
    }

    @Test
    public void sinceHidesSubjectsPastTheDialogLimit() {
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < ReleaseNotes.MAX_SHOWN + 3; i++) {
            notes.append(2_000_000_000L - i).append("\t項目 ").append(i).append('\n');
        }
        ReleaseNotes.Selection selection = ReleaseNotes.since(notes.toString(), "");
        assertEquals(ReleaseNotes.MAX_SHOWN, selection.subjects.size());
        assertEquals("項目 0", selection.subjects.get(0));
        assertEquals(3, selection.hidden);
    }

    @Test
    public void dialogSectionListsSubjectsAndHiddenCount() {
        String text = ReleaseNotes.dialogSection(
                java.util.List.of("戳一下只彈聊天視窗", "  "), 2);
        assertTrue(text.startsWith("這次修改："));
        assertTrue(text.contains("\n· 戳一下只彈聊天視窗"));
        assertTrue(text.contains("\n· …還有 2 項"));
        assertEquals("", ReleaseNotes.dialogSection(java.util.List.of(), 3));
    }

    @Test
    public void cleanSubjectDropsNewlinesAndTruncates() {
        String messy = "第一行\n第二行\t  尾端";
        assertEquals("第一行 第二行 尾端", ReleaseNotes.cleanSubject(messy));
        assertEquals("", ReleaseNotes.cleanSubject(" \n\t "));
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longLine.append('更');
        }
        String trimmed = ReleaseNotes.cleanSubject(longLine.toString());
        assertEquals(ReleaseNotes.MAX_SUBJECT_CHARS, trimmed.length());
        assertTrue(trimmed.endsWith("…"));
    }

    @Test
    public void collectReadsCommitSubjectsThatTouchTheDesktopSources() throws Exception {
        Assume.assumeTrue(gitAvailable());
        Path base = Path.of("target");
        Files.createDirectories(base);
        Path repo = Files.createTempDirectory(base, "release-notes");
        try {
            git(repo, "init");
            git(repo, "config", "user.email", "notes@example.com");
            git(repo, "config", "user.name", "notes");
            Path desktop = repo.resolve("client/src/main/A.java");
            Files.createDirectories(desktop.getParent());
            Files.writeString(desktop, "class A {}", StandardCharsets.UTF_8);
            git(repo, "add", "client/src/main/A.java");
            git(repo, "-c", "commit.gpgsign=false", "commit", "-m", "聊天視窗只晃一下");

            Path serverOnly = repo.resolve("server/src/main/B.java");
            Files.createDirectories(serverOnly.getParent());
            Files.writeString(serverOnly, "class B {}", StandardCharsets.UTF_8);
            git(repo, "add", "server/src/main/B.java");
            git(repo, "-c", "commit.gpgsign=false", "commit", "-m", "只改伺服器");

            String text = ReleaseNotes.collect(repo);
            assertTrue(text.contains("聊天視窗只晃一下"));
            assertTrue("伺服器自己的提交不該出現在桌面端更新說明", !text.contains("只改伺服器"));
        } finally {
            deleteTree(repo);
        }
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void git(Path repo, String... args) throws Exception {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = repo.toAbsolutePath().toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new AssertionError("git " + String.join(" ", args) + "\n" + output);
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // 測試暫存目錄盡量清掉即可
                }
            });
        }
    }
}
