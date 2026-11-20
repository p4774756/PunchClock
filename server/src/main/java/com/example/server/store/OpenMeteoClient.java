package com.example.server.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 台北即時天氣（Open-Meteo，免金鑰）。 */
public final class OpenMeteoClient implements WorldService.WeatherFetcher {

    public static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=25.04&longitude=121.53"
                    + "&current=temperature_2m,relative_humidity_2m,precipitation,rain,weather_code,"
                    + "cloud_cover,wind_speed_10m,wind_gusts_10m,is_day&timezone=Asia%2FTaipei";

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http;

    public OpenMeteoClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    OpenMeteoClient(HttpClient http) {
        this.http = http;
    }

    @Override
    public WorldService.WeatherSnapshot fetch() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(FORECAST_URL))
                .timeout(READ_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "PunchClock")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("open-meteo interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("open-meteo http " + response.statusCode());
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("current") || !root.get("current").isJsonObject()) {
            throw new IOException("open-meteo current");
        }
        JsonObject cur = root.getAsJsonObject("current");
        double temp = cur.get("temperature_2m").getAsDouble();
        int humidity = cur.get("relative_humidity_2m").getAsInt();
        double precip = num(cur, "precipitation", 0);
        double rain = num(cur, "rain", precip);
        int code = cur.get("weather_code").getAsInt();
        double wind = num(cur, "wind_speed_10m", 0);
        double gust = num(cur, "wind_gusts_10m", wind);
        boolean isDay = cur.has("is_day") && cur.get("is_day").getAsInt() == 1;
        String kind = WeatherKind.fromOpenMeteo(code, rain, precip, gust);
        return new WorldService.WeatherSnapshot(
                kind, temp, humidity, wind, gust, isDay, "open-meteo", false);
    }

    private static double num(JsonObject obj, String key, double fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsDouble();
    }
}