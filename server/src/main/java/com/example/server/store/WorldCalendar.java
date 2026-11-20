package com.example.server.store;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Asia/Taipei 節氣與節日（2026～2030，與前端 island-world-data.js 相同）。 */
public final class WorldCalendar {

    public static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Map<String, String> SOLAR_TERMS = WorldCalendarTables.SOLAR_BY_DATE;
    private static final Map<String, String> FESTIVAL_BY_DATE = WorldCalendarTables.FESTIVAL_BY_DATE;

    private WorldCalendar() {
    }

    public static LocalDate todayTaipei(long epochMillis) {
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), TAIPEI).toLocalDate();
    }

    public static String formatDate(LocalDate date) {
        return date.format(ISO_DATE);
    }

    public static String solarTermOn(LocalDate date) {
        return SOLAR_TERMS.get(date.format(ISO_DATE));
    }

    /** 當天節氣名，或 null。 */
    public static String solarTermForDate(String isoDate) {
        return SOLAR_TERMS.get(isoDate);
    }

    /** 節日 id（與 ?festival= 一致）或 null。 */
    public static String festivalOn(LocalDate date) {
        return FESTIVAL_BY_DATE.get(date.format(ISO_DATE));
    }

    public static String festivalForDate(String isoDate) {
        return FESTIVAL_BY_DATE.get(isoDate);
    }

    /** 當天或最近剛過（不晚於指定日）的節氣；查無資料回 null。 */
    public static RecentSolarTerm recentSolarTermForDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        String bestDate = null;
        for (String key : SOLAR_TERMS.keySet()) {
            if (key.compareTo(isoDate) > 0) {
                continue;
            }
            if (bestDate == null || key.compareTo(bestDate) > 0) {
                bestDate = key;
            }
        }
        if (bestDate == null) {
            return null;
        }
        return new RecentSolarTerm(SOLAR_TERMS.get(bestDate), bestDate, bestDate.equals(isoDate));
    }

    public static final class RecentSolarTerm {
        public final String name;
        public final String date;
        public final boolean isToday;

        RecentSolarTerm(String name, String date, boolean isToday) {
            this.name = name;
            this.date = date;
            this.isToday = isToday;
        }
    }

    public static final class CalendarView {
        public final String date;
        public final String solarTerm;
        public final String festival;
        /** 當天或最近剛過的節氣名，可能與 solarTerm 不同。 */
        public final String recentSolarTerm;
        public final String recentSolarTermDate;
        public final boolean solarTermIsToday;

        CalendarView(String date, String solarTerm, String festival, RecentSolarTerm recent) {
            this.date = date;
            this.solarTerm = solarTerm;
            this.festival = festival;
            this.recentSolarTerm = recent == null ? null : recent.name;
            this.recentSolarTermDate = recent == null ? null : recent.date;
            this.solarTermIsToday = recent != null && recent.isToday;
        }
    }

    public static CalendarView calendarFor(long epochMillis) {
        LocalDate d = todayTaipei(epochMillis);
        String iso = formatDate(d);
        return new CalendarView(iso, solarTermOn(d), festivalOn(d), recentSolarTermForDate(iso));
    }

    /** 測試：所有節氣日期鍵。 */
    public static List<String> solarTermDatesForTests() {
        return List.copyOf(SOLAR_TERMS.keySet());
    }
}