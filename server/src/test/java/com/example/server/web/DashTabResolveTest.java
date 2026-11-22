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

/** 讓 mvn test 跑到主控台預設分頁的 node:test。 */
public class DashTabResolveTest {

    @Test
    public void nodeTestResolvesDefaultDashTab() throws Exception {
        Path testFile = Paths.get("src/test/js/dash-tab.test.mjs").toAbsolutePath();
        if (!Files.isRegularFile(testFile)) {
            testFile = Paths.get("server/src/test/js/dash-tab.test.mjs").toAbsolutePath();
        }
        assertTrue("找不到 dash-tab.test.mjs：" + testFile, Files.isRegularFile(testFile));

        String node = System.getenv("NODE_BINARY");
        if (node == null || node.trim().isEmpty()) {
            node = "node";
        }
        Process process = new ProcessBuilder(node.trim(), "--test", testFile.toString())
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
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue("node --test 逾時\n" + output, finished);
        assertEquals(output, 0, process.exitValue());
    }
}
