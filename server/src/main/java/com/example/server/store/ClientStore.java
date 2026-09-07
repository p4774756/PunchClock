package com.example.server.store;

import com.example.PeerFileRules;
import com.google.gson.Gson;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public final class ClientStore {

    public static final long HEARTBEAT_TIMEOUT_MS = 3L * 60L * 1000L;
    public static final long PENDING_ACTION_TTL_MS = 30_000L;
    private static final int EVENT_LOG_MAX = 50;
    private static final int PEER_MESSAGE_MAX_LEN = 500;

    private static final Map<String, String> TASK_STATUS_LABEL = Map.of(
            "PENDING", "待命中",
            "SCHEDULED", "等待中",
            "CHECKING_IN", "執行中",
            "SUCCESS", "成功",
            "FAILED", "失敗",
            "CANCELLED", "已取消"
    );

    private final Map<String, Map<String, Object>> clients = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private ScheduledExecutorService monitor;

    public Map<String, Map<String, Object>> clients() {
        return clients;
    }

    public Map<String, Object> getOrCreateClient(String clientId) {
        return clients.computeIfAbsent(clientId, id -> {
            Map<String, Object> client = new LinkedHashMap<>();
            client.put("clientId", id);
            client.put("status", "ONLINE");
            client.put("tasks", new ArrayList<Map<String, Object>>());
            client.put("lastSeen", Instant.now().toString());
            client.put("message", "");
            client.put("transport", "unknown");
            return client;
        });
    }

    public void setClient(String clientId, Map<String, Object> clientInfo) {
        clients.put(clientId, clientInfo);
    }

    public boolean deleteClient(String clientId) {
        return clients.remove(clientId) != null;
    }

    public void queueClientAction(String clientId, String action) {
        queueClientAction(clientId, action, PENDING_ACTION_TTL_MS);
    }

    public void queueClientAction(String clientId, String action, long ttlMs) {
        Map<String, Object> existing = getOrCreateClient(clientId);
        List<Map<String, Object>> pending = pendingActions(existing);
        if (existing.containsKey("pendingAction")) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("action", existing.get("pendingAction"));
            legacy.put("time", existing.getOrDefault("pendingActionTime", System.currentTimeMillis()));
            pending.add(legacy);
            existing.remove("pendingAction");
            existing.remove("pendingActionTime");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("action", action);
        item.put("time", System.currentTimeMillis());
        if (ttlMs > 0 && ttlMs != PENDING_ACTION_TTL_MS) {
            item.put("ttlMs", ttlMs);
        }
        pending.add(item);
        existing.put("pendingActions", pending);

        if ("CANCEL_SCHEDULE".equals(action)) {
            appendClientEvent(existing, "後台已送出【取消全部任務】指令（等待桌面端下次心跳執行）");
        } else if (action != null && action.startsWith("CANCEL_TASK:")) {
            String taskId = action.substring("CANCEL_TASK:".length());
            String name = findTaskName(existing, taskId);
            appendClientEvent(existing, "後台已送出【取消任務】指令：" + name + "（" + taskId + "）（等待桌面端下次心跳執行）");
        } else if (action != null && action.startsWith("MSG|")) {
            String[] parts = action.split("\\|", 3);
            String fromId = parts.length > 1 ? parts[1] : "未知";
            appendClientEvent(existing, "同事【" + fromId + "】傳來訊息（等待桌面端下次心跳收取）");
        } else if (action != null && action.startsWith("POKE|")) {
            String fromId = action.length() > "POKE|".length() ? action.substring("POKE|".length()) : "未知";
            appendClientEvent(existing, "同事【" + fromId + "】戳了你（等待桌面端下次心跳收取）");
        } else if (action != null && action.startsWith("FILE|")) {
            String[] parts = action.split("\\|", 5);
            String fromId = parts.length > 1 ? parts[1] : "未知";
            String filename = parts.length > 3 ? PeerFileRules.decodeName(parts[3]) : "";
            appendClientEvent(existing, "同事【" + fromId + "】傳來檔案"
                    + (filename.isEmpty() ? "" : "「" + filename + "」")
                    + "（等待桌面端下次心跳收取）");
        }
        clients.put(clientId, existing);
    }

    public PeerResult queuePeerMessage(String toClientId, String fromClientId, String text) {
        return queuePeerMessage(toClientId, fromClientId, text, null);
    }

    public PeerResult queuePeerMessage(String toClientId, String fromClientId, String text, String avatar) {
        String trimmed = text == null ? "" : text.trim();
        if (toClientId == null || toClientId.isEmpty() || fromClientId == null || fromClientId.isEmpty() || trimmed.isEmpty()) {
            return PeerResult.fail("缺少收件人或訊息內容");
        }
        if (toClientId.equals(fromClientId)) {
            return PeerResult.fail("不能發送訊息給自己");
        }
        if (trimmed.length() > PEER_MESSAGE_MAX_LEN) {
            return PeerResult.fail("訊息長度不可超過 " + PEER_MESSAGE_MAX_LEN + " 字");
        }
        Map<String, Object> sender = getOrCreateClient(fromClientId);
        String encodedAvatar = firstNonEmptyAvatar(avatar, sender.get("avatar"));
        if (!encodedAvatar.isEmpty()) {
            sender.put("avatar", encodedAvatar);
        }
        String action = encodePeerMessage(fromClientId, trimmed, encodedAvatar);
        queueClientAction(toClientId, action);
        appendClientEvent(sender, "已傳送訊息給【" + toClientId + "】（等待對方心跳收取）");
        clients.put(fromClientId, sender);
        return PeerResult.ok("訊息已排入佇列，對方約 15 秒內收到");
    }

    public PeerResult queuePeerPoke(String toClientId, String fromClientId) {
        return queuePeerPoke(toClientId, fromClientId, null);
    }

    public PeerResult queuePeerPoke(String toClientId, String fromClientId, String avatar) {
        if (toClientId == null || toClientId.isEmpty() || fromClientId == null || fromClientId.isEmpty()) {
            return PeerResult.fail("缺少收件人或發送者");
        }
        if (toClientId.equals(fromClientId)) {
            return PeerResult.fail("不能戳自己");
        }
        Map<String, Object> sender = getOrCreateClient(fromClientId);
        String encodedAvatar = firstNonEmptyAvatar(avatar, sender.get("avatar"));
        if (!encodedAvatar.isEmpty()) {
            sender.put("avatar", encodedAvatar);
        }
        String action = encodePeerPoke(fromClientId, encodedAvatar);
        queueClientAction(toClientId, action);
        appendClientEvent(sender, "已戳【" + toClientId + "】（等待對方心跳收取）");
        clients.put(fromClientId, sender);
        return PeerResult.ok("戳一下已排入佇列，對方約 15 秒內收到");
    }

    public PeerResult queuePeerFile(String toClientId, String fromClientId, FileOfferStore.Offer offer) {
        if (offer == null || offer.fileId == null || offer.fileId.isEmpty()) {
            return PeerResult.fail("缺少檔案");
        }
        if (toClientId == null || toClientId.isEmpty() || fromClientId == null || fromClientId.isEmpty()) {
            return PeerResult.fail("缺少收件人或發送者");
        }
        if (toClientId.equals(fromClientId)) {
            return PeerResult.fail("不能傳送檔案給自己");
        }
        String action = encodePeerFile(offer);
        queueClientAction(toClientId, action, FileOfferStore.FILE_ACTION_TTL_MS);
        Map<String, Object> sender = getOrCreateClient(fromClientId);
        appendClientEvent(sender, "已傳送檔案「" + offer.filename + "」給【" + toClientId + "】（等待對方心跳收取）");
        clients.put(fromClientId, sender);
        return PeerResult.ok("檔案已排入佇列，對方約 15 秒內收到通知");
    }

    public List<String> drainPendingActions(Map<String, Object> existing) {
        long now = System.currentTimeMillis();
        List<String> actions = new ArrayList<>();
        List<Map<String, Object>> queue = new ArrayList<>(pendingActions(existing));
        if (existing.containsKey("pendingAction")) {
            Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("action", existing.get("pendingAction"));
            legacy.put("time", existing.getOrDefault("pendingActionTime", 0L));
            queue.add(legacy);
        }
        for (Map<String, Object> item : queue) {
            if (item == null || item.get("action") == null) {
                continue;
            }
            long time = toLong(item.get("time"));
            long ttl = PENDING_ACTION_TTL_MS;
            if (item.containsKey("ttlMs")) {
                long custom = toLong(item.get("ttlMs"));
                if (custom > 0) {
                    ttl = custom;
                }
            }
            if (now - time < ttl) {
                actions.add(String.valueOf(item.get("action")));
            }
        }
        existing.remove("pendingActions");
        existing.remove("pendingAction");
        existing.remove("pendingActionTime");
        return actions;
    }

    public List<Map<String, Object>> peerSnapshot(String excludeClientId) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> peers = new ArrayList<>();
        for (Map<String, Object> c : clients.values()) {
            if (c == null || c.get("clientId") == null) {
                continue;
            }
            String clientId = String.valueOf(c.get("clientId"));
            if (clientId.equals(excludeClientId)) {
                continue;
            }
            long lastSeenMs = parseInstantMillis(c.get("lastSeen"));
            boolean offline = lastSeenMs < 0 || (now - lastSeenMs > HEARTBEAT_TIMEOUT_MS);
            List<Map<String, Object>> tasks = tasks(c);
            Map<String, Object> peer = new LinkedHashMap<>();
            peer.put("clientId", clientId);
            peer.put("status", offline ? "OFFLINE" : String.valueOf(c.getOrDefault("status", "ONLINE")));
            peer.put("appVersion", String.valueOf(c.getOrDefault("appVersion", "")));
            peer.put("taskCount", tasks.size());
            peer.put("scheduledCount", tasks.stream().filter(t -> "SCHEDULED".equals(String.valueOf(t.get("status")))).count());
            peer.put("lastSeen", c.get("lastSeen"));
            String avatar = sanitizeAvatar(c.get("avatar"));
            if (!avatar.isEmpty()) {
                peer.put("avatar", avatar);
            }
            peers.add(peer);
        }
        peers.sort(Comparator.comparing(m -> String.valueOf(m.get("clientId"))));
        return peers;
    }

    public List<Map<String, Object>> publicClientsSnapshot() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> client : clients.values()) {
            snapshot.add(sanitizeClientForApi(deepCopy(client)));
        }
        return snapshot;
    }

    public void appendClientEvent(Map<String, Object> client, String text) {
        if (client == null || text == null || text.isEmpty()) {
            return;
        }
        List<Map<String, Object>> eventLog = eventLog(client);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", Instant.now().toString());
        entry.put("text", text);
        eventLog.add(entry);
        if (eventLog.size() > EVENT_LOG_MAX) {
            client.put("eventLog", new ArrayList<>(eventLog.subList(eventLog.size() - EVENT_LOG_MAX, eventLog.size())));
        } else {
            client.put("eventLog", eventLog);
        }
    }

    public void logTaskTransitions(Map<String, Object> existing, List<Map<String, Object>> nextTasks) {
        Map<String, Map<String, Object>> prevById = new LinkedHashMap<>();
        for (Map<String, Object> t : tasks(existing)) {
            if (t != null && t.get("id") != null) {
                prevById.put(String.valueOf(t.get("id")), t);
            }
        }
        for (Map<String, Object> t : nextTasks) {
            if (t == null || t.get("id") == null) {
                continue;
            }
            String name = t.get("name") != null ? String.valueOf(t.get("name")) : String.valueOf(t.get("id"));
            Map<String, Object> prev = prevById.get(String.valueOf(t.get("id")));
            if (prev == null) {
                String when = firstNonEmpty(t.get("actualTime"), t.get("targetTime"));
                appendClientEvent(existing, "任務【" + name + "】上報狀態：" + taskStatusLabel(t.get("status"))
                        + (when.isEmpty() ? "" : "（" + when + "）"));
                continue;
            }
            String prevStatus = String.valueOf(prev.get("status"));
            String nextStatus = String.valueOf(t.get("status"));
            if (!prevStatus.equals(nextStatus)) {
                String detail = t.get("message") != null ? "；原因：" + t.get("message") : "";
                appendClientEvent(existing, "任務【" + name + "】" + taskStatusLabel(prev.get("status"))
                        + " → " + taskStatusLabel(t.get("status")) + detail);
            }
        }
    }

    public List<Map<String, Object>> getTasks(Map<String, Object> client) {
        return tasks(client);
    }

    public void startOfflineMonitor(Consumer<Map<String, Object>> broadcastToDashboards) {
        if (monitor != null) {
            return;
        }
        monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "offline-monitor");
            t.setDaemon(true);
            return t;
        });
        monitor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (Map.Entry<String, Map<String, Object>> entry : clients.entrySet()) {
                Map<String, Object> data = entry.getValue();
                if (!"OFFLINE".equals(String.valueOf(data.get("status")))) {
                    long lastSeenMs = parseInstantMillis(data.get("lastSeen"));
                    if (lastSeenMs >= 0 && now - lastSeenMs > HEARTBEAT_TIMEOUT_MS) {
                        data.put("status", "OFFLINE");
                        data.put("offlineSince", Instant.now().toString());
                        changed = true;
                        System.out.println("[Heartbeat Monitor] 客戶端 " + entry.getKey() + " 已逾時，標記為 離線 (OFFLINE)");
                    }
                }
            }
            if (changed && broadcastToDashboards != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "STATUS_UPDATE");
                payload.put("clients", publicClientsSnapshot());
                broadcastToDashboards.accept(payload);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public static String maskTargetUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            URI parsed = URI.create(url);
            String host = maskHostname(parsed.getHost());
            String port = parsed.getPort() > 0 ? ":" + parsed.getPort() : "";
            return parsed.getScheme() + "://" + host + port + "/***";
        } catch (Exception e) {
            return maskHostname(url) + "/***";
        }
    }

    public Map<String, Object> sanitizeClientForApi(Map<String, Object> client) {
        if (client == null) {
            return client;
        }
        Map<String, Object> copy = deepCopy(client);
        if (copy.containsKey("targetUrl")) {
            copy.put("targetUrl", maskTargetUrl(String.valueOf(copy.get("targetUrl"))));
        }
        copy.remove("avatar");
        if (copy.get("tasks") instanceof List) {
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (Object item : (List<?>) copy.get("tasks")) {
                if (!(item instanceof Map)) {
                    tasks.add((Map<String, Object>) item);
                    continue;
                }
                Map<String, Object> task = deepCopy((Map<String, Object>) item);
                if (task.containsKey("targetUrl")) {
                    task.put("targetUrl", maskTargetUrl(String.valueOf(task.get("targetUrl"))));
                }
                tasks.add(task);
            }
            copy.put("tasks", tasks);
        }
        return copy;
    }

    private static String encodePeerMessage(String fromClientId, String text, String avatar) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String action = "MSG|" + fromClientId + "|" + payload + "|" + System.currentTimeMillis();
        if (avatar != null && !avatar.isEmpty()) {
            action += "|" + avatar;
        }
        return action;
    }

    private static String encodePeerPoke(String fromClientId, String avatar) {
        String action = "POKE|" + fromClientId + "|" + System.currentTimeMillis();
        if (avatar != null && !avatar.isEmpty()) {
            action += "|" + avatar;
        }
        return action;
    }

    static String encodePeerFile(FileOfferStore.Offer offer) {
        return "FILE|" + offer.fromClientId
                + "|" + offer.fileId
                + "|" + PeerFileRules.encodeName(offer.filename)
                + "|" + offer.size()
                + "|" + offer.mime
                + "|" + System.currentTimeMillis();
    }

    public static String sanitizeAvatar(Object raw) {
        if (raw == null) {
            return "";
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || value.length() > 60_000) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return "";
            }
        }
        return value;
    }

    private static String firstNonEmptyAvatar(String preferred, Object stored) {
        String fromRequest = sanitizeAvatar(preferred);
        if (!fromRequest.isEmpty()) {
            return fromRequest;
        }
        return sanitizeAvatar(stored);
    }

    private static String findTaskName(Map<String, Object> client, String taskId) {
        for (Map<String, Object> t : tasks(client)) {
            if (t != null && taskId.equals(String.valueOf(t.get("id")))) {
                return t.get("name") != null ? String.valueOf(t.get("name")) : taskId;
            }
        }
        return taskId;
    }

    private static String taskStatusLabel(Object status) {
        if (status == null) {
            return "未知";
        }
        return TASK_STATUS_LABEL.getOrDefault(String.valueOf(status), String.valueOf(status));
    }

    private static List<Map<String, Object>> pendingActions(Map<String, Object> client) {
        Object value = client.get("pendingActions");
        if (value instanceof List) {
            return new ArrayList<>((List<Map<String, Object>>) value);
        }
        return new ArrayList<>();
    }

    private static List<Map<String, Object>> tasks(Map<String, Object> client) {
        Object value = client.get("tasks");
        if (value instanceof List) {
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    tasks.add((Map<String, Object>) item);
                }
            }
            return tasks;
        }
        return new ArrayList<>();
    }

    private static List<Map<String, Object>> eventLog(Map<String, Object> client) {
        Object value = client.get("eventLog");
        if (value instanceof List) {
            return new ArrayList<>((List<Map<String, Object>>) value);
        }
        return new ArrayList<>();
    }

    private static long parseInstantMillis(Object value) {
        if (value == null) {
            return -1;
        }
        try {
            return Instant.parse(String.valueOf(value)).toEpochMilli();
        } catch (Exception e) {
            return -1;
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String firstNonEmpty(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isEmpty()) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private static String maskHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return "***";
        }
        String[] parts = hostname.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            boolean isTld = i == parts.length - 1 && parts.length > 1;
            sb.append(isTld ? parts[i] : maskLabel(parts[i]));
        }
        return sb.toString();
    }

    private static String maskLabel(String label) {
        if (label == null || label.isEmpty()) {
            return "***";
        }
        if (label.length() == 1) {
            return "*";
        }
        if (label.length() == 2) {
            return label.charAt(0) + "*";
        }
        return label.substring(0, 2) + "***";
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        return gson.fromJson(gson.toJson(source), Map.class);
    }

    public static final class PeerResult {
        public final boolean ok;
        public final String message;

        private PeerResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static PeerResult ok(String message) {
            return new PeerResult(true, message);
        }

        public static PeerResult fail(String message) {
            return new PeerResult(false, message);
        }
    }
}
