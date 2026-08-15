package com.example.service;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class HeartbeatServiceCommandParseTest {

    @Test
    public void parseServerCommand_supportsActionsArrayAndLegacyAction() throws Exception {
        HeartbeatService service = new HeartbeatService();
        List<String> received = new ArrayList<>();
        service.setCommandListener(received::add);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);

        String body = "{"
                + "\"success\":true,"
                + "\"action\":\"CANCEL_TASK:abc123\","
                + "\"actions\":[\"CANCEL_TASK:abc123\",\"CANCEL_SCHEDULE\"]"
                + "}";
        method.invoke(service, body, (Consumer<String>) msg -> {});

        assertEquals(2, received.size());
        assertTrue(received.contains("CANCEL_TASK:abc123"));
        assertTrue(received.contains("CANCEL_SCHEDULE"));
    }

    @Test
    public void parseServerCommand_ignoresNone() throws Exception {
        HeartbeatService service = new HeartbeatService();
        AtomicReference<String> received = new AtomicReference<>();
        service.setCommandListener(received::set);

        Method method = HeartbeatService.class.getDeclaredMethod(
                "parseServerCommand", String.class, Consumer.class);
        method.setAccessible(true);
        method.invoke(service, "{\"action\":\"NONE\",\"actions\":[]}", (Consumer<String>) msg -> {});

        assertNull(received.get());
    }
}
