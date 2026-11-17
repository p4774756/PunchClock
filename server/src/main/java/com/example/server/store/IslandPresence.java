package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 島上在線玩家。只活在記憶體，重啟就清空。
 * 同一 scene 的人才會互相出現；室內與室外互不可見。
 */
public final class IslandPresence {

    public static final int MAX_MESSAGE_BYTES = 512;
    public static final int MAX_CONNECTIONS = 50;
    public static final int MAX_PER_IP = 4;
    public static final int POS_PER_SECOND = 15;
    /** 同一秒訊息多過這個數就斷線。pos 超過每秒 15 則只丟掉，不立刻斷。 */
    public static final int ABUSE_PER_SECOND = 40;
    public static final long IDLE_MS = 60_000L;
    public static final long RENAME_MS = 10_000L;
    /**
     * 群島外緣大約離原點 22（電腦島、中醫島的視覺半徑）。
     * 室內傳的是房間本地座標，也落在這個圓裡。
     */
    public static final double WORLD_RADIUS = 36;
    public static final int NAME_MAX = 12;
    public static final int CHAT_MAX = 60;
    /** 同一連線兩則聊天至少間隔這麼久。超限回 slow，不斷線。 */
    public static final long CHAT_PEER_MS = 2_000L;
    /** 同一 IP 在這個視窗內最多 CHAT_IP_PER_MINUTE 則。 */
    public static final long CHAT_IP_WINDOW_MS = 60_000L;
    public static final int CHAT_IP_PER_MINUTE = 20;
    public static final String SCENE_OUTDOOR = "outdoor";

    private static final Set<String> SCENES;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    static {
        Set<String> scenes = new LinkedHashSet<>();
        scenes.add(SCENE_OUTDOOR);
        scenes.add("shop");
        scenes.add("casino");
        scenes.add("tcm-site");
        scenes.add("computer-site");
        SCENES = Collections.unmodifiableSet(scenes);
    }

    private final Map<String, Peer> peers = new LinkedHashMap<>();
    private final Map<String, Integer> ipCount = new LinkedHashMap<>();
    private final Map<String, java.util.ArrayDeque<Long>> ipSayAt = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private int nameSeq;
    private long revision;

    public static boolean allowedScene(String scene) {
        return scene != null && SCENES.contains(scene);
    }

    /** 去掉控制字元與頭尾空白。空的或超過 12 個字回傳 null，呼叫端改用自動名。 */
    public static String cleanName(String raw) {
        String source = raw == null ? "" : raw;
        StringBuilder kept = new StringBuilder();
        source.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) {
                return;
            }
            kept.appendCodePoint(cp);
        });
        String trimmed = kept.toString().strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > NAME_MAX) {
            return null;
        }
        return trimmed;
    }

    /**
     * 聊天文字：去掉控制字元與頭尾空白，保留標點與 emoji。
     * 空的回 null；超過 {@link #CHAT_MAX} 個字就截斷。不要拿來清理暱稱。
     */
    public static String cleanChatText(String raw) {
        String source = raw == null ? "" : raw;
        StringBuilder kept = new StringBuilder();
        source.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) {
                return;
            }
            kept.appendCodePoint(cp);
        });
        String trimmed = kept.toString().strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count <= CHAT_MAX) {
            return trimmed;
        }
        int end = trimmed.offsetByCodePoints(0, CHAT_MAX);
        return trimmed.substring(0, end);
    }

    public static String autoName(int n) {
        int value = Math.floorMod(n, 1000);
        return String.format("旅人 %03d", value);
    }

    /** 有限座標夾進島的半徑。呼叫前須先確認是有限數字。 */
    public static double[] clampXZ(double x, double z) {
        double radius = Math.hypot(x, z);
        if (!(radius > WORLD_RADIUS) || radius == 0) {
            return new double[] {x, z};
        }
        double scale = WORLD_RADIUS / radius;
        return new double[] {x * scale, z * scale};
    }

    public static double wrapYaw(double yaw) {
        double turn = yaw % (Math.PI * 2);
        if (turn <= -Math.PI) {
            turn += Math.PI * 2;
        } else if (turn > Math.PI) {
            turn -= Math.PI * 2;
        }
        return turn;
    }

    /**
     * trustProxy 為 false 時忽略 X-Forwarded-For，只用連線位址。
     * 不要走會無條件相信轉發標頭的 IpResolver。
     */
    public static String clientAddress(boolean trustProxy, String forwardedFor, String remote) {
        if (trustProxy && forwardedFor != null && !forwardedFor.isEmpty()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        if (remote == null || remote.isBlank()) {
            return "unknown";
        }
        return remote.trim();
    }

    /** Origin 必須跟這次連線的 Host、以及實際的 http/https 一致。缺 Origin 或對不上就拒絕。 */
    public static boolean sameOrigin(String origin, String host, boolean secure) {
        if (origin == null || host == null) {
            return false;
        }
        String originText = origin.trim();
        String hostText = host.trim();
        if (originText.isEmpty() || hostText.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(originText);
        } catch (URISyntaxException ex) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        boolean originSecure = "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
        boolean originPlain = "http".equalsIgnoreCase(scheme) || "ws".equalsIgnoreCase(scheme);
        if (originSecure == originPlain || originSecure != secure) {
            return false;
        }
        String originHost = uri.getHost();
        if (originHost == null || originHost.isEmpty()) {
            return false;
        }
        HostParts parts = hostParts(hostText);
        if (parts == null || !originHost.equalsIgnoreCase(parts.host)) {
            return false;
        }
        int fallback = secure ? 443 : 80;
        int originPort = uri.getPort() == -1 ? fallback : uri.getPort();
        int hostPort = parts.port == -1 ? fallback : parts.port;
        return originPort == hostPort;
    }

    public synchronized int connections() {
        return peers.size();
    }

    public synchronized int connectionsFrom(String ip) {
        String key = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        return ipCount.getOrDefault(key, 0);
    }

    public synchronized Slot join(String ip, long now) {
        String key = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        if (peers.size() >= MAX_CONNECTIONS) {
            return Slot.full();
        }
        int count = ipCount.getOrDefault(key, 0);
        if (count >= MAX_PER_IP) {
            return Slot.ipLimit();
        }
        String id = newId();
        nameSeq = nameSeq % 999 + 1;
        Peer peer = new Peer(id, key, autoName(nameSeq), now);
        peers.put(id, peer);
        ipCount.put(key, count + 1);
        return Slot.ok(id);
    }

    public synchronized View view(String id) {
        Peer peer = peers.get(id);
        if (peer == null) {
            return null;
        }
        return peer.view();
    }

    /** 同一個 scene、已經報過位置、而且不是自己的人。 */
    public synchronized List<View> othersInScene(String id) {
        Peer self = peers.get(id);
        if (self == null) {
            return List.of();
        }
        List<View> list = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (peer == self || !peer.placed || !self.scene.equals(peer.scene)) {
                continue;
            }
            list.add(peer.view());
        }
        return list;
    }

    public synchronized Result handle(String id, String text, long now) {
        Peer peer = peers.get(id);
        if (peer == null) {
            return Result.none();
        }
        if (markTraffic(peer, now)) {
            return disconnect(peer);
        }
        Inbound inbound = parse(text);
        if (inbound == null) {
            return Result.none();
        }
        switch (inbound.type) {
            case "hello":
                return hello(peer, inbound);
            case "pos":
                return pos(peer, inbound);
            case "rename":
                return rename(peer, inbound, now);
            case "say":
                return say(peer, inbound, now);
            default:
                return Result.none();
        }
    }

    public synchronized Gone disconnect(String id) {
        Peer peer = peers.remove(id);
        if (peer == null) {
            return null;
        }
        releaseIp(peer.ip);
        if (!peer.placed) {
            return new Gone(peer.id, null, List.of());
        }
        return leave(peer);
    }

    /** 超過 60 秒沒有任何訊息的連線。呼叫端要廣播 leave 並關掉 socket。 */
    public synchronized List<Gone> sweepIdle(long now) {
        List<Peer> expired = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (now - peer.lastMessageAt >= IDLE_MS) {
                expired.add(peer);
            }
        }
        List<Gone> gone = new ArrayList<>();
        for (int i = 0; i < expired.size(); i++) {
            Peer peer = expired.get(i);
            peers.remove(peer.id);
            releaseIp(peer.ip);
            if (peer.placed) {
                gone.add(leave(peer));
            } else {
                gone.add(new Gone(peer.id, null, List.of()));
            }
        }
        return gone;
    }

    /** 有變動的 scene 各一份快照。沒變化就不送。 */
    public synchronized List<Snap> takeSnaps() {
        if (dirty.isEmpty()) {
            return List.of();
        }
        List<String> scenes = new ArrayList<>(dirty);
        dirty.clear();
        List<Snap> snaps = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            String scene = scenes.get(i);
            List<Peer> show = new ArrayList<>();
            List<String> recipients = new ArrayList<>();
            for (Peer peer : peers.values()) {
                if (!scene.equals(peer.scene) || !peer.welcomed) {
                    continue;
                }
                recipients.add(peer.id);
                if (peer.placed) {
                    show.add(peer);
                }
            }
            if (recipients.isEmpty() || show.isEmpty()) {
                continue;
            }
            snaps.add(new Snap(snapshotJson(scene, show), recipients));
        }
        return snaps;
    }

    /** 壞 JSON、超長、或欄位型別不對就回 null，呼叫端丟掉這則。 */
    public static Inbound parse(String text) {
        if (text == null) {
            return null;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            return null;
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (JsonParseException ex) {
            return null;
        }
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String type = stringField(object, "t");
        if (type == null) {
            return null;
        }
        if ("hello".equals(type) || "rename".equals(type)) {
            String name = optionalString(object, "name");
            if (name == null) {
                return null;
            }
            return Inbound.named(type, name);
        }
        if ("say".equals(type)) {
            String chat = optionalString(object, "text");
            if (chat == null) {
                return null;
            }
            return Inbound.say(chat);
        }
        if (!"pos".equals(type)) {
            return null;
        }
        Double x = finite(object.get("x"));
        Double z = finite(object.get("z"));
        Double yaw = finite(object.get("yaw"));
        Boolean moving = boolField(object.get("moving"));
        String scene = stringField(object, "scene");
        if (x == null || z == null || yaw == null || moving == null || !allowedScene(scene)) {
            return null;
        }
        return Inbound.pos(x, z, yaw, moving, scene);
    }

    private Result hello(Peer peer, Inbound inbound) {
        if (peer.welcomed) {
            return Result.none();
        }
        String cleaned = cleanName(inbound.name);
        peer.name = cleaned == null ? peer.autoName : cleaned;
        peer.welcomed = true;
        return Result.welcome(welcomeJson(peer));
    }

    private Result pos(Peer peer, Inbound inbound) {
        peer.posInWindow += 1;
        if (peer.posInWindow > ABUSE_PER_SECOND) {
            return disconnect(peer);
        }
        if (!peer.welcomed || peer.posInWindow > POS_PER_SECOND) {
            return Result.none();
        }
        double[] clamped = clampXZ(inbound.x, inbound.z);
        String previous = peer.scene;
        boolean movedScene = peer.placed && !previous.equals(inbound.scene);
        peer.x = clamped[0];
        peer.z = clamped[1];
        peer.yaw = wrapYaw(inbound.yaw);
        peer.moving = inbound.moving;
        peer.scene = inbound.scene;
        peer.placed = true;
        dirty.add(peer.scene);
        if (!movedScene) {
            return Result.none();
        }
        dirty.add(previous);
        Gone gone = leaveFrom(peer, previous);
        return Result.leave(gone.json, gone.ids);
    }

    private Result rename(Peer peer, Inbound inbound, long now) {
        if (!peer.welcomed) {
            return Result.none();
        }
        if (peer.lastRenameAt != Long.MIN_VALUE && now - peer.lastRenameAt < RENAME_MS) {
            return Result.none();
        }
        String cleaned = cleanName(inbound.name);
        String next = cleaned == null ? peer.autoName : cleaned;
        peer.lastRenameAt = now;
        if (next.equals(peer.name)) {
            return Result.none();
        }
        peer.name = next;
        if (peer.placed) {
            dirty.add(peer.scene);
        }
        return Result.none();
    }

    private Result say(Peer peer, Inbound inbound, long now) {
        if (!peer.welcomed) {
            return Result.none();
        }
        String cleaned = cleanChatText(inbound.text);
        if (cleaned == null) {
            return Result.none();
        }
        if (peer.lastSayAt != Long.MIN_VALUE && now - peer.lastSayAt < CHAT_PEER_MS) {
            return Result.slow(slowJson());
        }
        if (ipSayLimited(peer.ip, now)) {
            return Result.slow(slowJson());
        }
        peer.lastSayAt = now;
        recordIpSay(peer.ip, now);
        List<String> ids = welcomedInScene(peer.scene);
        if (ids.isEmpty()) {
            return Result.none();
        }
        return Result.said(saidJson(peer, cleaned, now), ids);
    }

    private List<String> welcomedInScene(String scene) {
        List<String> ids = new ArrayList<>();
        for (Peer other : peers.values()) {
            if (!other.welcomed || !scene.equals(other.scene)) {
                continue;
            }
            ids.add(other.id);
        }
        return ids;
    }

    private static String saidJson(Peer peer, String text, long now) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("t", "said");
        body.put("id", peer.id);
        body.put("name", peer.name);
        body.put("text", text);
        body.put("ts", now);
        return GSON.toJson(body);
    }

    private static String slowJson() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("t", "slow");
        return GSON.toJson(body);
    }

    private boolean ipSayLimited(String ip, long now) {
        java.util.ArrayDeque<Long> times = pruneIpSays(ip, now);
        return times.size() >= CHAT_IP_PER_MINUTE;
    }

    private void recordIpSay(String ip, long now) {
        pruneIpSays(ip, now).addLast(now);
    }

    private java.util.ArrayDeque<Long> pruneIpSays(String ip, long now) {
        String key = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        java.util.ArrayDeque<Long> times = ipSayAt.get(key);
        if (times == null) {
            times = new java.util.ArrayDeque<>();
            ipSayAt.put(key, times);
        }
        while (!times.isEmpty() && now - times.peekFirst() >= CHAT_IP_WINDOW_MS) {
            times.pollFirst();
        }
        if (times.isEmpty()) {
            ipSayAt.put(key, times);
        }
        return times;
    }

    private Result disconnect(Peer peer) {
        peers.remove(peer.id);
        releaseIp(peer.ip);
        if (!peer.placed) {
            return Result.close(null, List.of());
        }
        Gone gone = leave(peer);
        return Result.close(gone.json, gone.ids);
    }

    /** @return 這一秒的訊息已經超量，應該斷線 */
    private boolean markTraffic(Peer peer, long now) {
        if (peer.windowStart == 0 || now - peer.windowStart >= 1000L) {
            peer.windowStart = now;
            peer.messagesInWindow = 0;
            peer.posInWindow = 0;
        }
        peer.messagesInWindow += 1;
        peer.lastMessageAt = now;
        return peer.messagesInWindow > ABUSE_PER_SECOND;
    }

    private Gone leave(Peer peer) {
        return leaveFrom(peer, peer.scene);
    }

    private Gone leaveFrom(Peer peer, String scene) {
        List<String> ids = new ArrayList<>();
        for (Peer other : peers.values()) {
            if (other == peer || !other.welcomed || !scene.equals(other.scene)) {
                continue;
            }
            ids.add(other.id);
        }
        if (ids.isEmpty()) {
            return new Gone(peer.id, null, List.of());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("t", "leave");
        body.put("id", peer.id);
        body.put("rev", nextRev());
        return new Gone(peer.id, GSON.toJson(body), ids);
    }

    private String welcomeJson(Peer self) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("t", "welcome");
        body.put("id", self.id);
        body.put("name", self.name);
        body.put("rev", nextRev());
        body.put("players", playerMaps(placedIn(self.scene, self)));
        return GSON.toJson(body);
    }

    private String snapshotJson(String scene, List<Peer> show) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("t", "snap");
        body.put("scene", scene);
        body.put("rev", nextRev());
        body.put("players", playerMaps(show));
        return GSON.toJson(body);
    }

    private List<Peer> placedIn(String scene, Peer skip) {
        List<Peer> list = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (peer == skip || !peer.placed || !scene.equals(peer.scene)) {
                continue;
            }
            list.add(peer);
        }
        return list;
    }

    private static List<Map<String, Object>> playerMaps(List<Peer> peers) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < peers.size(); i++) {
            list.add(peers.get(i).json());
        }
        return list;
    }

    private long nextRev() {
        revision += 1;
        return revision;
    }

    private void releaseIp(String ip) {
        Integer count = ipCount.get(ip);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            ipCount.remove(ip);
        } else {
            ipCount.put(ip, count - 1);
        }
    }

    private String newId() {
        String id;
        do {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (peers.containsKey(id));
        return id;
    }

    private static String stringField(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            return null;
        }
        return primitive.getAsString();
    }

    /** name 可以省略，省略當空字串。型別不對則整則丟掉。 */
    private static String optionalString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return stringField(object, key);
    }

    private static Double finite(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value)) {
            return null;
        }
        return value;
    }

    private static Boolean boolField(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            return null;
        }
        return primitive.getAsBoolean();
    }

    private static HostParts hostParts(String host) {
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end <= 1) {
                return null;
            }
            String name = host.substring(1, end);
            if (end == host.length() - 1) {
                return new HostParts(name, -1);
            }
            if (host.charAt(end + 1) != ':' || end + 2 >= host.length()) {
                return null;
            }
            Integer port = port(host.substring(end + 2));
            if (port == null) {
                return null;
            }
            return new HostParts(name, port);
        }
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            Integer port = port(host.substring(colon + 1));
            if (port == null) {
                return null;
            }
            return new HostParts(host.substring(0, colon), port);
        }
        return new HostParts(host, -1);
    }

    private static Integer port(String text) {
        try {
            int value = Integer.parseInt(text);
            if (value < 1 || value > 65535) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class HostParts {
        final String host;
        final int port;

        HostParts(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class Peer {
        final String id;
        final String ip;
        final String autoName;
        String name;
        String scene = SCENE_OUTDOOR;
        double x;
        double z;
        double yaw;
        boolean moving;
        boolean placed;
        boolean welcomed;
        long lastMessageAt;
        long lastRenameAt = Long.MIN_VALUE;
        long lastSayAt = Long.MIN_VALUE;
        long windowStart;
        int messagesInWindow;
        int posInWindow;

        Peer(String id, String ip, String autoName, long now) {
            this.id = id;
            this.ip = ip;
            this.autoName = autoName;
            this.name = autoName;
            this.lastMessageAt = now;
        }

        View view() {
            return new View(id, name, scene, x, z, yaw, moving, placed, welcomed);
        }

        Map<String, Object> json() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", id);
            body.put("name", name);
            body.put("x", round3(x));
            body.put("z", round3(z));
            body.put("yaw", round3(yaw));
            body.put("moving", moving);
            return body;
        }

        static double round3(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }

    public static final class Slot {
        public final String id;
        public final String reject;

        private Slot(String id, String reject) {
            this.id = id;
            this.reject = reject;
        }

        public boolean ok() {
            return id != null;
        }

        static Slot ok(String id) {
            return new Slot(id, null);
        }

        static Slot full() {
            return new Slot(null, "full");
        }

        static Slot ipLimit() {
            return new Slot(null, "ip");
        }
    }

    public static final class View {
        public final String id;
        public final String name;
        public final String scene;
        public final double x;
        public final double z;
        public final double yaw;
        public final boolean moving;
        public final boolean placed;
        public final boolean welcomed;

        View(String id, String name, String scene, double x, double z, double yaw,
                boolean moving, boolean placed, boolean welcomed) {
            this.id = id;
            this.name = name;
            this.scene = scene;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.moving = moving;
            this.placed = placed;
            this.welcomed = welcomed;
        }
    }

    public static final class Inbound {
        public final String type;
        public final String name;
        public final String text;
        public final double x;
        public final double z;
        public final double yaw;
        public final boolean moving;
        public final String scene;

        private Inbound(String type, String name, String text, double x, double z, double yaw, boolean moving,
                String scene) {
            this.type = type;
            this.name = name;
            this.text = text;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.moving = moving;
            this.scene = scene;
        }

        static Inbound named(String type, String name) {
            return new Inbound(type, name, "", 0, 0, 0, false, null);
        }

        static Inbound say(String text) {
            return new Inbound("say", "", text, 0, 0, 0, false, null);
        }

        static Inbound pos(double x, double z, double yaw, boolean moving, String scene) {
            return new Inbound("pos", "", "", x, z, yaw, moving, scene);
        }
    }

    public static final class Result {
        public final String welcomeJson;
        public final String leaveJson;
        public final List<String> leaveIds;
        public final String saidJson;
        public final List<String> saidIds;
        public final String slowJson;
        public final boolean close;

        private Result(String welcomeJson, String leaveJson, List<String> leaveIds,
                String saidJson, List<String> saidIds, String slowJson, boolean close) {
            this.welcomeJson = welcomeJson;
            this.leaveJson = leaveJson;
            this.leaveIds = leaveIds;
            this.saidJson = saidJson;
            this.saidIds = saidIds;
            this.slowJson = slowJson;
            this.close = close;
        }

        static Result none() {
            return new Result(null, null, List.of(), null, List.of(), null, false);
        }

        static Result welcome(String json) {
            return new Result(json, null, List.of(), null, List.of(), null, false);
        }

        static Result leave(String json, List<String> ids) {
            return new Result(null, json, ids, null, List.of(), null, false);
        }

        static Result said(String json, List<String> ids) {
            return new Result(null, null, List.of(), json, ids, null, false);
        }

        static Result slow(String json) {
            return new Result(null, null, List.of(), null, List.of(), json, false);
        }

        static Result close(String json, List<String> ids) {
            return new Result(null, json, ids, null, List.of(), null, true);
        }
    }

    public static final class Gone {
        public final String id;
        public final String json;
        public final List<String> ids;

        Gone(String id, String json, List<String> ids) {
            this.id = id;
            this.json = json;
            this.ids = ids;
        }
    }

    public static final class Snap {
        public final String json;
        public final List<String> ids;

        Snap(String json, List<String> ids) {
            this.json = json;
            this.ids = ids;
        }
    }
}
