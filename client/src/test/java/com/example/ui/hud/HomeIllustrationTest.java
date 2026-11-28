package com.example.ui.hud;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HomeIllustrationTest {

    @Test
    public void fromId_defaultsToRoomB() {
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId(null));
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId(""));
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId("unknown"));
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId("b"));
        assertEquals(HomeIllustration.POOL_C, HomeIllustration.fromId("c"));
        assertEquals(HomeIllustration.CHERRY_PARK_D, HomeIllustration.fromId("d"));
        assertEquals(HomeIllustration.NIGHT_MARKET_E, HomeIllustration.fromId("e"));
        assertEquals(HomeIllustration.OVERTIME_F, HomeIllustration.fromId("f"));
    }

    @Test
    public void nextCyclesInOrder() {
        assertEquals(HomeIllustration.POOL_C, HomeIllustration.ROOM_B.next());
        assertEquals(HomeIllustration.CHERRY_PARK_D, HomeIllustration.POOL_C.next());
        assertEquals(HomeIllustration.NIGHT_MARKET_E, HomeIllustration.CHERRY_PARK_D.next());
        assertEquals(HomeIllustration.OVERTIME_F, HomeIllustration.NIGHT_MARKET_E.next());
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.OVERTIME_F.next());
    }

    @Test
    public void fromLabel_mapsAllOptions() {
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromLabel("夜晚房間"));
        assertEquals(HomeIllustration.POOL_C, HomeIllustration.fromLabel("頂樓泳池"));
        assertEquals(HomeIllustration.CHERRY_PARK_D, HomeIllustration.fromLabel("櫻花公園"));
        assertEquals(HomeIllustration.NIGHT_MARKET_E, HomeIllustration.fromLabel("台灣夜市"));
        assertEquals(HomeIllustration.OVERTIME_F, HomeIllustration.fromLabel("加班夜景"));
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromLabel("bad"));
    }

    @Test
    public void resourcePathsFollowId() {
        for (HomeIllustration illustration : HomeIllustration.values()) {
            assertEquals(
                    "/hud/anime/home-" + illustration.id() + ".jpg",
                    illustration.resourcePath());
        }
    }

    @Test
    public void coverFillsIllustrationPanelWithoutTopGaps() throws Exception {
        int[][] shells = {
                {ShellDimensions.DEFAULT_WIDTH, ShellDimensions.DEFAULT_HEIGHT},
                {417, 644},
        };
        for (HomeIllustration illustration : HomeIllustration.values()) {
            try (InputStream in = HomeIllustrationTest.class.getResourceAsStream(illustration.resourcePath())) {
                assertNotNull(in);
                var img = ImageIO.read(in);
                int imgW = img.getWidth();
                int imgH = img.getHeight();
                for (int[] shell : shells) {
                    int panelH = shell[1] - ShellDimensions.TAB_BAR_HEIGHT;
                    int panelW = shell[0];
                    int crop = HomeBubbleAnchor.sourceTopCropPx(illustration, imgH);
                    java.awt.Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                            panelW, panelH, imgW, imgH, crop);
                    assertTrue(draw.width >= panelW);
                    assertTrue(draw.height >= panelH);
                    assertTrue("top gap", draw.y <= 0);
                    assertTrue("bottom gap", draw.y + draw.height >= panelH);
                    assertTrue("left gap", draw.x <= 0);
                    assertTrue("right gap", draw.x + draw.width >= panelW);
                }
            }
        }
    }

    /** 原圖 home-c.jpg 臉部（髮頂）上緣像素 Y，用於驗證 cover 未切頭。 */
    private static final int POOL_C_HEAD_TOP_SRC_Y = 123;

    @Test
    public void poolCKeepsHeadAtReviewSizes() throws Exception {
        int[][] shells = {{380, 520}, {417, 644}};
        try (InputStream in = HomeIllustrationTest.class.getResourceAsStream(HomeIllustration.POOL_C.resourcePath())) {
            assertNotNull(in);
            var img = ImageIO.read(in);
            int imgW = img.getWidth();
            int imgH = img.getHeight();
            assertEquals(0, HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.POOL_C, imgH));
            for (int[] shell : shells) {
                int panelH = shell[1] - ShellDimensions.TAB_BAR_HEIGHT;
                int panelW = shell[0];
                java.awt.Rectangle draw = HomeBubbleAnchor.imageDrawRect(panelW, panelH, imgW, imgH, 0);
                double scale = draw.width / (double) imgW;
                int visibleTopSrcY = (int) Math.round((-draw.y) / scale);
                assertTrue(
                        "c @" + shell[0] + "x" + shell[1] + " topY=" + visibleTopSrcY,
                        visibleTopSrcY < POOL_C_HEAD_TOP_SRC_Y);
            }
        }
    }

    @Test
    public void sceneIllustrationsSkipTopPaddingBand() throws Exception {
        int[][] shells = {{380, 520}, {417, 644}};
        HomeIllustration[] scenes = {
                HomeIllustration.CHERRY_PARK_D,
                HomeIllustration.NIGHT_MARKET_E,
                HomeIllustration.OVERTIME_F,
        };
        assertEquals(0, HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.ROOM_B, 1152));
        assertEquals(0, HomeBubbleAnchor.sourceTopCropPx(HomeIllustration.POOL_C, 1152));
        for (HomeIllustration illustration : scenes) {
            try (InputStream in = HomeIllustrationTest.class.getResourceAsStream(illustration.resourcePath())) {
                var img = ImageIO.read(in);
                int imgW = img.getWidth();
                int imgH = img.getHeight();
                int crop = HomeBubbleAnchor.sourceTopCropPx(illustration, imgH);
                assertEquals(illustration.id() + " crop", 288, crop);
                for (int[] shell : shells) {
                    int panelH = shell[1] - ShellDimensions.TAB_BAR_HEIGHT;
                    int panelW = shell[0];
                    java.awt.Rectangle draw = HomeBubbleAnchor.imageDrawRect(
                            panelW, panelH, imgW, imgH, crop);
                    assertTrue(draw.width >= panelW);
                    assertTrue(draw.height >= panelH);
                    double scale = draw.width / (double) imgW;
                    int visibleTopSrcY = (int) Math.round((-draw.y) / scale) + crop;
                    assertTrue(illustration.id() + " @" + shell[0] + "x" + shell[1], visibleTopSrcY >= crop);
                }
            }
        }
    }

    @Test
    public void allResourceFilesLoad() throws Exception {
        for (HomeIllustration illustration : HomeIllustration.values()) {
            try (InputStream in = HomeIllustrationTest.class.getResourceAsStream(illustration.resourcePath())) {
                assertNotNull(illustration.id() + " resource missing", in);
                assertNotNull(illustration.id(), ImageIO.read(in));
            }
        }
    }

    @Test
    public void resourcesLoadForBothIllustrations() {
        AnimeShell shell = new AnimeShell(null, new javax.swing.JTabbedPane());
        shell.setHomeIllustration(HomeIllustration.ROOM_B);
        assertEquals(HomeIllustration.ROOM_B, shell.homeIllustrationForTests());
        assertNotNull(AnimeShell.renderShellPreview(380, 520, false));
        shell.setHomeIllustration(HomeIllustration.POOL_C);
        assertEquals(HomeIllustration.POOL_C, shell.homeIllustrationForTests());
        assertNotNull(AnimeShell.renderShellPreview(340, 440, false));
        shell.setHomeIllustration(HomeIllustration.CHERRY_PARK_D);
        assertNotNull(AnimeShell.renderShellPreview(380, 520, false));
    }

    @Test
    public void swapIllustrationButtonVisibleOnHome() {
        AnimeShell shell = new AnimeShell(null, new javax.swing.JTabbedPane());
        shell.setSize(380, 520);
        shell.showHome();
        shell.doLayout();
        assertEquals("換圖", shell.swapIllustrationButtonForTests().getText());
        assertTrue(shell.swapIllustrationButtonForTests().isVisible());
        shell.openTab(0);
        assertFalse(shell.swapIllustrationButtonForTests().isVisible());
    }
}