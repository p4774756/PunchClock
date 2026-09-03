package com.example.server;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerAppHeartbeatSeqTest {

    @Test
    public void restartFromLowSeq_isAccepted() {
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(1, 500));
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(3, 120));
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(10, 11));
    }

    @Test
    public void largeBackwardJump_isTreatedAsRestart() {
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(30, 100));
    }

    @Test
    public void slightOutOfOrder_isStale() {
        assertTrue(ServerApp.isOutOfOrderStaleHeartbeat(50, 51));
        assertTrue(ServerApp.isOutOfOrderStaleHeartbeat(48, 55));
    }

    @Test
    public void equalOrForwardSeq_isNotStale() {
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(55, 55));
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(56, 55));
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(0, 55));
        assertFalse(ServerApp.isOutOfOrderStaleHeartbeat(10, 0));
    }
}
