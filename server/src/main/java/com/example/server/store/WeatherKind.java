package com.example.server.store;

/** Open-Meteo WMO weather_code → 探索島天氣 kind。 */
public final class WeatherKind {

    public static final String CLEAR = "clear";
    public static final String CLOUDY = "cloudy";
    public static final String RAIN = "rain";
    public static final String STORM = "storm";
    public static final String FOG = "fog";

    private WeatherKind() {
    }

    public static String fromOpenMeteo(int weatherCode, double rainMm, double precipitationMm,
                                     double gustKmh) {
        double hourlyRain = Math.max(rainMm, precipitationMm);
        if (gustKmh >= 62.0 || hourlyRain >= 15.0) {
            return STORM;
        }
        if (weatherCode == 45 || weatherCode == 48) {
            return FOG;
        }
        if (weatherCode >= 95 && weatherCode <= 99) {
            return STORM;
        }
        if ((weatherCode >= 51 && weatherCode <= 67)
                || (weatherCode >= 80 && weatherCode <= 82)) {
            return RAIN;
        }
        if (weatherCode >= 2 && weatherCode <= 3) {
            return CLOUDY;
        }
        if (weatherCode == 0 || weatherCode == 1) {
            return CLEAR;
        }
        if (weatherCode >= 61 && weatherCode <= 67) {
            return RAIN;
        }
        return CLOUDY;
    }

    public static boolean isValidKind(String kind) {
        return CLEAR.equals(kind) || CLOUDY.equals(kind) || RAIN.equals(kind)
                || STORM.equals(kind) || FOG.equals(kind);
    }
}