package com.example.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class RelayProxyServiceTest {

    @Test
    public void clampPortReturnsDefaultForInvalidPorts() {
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(0));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(-1));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(100));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(1023));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(70000));
    }

    @Test
    public void clampPortAcceptsValidPorts() {
        assertEquals(1024, RelayProxyService.clampPort(1024));
        assertEquals(8888, RelayProxyService.clampPort(8888));
        assertEquals(65535, RelayProxyService.clampPort(65535));
    }

    @Test
    public void serviceStartsAndStops() {
        RelayProxyService service = new RelayProxyService();
        assertFalse(service.isRunning());

        boolean started = service.start(9999, "http://localhost:3000", "token", null);
        assertTrue(started);
        assertTrue(service.isRunning());
        assertEquals(9999, service.getPort());
        assertEquals("http://localhost:3000", service.getTargetServerUrl());

        service.stop();
        assertFalse(service.isRunning());
    }

    @Test
    public void serviceDoesNotStartWithoutTargetUrl() {
        RelayProxyService service = new RelayProxyService();
        boolean started = service.start(8888, "", "token", null);
        assertFalse(started);
        assertFalse(service.isRunning());
    }

    @Test
    public void trustAllSslDefaultsOffAndCanToggle() {
        RelayProxyService service = new RelayProxyService();
        assertFalse(service.isTrustAllSsl());
        service.setTrustAllSsl(true);
        assertTrue(service.isTrustAllSsl());
        service.setTrustAllSsl(false);
        assertFalse(service.isTrustAllSsl());
    }

    @Test
    public void serviceTrimsTrailingSlash() {
        RelayProxyService service = new RelayProxyService();
        service.start(9998, "http://localhost:3000/", "token", null);
        assertEquals("http://localhost:3000", service.getTargetServerUrl());
        service.stop();
    }
}
