package com.example.ui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HomeIllustrationTest {

    @Test
    public void fromId_defaultsToRoomB() {
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId(null));
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId("unknown"));
        assertEquals(HomeIllustration.ROOM_B, HomeIllustration.fromId("b"));
        assertEquals(HomeIllustration.POOL_C, HomeIllustration.fromId("c"));
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
    }
}