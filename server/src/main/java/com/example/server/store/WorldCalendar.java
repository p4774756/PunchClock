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

    public static final class CalendarView {
        public final String date;
        public final String solarTerm;
        public final String festival;

        CalendarView(String date, String solarTerm, String festival) {
            this.date = date;
            this.solarTerm = solarTerm;
            this.festival = festival;
        }
    }

    public static CalendarView calendarFor(long epochMillis) {
        LocalDate d = todayTaipei(epochMillis);
        String iso = formatDate(d);
        return new CalendarView(iso, solarTermOn(d), festivalOn(d));
    }

    /** 測試：所有節氣日期鍵。 */
    public static List<String> solarTermDatesForTests() {
        return List.copyOf(SOLAR_TERMS.keySet());
    }
}