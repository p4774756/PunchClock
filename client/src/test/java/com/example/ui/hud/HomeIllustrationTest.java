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
    public void preferTopWhenVerticalCrop_roomBOnlyFalse() {
        assertFalse(HomeIllustration.ROOM_B.preferTopWhenVerticalCrop());
        assertTrue(HomeIllustration.POOL_C.preferTopWhenVerticalCrop());
        assertTrue(HomeIllustration.CHERRY_PARK_D.preferTopWhenVerticalCrop());
        assertTrue(HomeIllustration.NIGHT_MARKET_E.preferTopWhenVerticalCrop());
        assertTrue(HomeIllustration.OVERTIME_F.preferTopWhenVerticalCrop());
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