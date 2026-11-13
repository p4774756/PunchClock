package com.example.server.web;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** 執行島嶼探索／賭場純邏輯 node:test（碰撞、互動區、大廳狀態）。沒有 Node 時略過。 */
public class IslandExploreLogicTest {

    private static final String EXPLORE_SCRIPT = "src/test/js/island-explore.test.mjs";
    private static final String CASINO_SCRIPT = "src/test/js/island-casino.test.mjs";

    @Test
    public void exploreLogicPassesNodeTests() throws Exception {
        runNodeTest(EXPLORE_SCRIPT, "未安裝 Node.js，略過島嶼探索邏輯測試");
    }

    @Test
    public void casinoLogicPassesNodeTests() throws Exception {
        runNodeTest(CASINO_SCRIPT, "未安裝 Node.js，略過賭場大廳邏輯測試");
    }

    private void runNodeTest(String scriptPath, String skipMessage) throws Exception {
        File script = new File(scriptPath);
        assertTrue("找不到 " + script.getAbsolutePath(), script.isFile());
        String node = System.getenv().getOrDefault("NODE_BINARY", "node");
        assumeTrue(skipMessage, nodeAvailable(node));

        Process process = new ProcessBuilder(node, "--test", script.getPath())
                .redirectErrorStream(true)
                .start();
        String output = readAll(process.getInputStream());
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue("node --test 逾時\n" + output, finished);
        assertEquals("node --test 失敗：\n" + output, 0, process.exitValue());
    }

    private static boolean nodeAvailable(String node) {
        try {
            Process process = new ProcessBuilder(node, "--version").redirectErrorStream(true).start();
            readAll(process.getInputStream());
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
