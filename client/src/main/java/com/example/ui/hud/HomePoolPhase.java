package com.example.ui.hud;

import java.time.Clock;
import java.time.LocalTime;

/** 首頁泳池背景時段（本機時區）。 */
public final class HomePoolPhase {

    public enum Phase {
        DAY,
        DUSK,
        NIGHT
    }

    private HomePoolPhase() {
    }

    public static Phase phase(LocalTime time) {
        if (time == null) {
            return Phase.NIGHT;
        }
        int minuteOfDay = time.toSecondOfDay() / 60;
        int dayStart = 6 * 60;
        int duskStart = 17 * 60;
        int nightStart = 19 * 60;
        if (minuteOfDay >= dayStart && minuteOfDay < duskStart) {
            return Phase.DAY;
        }
        if (minuteOfDay >= duskStart && minuteOfDay < nightStart) {
            return Phase.DUSK;
        }
        return Phase.NIGHT;
    }

    public static Phase phase(Clock clock) {
        if (clock == null) {
            return phase(LocalTime.now());
        }
        return phase(LocalTime.now(clock));
    }

    public static String resourcePath(Phase phase) {
        if (phase == Phase.DAY) {
            return "/hud/anime/bg-day.jpg";
        }
        if (phase == Phase.DUSK) {
            return "/hud/anime/bg-dusk.jpg";
        }
        return "/hud/anime/bg-night.jpg";
    }
}