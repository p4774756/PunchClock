package com.example.server.store;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeatherKindTest {

    @Test
    public void mapsWmoCodes() {
        assertEquals(WeatherKind.CLEAR, WeatherKind.fromOpenMeteo(0, 0, 0, 10));
        assertEquals(WeatherKind.CLEAR, WeatherKind.fromOpenMeteo(1, 0, 0, 10));
        assertEquals(WeatherKind.CLOUDY, WeatherKind.fromOpenMeteo(2, 0, 0, 10));
        assertEquals(WeatherKind.CLOUDY, WeatherKind.fromOpenMeteo(3, 0, 0, 10));
        assertEquals(WeatherKind.FOG, WeatherKind.fromOpenMeteo(45, 0, 0, 10));
        assertEquals(WeatherKind.FOG, WeatherKind.fromOpenMeteo(48, 0, 0, 10));
        assertEquals(WeatherKind.RAIN, WeatherKind.fromOpenMeteo(55, 2, 2, 10));
        assertEquals(WeatherKind.RAIN, WeatherKind.fromOpenMeteo(80, 1, 1, 10));
        assertEquals(WeatherKind.STORM, WeatherKind.fromOpenMeteo(95, 0, 0, 10));
        assertEquals(WeatherKind.STORM, WeatherKind.fromOpenMeteo(99, 0, 0, 10));
    }

    @Test
    public void gustOrRainUpgradesToStorm() {
        assertEquals(WeatherKind.STORM, WeatherKind.fromOpenMeteo(1, 0, 0, 62));
        assertEquals(WeatherKind.STORM, WeatherKind.fromOpenMeteo(1, 16, 0, 10));
        assertEquals(WeatherKind.STORM, WeatherKind.fromOpenMeteo(3, 0, 15, 20));
    }

    @Test
    public void validatesKinds() {
        assertTrue(WeatherKind.isValidKind("clear"));
        assertTrue(WeatherKind.isValidKind("storm"));
    }
}