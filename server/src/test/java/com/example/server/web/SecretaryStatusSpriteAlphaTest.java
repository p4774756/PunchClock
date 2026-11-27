package com.example.server.web;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 秘書狀態與拍手慶祝立繪的 alpha 檢查（PIL）。 */
public class SecretaryStatusSpriteAlphaTest {

    @Test
    public void statusSpritesPassPilAlphaChecks() throws Exception {
        Path script = resolveScript();
        assertTrue("找不到 fix_secretary_status_sprites.py：" + script, Files.isRegularFile(script));
        Path repo = script.getParent().getParent();

        String python = System.getenv("PYTHON_BINARY");
        if (python == null || python.trim().isEmpty()) {
            python = "python3";
        }

        Process process = new ProcessBuilder(python.trim(), script.toString(), "--check")
                .directory(repo.toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                captured.write(buf, 0, n);
            }
        }
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue("alpha 檢查逾時\n" + output, finished);
        assertEquals(output, 0, process.exitValue());
    }

    private static Path resolveScript() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path direct = cwd.resolve("scripts/fix_secretary_status_sprites.py");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path fromServer = cwd.resolve("../scripts/fix_secretary_status_sprites.py").normalize();
        if (Files.isRegularFile(fromServer)) {
            return fromServer;
        }
        return direct;
    }
}