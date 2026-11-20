package com.example.server.web;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** 執行 server/src/test/js 底下全部 *.test.mjs（node:test）。沒有 Node 時略過。 */
@RunWith(Parameterized.class)
public class IslandExploreLogicTest {

    private static final String JS_TEST_DIR = "src/test/js";

    @Parameterized.Parameter
    public String scriptPath;

    @Parameterized.Parameters(name = "{0}")
    public static List<String> discoverNodeTestScripts() {
        File dir = new File(JS_TEST_DIR);
        assertTrue("找不到 " + dir.getAbsolutePath(), dir.isDirectory());
        File[] files = dir.listFiles((d, name) -> name.endsWith(".test.mjs"));
        assertTrue(JS_TEST_DIR + " 底下沒有 *.test.mjs", files != null && files.length > 0);
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<String> paths = new ArrayList<>(files.length);
        for (File file : files) {
            paths.add(JS_TEST_DIR + "/" + file.getName());
        }
        return paths;
    }

    @Test
    public void passesNodeTests() throws Exception {
        runNodeTest(scriptPath, "未安裝 Node.js，略過 " + scriptPath);
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
        assertTrue("node --test 逾時：" + scriptPath + "\n" + output, finished);
        assertEquals("node --test 失敗：" + scriptPath + "\n" + output, 0, process.exitValue());
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