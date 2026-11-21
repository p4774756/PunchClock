package com.example.ui.hud;

import org.junit.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;

public class HomePoolPhaseTest {

    @Test
    public void mapsDayDuskAndNight() {
        assertEquals(HomePoolPhase.Phase.NIGHT, HomePoolPhase.phase(LocalTime.of(5, 59)));
        assertEquals(HomePoolPhase.Phase.DAY, HomePoolPhase.phase(LocalTime.of(6, 0)));
        assertEquals(HomePoolPhase.Phase.DAY, HomePoolPhase.phase(LocalTime.of(16, 59)));
        assertEquals(HomePoolPhase.Phase.DUSK, HomePoolPhase.phase(LocalTime.of(17, 0)));
        assertEquals(HomePoolPhase.Phase.DUSK, HomePoolPhase.phase(LocalTime.of(18, 59)));
        assertEquals(HomePoolPhase.Phase.NIGHT, HomePoolPhase.phase(LocalTime.of(19, 0)));
        assertEquals(HomePoolPhase.Phase.NIGHT, HomePoolPhase.phase(LocalTime.of(22, 0)));
    }

    @Test
    public void resourcePathsMatchPhase() {
        assertEquals("/hud/anime/bg-day.jpg", HomePoolPhase.resourcePath(HomePoolPhase.Phase.DAY));
        assertEquals("/hud/anime/bg-dusk.jpg", HomePoolPhase.resourcePath(HomePoolPhase.Phase.DUSK));
        assertEquals("/hud/anime/bg-night.jpg", HomePoolPhase.resourcePath(HomePoolPhase.Phase.NIGHT));
    }

    @Test
    public void shellUsesInjectedClock() {
        ZoneId zone = ZoneId.systemDefault();
        Clock clock = Clock.fixed(LocalDate.of(2026, 1, 1).atTime(17, 30).atZone(zone).toInstant(), zone);
        AnimeShell shell = new AnimeShell(null, new javax.swing.JTabbedPane());
        shell.setClockForTests(clock);
        assertEquals(HomePoolPhase.Phase.DUSK, shell.currentPhase());
    }
}