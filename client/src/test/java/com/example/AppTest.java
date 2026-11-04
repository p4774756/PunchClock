package com.example;

import com.example.service.HeartbeatService.PeerInfo;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppTest {

    @Test
    public void versionIsFilteredFromPom() {
        assertFalse(AppVersion.VERSION.contains("${"));
        assertTrue(AppVersion.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    public void offlinePeersSortBelowOnline() {
        List<PeerInfo> peers = Arrays.asList(
                peer("AA123", "OFFLINE"),
                peer("John Wick", "ONLINE"),
                peer("yes123", "ONLINE"),
                peer("yes123asus", "OFFLINE"),
                peer("筆電", "OFFLINE"));
        Set<String> favorites = new HashSet<>();
        favorites.add("John Wick");
        favorites.add("筆電");

        List<String> ids = App.orderPeers(peers, favorites).stream()
                .map(peer -> peer.clientId)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("John Wick", "yes123", "筆電", "AA123", "yes123asus"), ids);
    }

    private static PeerInfo peer(String clientId, String status) {
        return new PeerInfo(clientId, status, "3.0.0", 0, 0, "");
    }

    @Test
    public void releaseTimeIsFilteredFromGitOrBuild() {
        assertNotNull(Instant.parse(AppVersion.RELEASE_TIME_ISO));
        assertTrue(AppVersion.displayLabel().matches("v\\S+( · [0-9a-f]{7})?（(提交|建置) \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}）"));
    }
}
