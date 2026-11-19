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

/** 執行島嶼探索／賭場／milu／塗鴉牆／晝夜／動畫迴圈／在線 node:test。沒有 Node 時略過。 */
public class IslandExploreLogicTest {

    private static final String EXPLORE_SCRIPT = "src/test/js/island-explore.test.mjs";
    private static final String LAYOUT_SCRIPT = "src/test/js/island-layout.test.mjs";
    private static final String CASINO_SCRIPT = "src/test/js/island-casino.test.mjs";
    private static final String BLACKJACK_SCRIPT = "src/test/js/island-blackjack.test.mjs";
    private static final String LOBBY_DOM_SCRIPT = "src/test/js/island-lobby-dom.test.mjs";
    private static final String MILU_SCRIPT = "src/test/js/island-milu.test.mjs";
    private static final String GRAFFITI_SCRIPT = "src/test/js/island-graffiti.test.mjs";
    private static final String ROADMAP_SCRIPT = "src/test/js/island-roadmap.test.mjs";
    private static final String INTERIOR_SCRIPT = "src/test/js/island-interior.test.mjs";
    private static final String LOOP_SCRIPT = "src/test/js/island-loop.test.mjs";
    private static final String DAYNIGHT_SCRIPT = "src/test/js/island-daynight.test.mjs";
    private static final String SKY_SCRIPT = "src/test/js/island-sky.test.mjs";
    private static final String CAMERA_SCRIPT = "src/test/js/island-camera.test.mjs";
    private static final String PRESENCE_SCRIPT = "src/test/js/island-presence.test.mjs";
    private static final String CHAT_SCRIPT = "src/test/js/island-chat.test.mjs";
    private static final String LIFECYCLE_SCRIPT = "src/test/js/island-lifecycle.test.mjs";
    private static final String EXPLORE_INPUT_SCRIPT = "src/test/js/island-explore-input.test.mjs";
    private static final String PINCH_SCRIPT = "src/test/js/island-pinch.test.mjs";
    private static final String QUALITY_SCRIPT = "src/test/js/island-quality.test.mjs";
    private static final String BOAT_SCRIPT = "src/test/js/island-boat.test.mjs";
    private static final String STEER_SCRIPT = "src/test/js/island-steer.test.mjs";
    private static final String TCM_VISIT_SCRIPT = "src/test/js/island-tcm-visit.test.mjs";
    private static final String TCM_FIGURE_SCRIPT = "src/test/js/island-tcm-figure.test.mjs";
    private static final String LIFE_SCRIPT = "src/test/js/island-life.test.mjs";
    private static final String MOBILE_CHROME_SCRIPT = "src/test/js/island-mobile-chrome.test.mjs";

    @Test
    public void exploreLogicPassesNodeTests() throws Exception {
        runNodeTest(EXPLORE_SCRIPT, "未安裝 Node.js，略過島嶼探索邏輯測試");
    }

    @Test
    public void layoutLogicPassesNodeTests() throws Exception {
        runNodeTest(LAYOUT_SCRIPT, "未安裝 Node.js，略過群島清單邏輯測試");
    }

    @Test
    public void casinoLogicPassesNodeTests() throws Exception {
        runNodeTest(CASINO_SCRIPT, "未安裝 Node.js，略過賭場大廳邏輯測試");
    }

    @Test
    public void blackjackLogicPassesNodeTests() throws Exception {
        runNodeTest(BLACKJACK_SCRIPT, "未安裝 Node.js，略過網頁 21 點邏輯測試");
    }

    @Test
    public void casinoLobbyDomIntegrationPassesNodeTests() throws Exception {
        runNodeTest(LOBBY_DOM_SCRIPT, "未安裝 Node.js，略過賭場大廳 DOM 整合測試");
    }

    @Test
    public void miluLogicPassesNodeTests() throws Exception {
        runNodeTest(MILU_SCRIPT, "未安裝 Node.js，略過 milu 看板邏輯測試");
    }

    @Test
    public void graffitiLogicPassesNodeTests() throws Exception {
        runNodeTest(GRAFFITI_SCRIPT, "未安裝 Node.js，略過塗鴉牆邏輯測試");
    }

    @Test
    public void roadmapLogicPassesNodeTests() throws Exception {
        runNodeTest(ROADMAP_SCRIPT, "未安裝 Node.js，略過施工進度邏輯測試");
    }

    @Test
    public void interiorLogicPassesNodeTests() throws Exception {
        runNodeTest(INTERIOR_SCRIPT, "未安裝 Node.js，略過室內進出邏輯測試");
    }

    @Test
    public void updateLoopPassesNodeTests() throws Exception {
        runNodeTest(LOOP_SCRIPT, "未安裝 Node.js，略過動畫迴圈測試");
    }

    @Test
    public void dayNightPassesNodeTests() throws Exception {
        runNodeTest(DAYNIGHT_SCRIPT, "未安裝 Node.js，略過晝夜混合測試");
    }

    @Test
    public void skyPassesNodeTests() throws Exception {
        runNodeTest(SKY_SCRIPT, "未安裝 Node.js，略過日月星辰測試");
    }

    @Test
    public void cameraPassesNodeTests() throws Exception {
        runNodeTest(CAMERA_SCRIPT, "未安裝 Node.js，略過鏡頭控制測試");
    }

    @Test
    public void presencePassesNodeTests() throws Exception {
        runNodeTest(PRESENCE_SCRIPT, "未安裝 Node.js，略過島上在線邏輯測試");
    }

    @Test
    public void chatPassesNodeTests() throws Exception {
        runNodeTest(CHAT_SCRIPT, "未安裝 Node.js，略過島上聊天邏輯測試");
    }

    @Test
    public void lifecyclePassesNodeTests() throws Exception {
        runNodeTest(LIFECYCLE_SCRIPT, "未安裝 Node.js，略過生命週期匯流排測試");
    }

    @Test
    public void exploreInputPassesNodeTests() throws Exception {
        runNodeTest(EXPLORE_INPUT_SCRIPT, "未安裝 Node.js，略過探索輸入邏輯測試");
    }

    @Test
    public void pinchLogicPassesNodeTests() throws Exception {
        runNodeTest(PINCH_SCRIPT, "未安裝 Node.js，略過兩指捏合邏輯測試");
    }

    @Test
    public void qualityPassesNodeTests() throws Exception {
        runNodeTest(QUALITY_SCRIPT, "未安裝 Node.js，略過自適應畫質邏輯測試");
    }

    @Test
    public void boatPassesNodeTests() throws Exception {
        runNodeTest(BOAT_SCRIPT, "未安裝 Node.js，略過搭船邏輯測試");
    }

    @Test
    public void steerPassesNodeTests() throws Exception {
        runNodeTest(STEER_SCRIPT, "未安裝 Node.js，略過掌舵邏輯測試");
    }

    @Test
    public void tcmVisitPassesNodeTests() throws Exception {
        runNodeTest(TCM_VISIT_SCRIPT, "未安裝 Node.js，略過推拿館研習室往返測試");
    }

    @Test
    public void tcmFigurePassesNodeTests() throws Exception {
        runNodeTest(TCM_FIGURE_SCRIPT, "未安裝 Node.js，略過銅人穴位邏輯測試");
    }

    @Test
    public void islandLifePassesNodeTests() throws Exception {
        runNodeTest(LIFE_SCRIPT, "未安裝 Node.js，略過海空生物邏輯測試");
    }

    @Test
    public void mobileChromePassesNodeTests() throws Exception {
        runNodeTest(MOBILE_CHROME_SCRIPT, "未安裝 Node.js，略過主島手機 HUD 測試");
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
