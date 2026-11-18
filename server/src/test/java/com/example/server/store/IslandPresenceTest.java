package com.example.server.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import com.google.gson.Gson;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IslandPresenceTest {

    @Test
    public void cleanNameStripsControlsAndRejectsBlankOrTooLong() {
        assertEquals("小明", IslandPresence.cleanName(" \u0001小明\n "));
        assertEquals("<script>", IslandPresence.cleanName("<script>"));
        assertNull(IslandPresence.cleanName("   "));
        assertNull(IslandPresence.cleanName("\u0007"));
        assertEquals("一二三四五六七八九十甲乙", IslandPresence.cleanName("一二三四五六七八九十甲乙"));
        assertNull(IslandPresence.cleanName("一二三四五六七八九十甲乙丙"));
        assertEquals("旅人 042", IslandPresence.autoName(42));
    }

    @Test
    public void helloFallsBackToAutoNameAndKeepsScriptAsText() {
        IslandPresence presence = new IslandPresence();
        IslandPresence.Slot slot = presence.join("10.0.0.1", 0L);
        IslandPresence.Result blank = presence.handle(slot.id, hello("  \n"), 1L);
        assertNotNull(blank.welcomeJson);
        assertTrue(blank.welcomeJson.contains("旅人 001"));

        IslandPresence.Slot other = presence.join("10.0.0.2", 2L);
        IslandPresence.Result script = presence.handle(other.id, hello("<script>"), 3L);
        assertTrue(script.welcomeJson.contains("<script>"));
        assertEquals("<script>", presence.view(other.id).name);
        assertFalse(script.welcomeJson.contains("<script>alert"));
    }

    @Test
    public void duplicateNamesStayDistinctById() {
        IslandPresence presence = new IslandPresence();
        String a = welcomed(presence, "10.0.0.1", "阿明", 0L);
        String b = welcomed(presence, "10.0.0.2", "阿明", 1L);
        assertEquals("阿明", presence.view(a).name);
        assertEquals("阿明", presence.view(b).name);
        assertFalse(a.equals(b));
    }

    @Test
    public void renameCooldownAndInvalidNameRevertsToAuto() {
        IslandPresence presence = new IslandPresence();
        String id = welcomed(presence, "10.0.0.8", "小明", 0L);
        presence.handle(id, rename("大大"), 1_000L);
        assertEquals("大大", presence.view(id).name);
        presence.handle(id, rename("小小"), 5_000L);
        assertEquals("大大", presence.view(id).name);
        presence.handle(id, rename("新名"), 11_000L);
        assertEquals("新名", presence.view(id).name);
        presence.handle(id, rename("一二三四五六七八九十甲乙丙"), 22_000L);
        assertEquals("旅人 001", presence.view(id).name);
    }

    @Test
    public void clampsFiniteCoordinatesAndDropsIllegalPos() {
        IslandPresence presence = new IslandPresence();
        String id = welcomed(presence, "10.1.1.1", "旅人", 0L);
        presence.handle(id, pos(3, 4, 0.5, true, "outdoor"), 10L);
        assertEquals(3, presence.view(id).x, 0.001);
        assertEquals(4, presence.view(id).z, 0.001);

        presence.handle(id, pos(100, 0, 0, false, "outdoor"), 20L);
        assertEquals(IslandPresence.WORLD_RADIUS, presence.view(id).x, 0.001);
        assertEquals(0, presence.view(id).z, 0.001);

        double[] diagonal = IslandPresence.clampXZ(30, 30);
        assertEquals(IslandPresence.WORLD_RADIUS, Math.hypot(diagonal[0], diagonal[1]), 0.001);

        presence.handle(id, "{\"t\":\"pos\",\"x\":\"no\",\"z\":1,\"yaw\":0,\"moving\":true,\"scene\":\"outdoor\"}", 30L);
        assertEquals(IslandPresence.WORLD_RADIUS, presence.view(id).x, 0.001);
        presence.handle(id, "{\"t\":\"pos\",\"x\":1e309,\"z\":0,\"yaw\":0,\"moving\":false,\"scene\":\"outdoor\"}", 40L);
        assertEquals(IslandPresence.WORLD_RADIUS, presence.view(id).x, 0.001);
        presence.handle(id, pos(1, 1, 0, true, "roof"), 50L);
        assertEquals("outdoor", presence.view(id).scene);
        assertNull(IslandPresence.parse("{"));
        assertNull(IslandPresence.parse("x".repeat(IslandPresence.MAX_MESSAGE_BYTES + 1)));
    }

    @Test
    public void onlySameSceneIsVisible() {
        IslandPresence presence = new IslandPresence();
        String outdoor = welcomed(presence, "10.2.0.1", "外", 0L);
        String inside = welcomed(presence, "10.2.0.2", "內", 1L);
        String shopMate = welcomed(presence, "10.2.0.3", "伴", 2L);
        presence.handle(outdoor, pos(1, 2, 0, false, "outdoor"), 3L);
        presence.handle(inside, pos(0.2, 0.4, 1, true, "shop"), 4L);
        presence.handle(shopMate, pos(-0.2, 0.1, 0, false, "shop"), 5L);

        assertEquals(0, presence.othersInScene(outdoor).size());
        assertEquals(1, presence.othersInScene(inside).size());
        assertEquals(shopMate, presence.othersInScene(inside).get(0).id);
        assertEquals("伴", presence.othersInScene(inside).get(0).name);

        IslandPresence.Result left = presence.handle(inside, pos(1.5, 2.5, 0, false, "outdoor"), 6L);
        assertNotNull(left.leaveJson);
        assertTrue(left.leaveIds.contains(shopMate));
        assertFalse(left.leaveIds.contains(inside));
        assertEquals(1, presence.othersInScene(outdoor).size());
        assertEquals(0, presence.othersInScene(shopMate).size());
    }

    @Test
    public void dropsPosOverFifteenPerSecondAndDisconnectsFlood() {
        IslandPresence presence = new IslandPresence();
        String id = welcomed(presence, "10.3.0.1", "快", 0L);
        for (int i = 1; i <= IslandPresence.POS_PER_SECOND; i++) {
            presence.handle(id, pos(i, 0, 0, true, "outdoor"), 100L);
        }
        assertEquals(IslandPresence.POS_PER_SECOND, presence.view(id).x, 0.001);
        presence.handle(id, pos(99, 0, 0, true, "outdoor"), 100L);
        assertEquals(IslandPresence.POS_PER_SECOND, presence.view(id).x, 0.001);

        IslandPresence.Result closed = null;
        for (int i = 0; i < IslandPresence.ABUSE_PER_SECOND; i++) {
            closed = presence.handle(id, pos(1, 0, 0, true, "outdoor"), 100L);
            if (closed.close) {
                break;
            }
        }
        assertNotNull(closed);
        assertTrue(closed.close);
        assertNull(presence.view(id));
    }

    @Test
    public void rejectsConnectionCaps() {
        IslandPresence presence = new IslandPresence();
        for (int i = 0; i < IslandPresence.MAX_PER_IP; i++) {
            assertTrue(presence.join("10.4.0.9", i).ok());
        }
        IslandPresence.Slot limited = presence.join("10.4.0.9", 10L);
        assertFalse(limited.ok());
        assertEquals("ip", limited.reject);
        assertEquals(IslandPresence.MAX_PER_IP, presence.connectionsFrom("10.4.0.9"));

        IslandPresence crowded = new IslandPresence();
        for (int i = 0; i < IslandPresence.MAX_CONNECTIONS; i++) {
            assertTrue(crowded.join("10.5.0." + i, i).ok());
        }
        IslandPresence.Slot full = crowded.join("10.9.9.9", 1L);
        assertFalse(full.ok());
        assertEquals("full", full.reject);
    }

    @Test
    public void sweepRemovesIdleAndAnnouncesPlacedPeers() {
        IslandPresence presence = new IslandPresence();
        String id = welcomed(presence, "10.6.0.1", "站著", 0L);
        String watcher = welcomed(presence, "10.6.0.2", "看", 1L);
        presence.handle(id, pos(2, 3, 0, false, "outdoor"), 2L);
        presence.handle(watcher, pos(4, 5, 0, false, "outdoor"), 3L);
        assertTrue(presence.sweepIdle(2L + IslandPresence.IDLE_MS - 1).isEmpty());
        List<IslandPresence.Gone> gone = presence.sweepIdle(2L + IslandPresence.IDLE_MS);
        assertEquals(1, gone.size());
        assertEquals(id, gone.get(0).id);
        assertNotNull(gone.get(0).json);
        assertTrue(gone.get(0).ids.contains(watcher));
        assertNull(presence.view(id));
        assertNotNull(presence.view(watcher));
    }

    @Test
    public void snapshotListsOnlySameSceneWhenSomethingChanged() {
        IslandPresence presence = new IslandPresence();
        String a = welcomed(presence, "10.7.0.1", "甲", 0L);
        String b = welcomed(presence, "10.7.0.2", "乙", 1L);
        assertTrue(presence.takeSnaps().isEmpty());
        presence.handle(a, pos(1, 1, 0.2, true, "outdoor"), 2L);
        presence.handle(b, pos(2, 2, 0, false, "casino"), 3L);
        List<IslandPresence.Snap> snaps = presence.takeSnaps();
        assertEquals(2, snaps.size());
        String outdoor = snapFor(snaps, "outdoor");
        String casino = snapFor(snaps, "casino");
        assertTrue(outdoor.contains("甲"));
        assertFalse(outdoor.contains("乙"));
        assertTrue(casino.contains("乙"));
        assertFalse(casino.contains("甲"));
        assertTrue(presence.takeSnaps().isEmpty());
    }

    @Test
    public void clientAddressIgnoresForwardedHeaderUnlessTrusted() {
        assertEquals("10.0.0.8", IslandPresence.clientAddress(false, "1.2.3.4, 5.6.7.8", "10.0.0.8"));
        assertEquals("1.2.3.4", IslandPresence.clientAddress(true, "1.2.3.4, 5.6.7.8", "10.0.0.8"));
        assertEquals("10.0.0.8", IslandPresence.clientAddress(true, "  ", "10.0.0.8"));
        assertEquals("unknown", IslandPresence.clientAddress(false, null, " "));
    }

    @Test
    public void cleanChatTextKeepsPunctuationAndTruncates() {
        assertEquals("你好！", IslandPresence.cleanChatText(" \u0001你好！\n "));
        assertEquals("<img src=x>", IslandPresence.cleanChatText("<img src=x>"));
        assertEquals("🙂✨", IslandPresence.cleanChatText("🙂✨"));
        assertNull(IslandPresence.cleanChatText("   "));
        assertNull(IslandPresence.cleanChatText("\u0007"));
        String sixty = "一二三四五六七八九十".repeat(6);
        assertEquals(sixty, IslandPresence.cleanChatText(sixty));
        assertEquals(sixty, IslandPresence.cleanChatText(sixty + "甲"));
        assertNull(IslandPresence.cleanName(sixty + "甲"));
    }

    @Test
    public void sayRequiresWelcomeAndBroadcastsSameSceneIncludingSelf() {
        IslandPresence presence = new IslandPresence();
        String a = welcomed(presence, "10.8.0.1", "甲", 0L);
        String b = welcomed(presence, "10.8.0.2", "乙", 1L);
        String shop = welcomed(presence, "10.8.0.3", "丙", 2L);
        presence.handle(a, pos(1, 1, 0, false, "outdoor"), 3L);
        presence.handle(b, pos(2, 2, 0, false, "outdoor"), 4L);
        presence.handle(shop, pos(0.1, 0.1, 0, false, "shop"), 5L);

        IslandPresence.Slot raw = presence.join("10.8.0.4", 6L);
        IslandPresence.Result beforeHello = presence.handle(raw.id, say("嗨"), 7L);
        assertNull(beforeHello.saidJson);
        assertNull(beforeHello.slowJson);
        assertFalse(beforeHello.close);

        IslandPresence.Result said = presence.handle(a, say("  你好！ "), 8L);
        assertNotNull(said.saidJson);
        assertTrue(said.saidIds.contains(a));
        assertTrue(said.saidIds.contains(b));
        assertFalse(said.saidIds.contains(shop));
        JsonObject body = JsonParser.parseString(said.saidJson).getAsJsonObject();
        assertEquals("said", body.get("t").getAsString());
        assertEquals(a, body.get("id").getAsString());
        assertEquals("甲", body.get("name").getAsString());
        assertEquals("你好！", body.get("text").getAsString());
        assertEquals(8L, body.get("ts").getAsLong());
        assertNull(said.slowJson);
        assertFalse(said.close);
    }

    @Test
    public void sayRateLimitsPerPeerAndIpWithoutDisconnect() {
        IslandPresence presence = new IslandPresence();
        String id = welcomed(presence, "10.9.0.1", "快嘴", 0L);
        IslandPresence.Result first = presence.handle(id, say("第一句"), 1_000L);
        assertNotNull(first.saidJson);
        IslandPresence.Result peerSlow = presence.handle(id, say("太快"), 2_000L);
        assertNull(peerSlow.saidJson);
        assertNotNull(peerSlow.slowJson);
        assertTrue(peerSlow.slowJson.contains("\"slow\""));
        assertFalse(peerSlow.close);
        assertNotNull(presence.view(id));

        IslandPresence.Result again = presence.handle(id, say("第二句"), 3_100L);
        assertNotNull(again.saidJson);

        for (int i = 0; i < IslandPresence.CHAT_IP_PER_MINUTE - 2; i++) {
            IslandPresence.Result ok = presence.handle(id, say("第" + i), 10_000L + i * IslandPresence.CHAT_PEER_MS);
            assertNotNull(ok.saidJson);
            assertNull(ok.slowJson);
        }
        IslandPresence.Result ipSlow = presence.handle(id, say("爆了"),
                10_000L + (IslandPresence.CHAT_IP_PER_MINUTE - 2) * IslandPresence.CHAT_PEER_MS);
        assertNull(ipSlow.saidJson);
        assertNotNull(ipSlow.slowJson);
        assertFalse(ipSlow.close);
        assertNotNull(presence.view(id));
    }

    @Test
    public void posAvatarDefaultsToMiluAndBerryInSnapshot() {
        IslandPresence presence = new IslandPresence();
        String miluPeer = welcomed(presence, "10.10.0.1", "蜜鹿", 0L);
        String berryPeer = welcomed(presence, "10.10.0.2", "小莓", 1L);
        presence.handle(miluPeer, pos(1, 1, 0, false, "outdoor"), 2L);
        presence.handle(berryPeer, pos(2, 2, 0, true, "outdoor", "berry"), 3L);
        String outdoor = snapFor(presence.takeSnaps(), "outdoor");
        assertFalse(outdoor.contains("\"avatar\":\"milu\""));
        assertTrue(outdoor.contains("\"avatar\":\"berry\""));
        assertEquals("milu", IslandPresence.cleanAvatar(null));
        assertEquals("milu", IslandPresence.cleanAvatar("cat"));
        assertEquals("berry", IslandPresence.cleanAvatar("berry"));
    }

    @Test
    public void originMustMatchHostAndScheme() {
        assertTrue(IslandPresence.sameOrigin("http://localhost:3000", "localhost:3000", false));
        assertFalse(IslandPresence.sameOrigin("https://localhost:3000", "localhost:3000", false));
        assertTrue(IslandPresence.sameOrigin("https://app.example.com", "app.example.com", true));
        assertFalse(IslandPresence.sameOrigin("https://evil.example", "app.example.com", true));
        assertFalse(IslandPresence.sameOrigin(null, "localhost:3000", false));
        assertFalse(IslandPresence.sameOrigin("http://localhost:3000", "localhost:3001", false));
        assertFalse(IslandPresence.sameOrigin("null", "localhost:3000", false));
    }

    private static String welcomed(IslandPresence presence, String ip, String name, long now) {
        IslandPresence.Slot slot = presence.join(ip, now);
        assertTrue(slot.ok());
        IslandPresence.Result result = presence.handle(slot.id, hello(name), now);
        assertNotNull(result.welcomeJson);
        return slot.id;
    }

    private static String hello(String name) {
        return "{\"t\":\"hello\",\"name\":" + jsonString(name) + "}";
    }

    private static String rename(String name) {
        return "{\"t\":\"rename\",\"name\":" + jsonString(name) + "}";
    }

    private static String say(String text) {
        return "{\"t\":\"say\",\"text\":" + jsonString(text) + "}";
    }

    private static String pos(double x, double z, double yaw, boolean moving, String scene) {
        return pos(x, z, yaw, moving, scene, null);
    }

    private static String pos(double x, double z, double yaw, boolean moving, String scene, String avatar) {
        StringBuilder body = new StringBuilder();
        body.append("{\"t\":\"pos\",\"x\":").append(x)
                .append(",\"z\":").append(z)
                .append(",\"yaw\":").append(yaw)
                .append(",\"moving\":").append(moving)
                .append(",\"scene\":").append(jsonString(scene));
        if (avatar != null) {
            body.append(",\"avatar\":").append(jsonString(avatar));
        }
        body.append("}");
        return body.toString();
    }

    private static final Gson JSON = new Gson();

    private static String jsonString(String value) {
        return JSON.toJson(value);
    }

    private static String snapFor(List<IslandPresence.Snap> snaps, String scene) {
        for (int i = 0; i < snaps.size(); i++) {
            JsonObject object = JsonParser.parseString(snaps.get(i).json).getAsJsonObject();
            if (scene.equals(object.get("scene").getAsString())) {
                return snaps.get(i).json;
            }
        }
        throw new AssertionError("沒有 " + scene + " 快照");
    }
}
