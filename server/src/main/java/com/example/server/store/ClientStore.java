package com.example.server.store;

import com.example.ChatProtocol;
import com.example.PeerFileRules;
import com.google.gson.Gson;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class ClientStore {

    public static final long HEARTBEAT_TIMEOUT_MS = 3L * 60L * 1000L;
    public static final long PENDING_ACTION_TTL_MS = 30_000L;
    private static final int EVENT_LOG_MAX = 50;
    private static final int PEER_MESSAGE_MAX_LEN = 10_000;
    /** 一則訊息全部附圖的 Base64 字元上限（含分隔逗號），約 0.9 MB 的 JPEG */
    public static final int PEER_IMAGE_MAX_CHARS = ChatProtocol.MAX_PEER_IMAGE_CHARS;

    private static final Map<String, String> TASK_STATUS_LABEL = Map.of(
            "PENDING", "待命中",
            "SCHEDULED", "等待中",
            "CHECKING_IN", "執行中",
            "SUCCESS", "成功",
            "FAILED", "失敗",
            "CANCELLED", "已取消"
    );

    private final Map<String, Map<String, Object>> clients = new ConcurrentHashMap<>();
    /**
     * 每台 client 的值是一般 LinkedHashMap，讀寫（含 Dashboard 快照序列化）都要持有這把鎖。
     * 持鎖期間不可廣播或做 I/O；全站只有這一把，不會互鎖。
     */
    private final Object metadataLock = new Object();
    /** 待送指令與 client 資料分開放，心跳覆寫 client 資料時不會帶走剛排入的指令。key 為正規化後的 clientId。 */
    private final Map<String, Deque<PendingAction>> pendingQueues = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private ScheduledExecutorService monitor;

    public Map<String, Map<String, Object>> clients() {
        return clients;
    }

    /** 在 client 資料鎖內執行；呼叫端不可在裡面廣播或做網路 I/O。 */
    public <T> T withMetadataLock(Supplier<T> work) {
        synchronized (metadataLock) {
            return work.get();
        }
    }

    public Map<String, Object> getOrCreateClient(String clientId) {
        String id = PeerFileRules.normalizeClientId(clientId);
        return clients.computeIfAbsent(id, key -> {
            Map<String, Object> client = new LinkedHashMap<>();
            client.put("clientId", key);
            client.put("status", "ONLINE");
            client.put("tasks", new ArrayList<Map<String, Object>>());
            client.put("lastSeen", Instant.now().toString());
            client.put("message", "");
            client.put("transport", "unknown");
            return client;
        });
    }

    public void setClient(String clientId, Map<String, Object> clientInfo) {
        String id = PeerFileRules.normalizeClientId(clientId);
        synchronized (metadataLock) {
            if (clientInfo != null) {
                clientInfo.put("clientId", id);
            }
            clients.put(id, clientInfo);
        }
    }

    public boolean deleteClient(String clientId) {
        String id = PeerFileRules.normalizeClientId(clientId);
        synchronized (metadataLock) {
            pendingQueues.remove(id);
            return clients.remove(id) != null;
        }
    }

    public void queueClientAction(String clientId, String action) {
        queueClientAction(clientId, action, PENDING_ACTION_TTL_MS);
    }

    public void queueClientAction(String clientId, String action, long ttlMs) {
        queueClientAction(clientId, action, ttlMs, System.currentTimeMillis());
    }

    void queueClientAction(String clientId, String action, long ttlMs, long queuedAtMs) {
        String id = PeerFileRules.normalizeClientId(clientId);
        synchronized (metadataLock) {
            Map<String, Object> existing = getOrCreateClient(id);
            pendingQueues.computeIfAbsent(id, key -> new ConcurrentLinkedDeque<>())
                    .addLast(new PendingAction(action, queuedAtMs, ttlMs > 0 ? ttlMs : PENDING_ACTION_TTL_MS));
            logQueuedAction(existing, action);
        }
    }

    private void logQueuedAction(Map<String, Object> existing, String action) {
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
        } else if (action != null && action.startsWith("MSGI|")) {
            String[] parts = action.split("\\|", 3);
            String fromId = parts.length > 1 ? parts[1] : "未知";
            appendClientEvent(existing, "同事【" + fromId + "】傳來圖片訊息（等待桌面端下次心跳收取）");
        } else if (action != null && action.startsWith("POKE|")) {
            String fromId = action.length() > "POKE|".length() ? action.substring("POKE|".length()) : "未知";
            appendClientEvent(existing, "同事【" + fromId + "】戳了你（等待桌面端下次心跳收取）");
        } else if (action != null && action.startsWith("RELAY|")) {
            String[] parts = action.split("\\|", -1);
            String fromId = parts.length > 1 ? PeerFileRules.decodeName(parts[1]) : "未知";
            String filename = parts.length > 3 ? PeerFileRules.decodeName(parts[3]) : "";
            appendClientEvent(existing, "同事【" + fromId + "】要直傳檔案"
                    + (filename.isEmpty() ? "" : "「" + filename + "」")
                    + "（等待桌面端接收，伺服器不暫存）");
        }
    }

    public PeerResult queuePeerMessage(String toClientId, String fromClientId, String text) {
        return queuePeerMessage(toClientId, fromClientId, text, null);
    }

    public PeerResult queuePeerMessage(String toClientId, String fromClientId, String text, String avatar) {
        return queuePeerMessage(toClientId, fromClientId, text, avatar, null);
    }

    /**
     * @param image Base64 URL 編碼的 JPEG；多張以逗號串起。有圖時文字可為空
     */
    public PeerResult queuePeerMessage(
            String toClientId, String fromClientId, String text, String avatar, String image) {
        String trimmed = text == null ? "" : text.trim();
        String cleanImage = sanitizeImage(image);
        boolean imageRejected = image != null && !image.trim().isEmpty() && cleanImage.isEmpty();
        if (imageRejected) {
            return PeerResult.fail("圖片格式不正確或超過大小上限");
        }
        if (toClientId == null || toClientId.isEmpty() || fromClientId == null || fromClientId.isEmpty()
                || (trimmed.isEmpty() && cleanImage.isEmpty())) {
            return PeerResult.fail("缺少收件人或訊息內容");
        }
        if (toClientId.equals(fromClientId)) {
            return PeerResult.fail("不能發送訊息給自己");
        }
        if (trimmed.length() > PEER_MESSAGE_MAX_LEN) {
            return PeerResult.fail("訊息長度不可超過 " + PEER_MESSAGE_MAX_LEN + " 字");
        }
        synchronized (metadataLock) {
            Map<String, Object> sender = getOrCreateClient(fromClientId);
            String encodedAvatar = firstNonEmptyAvatar(avatar, sender.get("avatar"));
            if (!encodedAvatar.isEmpty()) {
                sender.put("avatar", encodedAvatar);
            }
            String action = cleanImage.isEmpty()
                    ? encodePeerMessage(fromClientId, trimmed, encodedAvatar)
                    : encodePeerImageMessage(fromClientId, trimmed, encodedAvatar, cleanImage);
            queueClientAction(toClientId, action);
            appendClientEvent(sender, (cleanImage.isEmpty() ? "已傳送訊息給【" : "已傳送圖片訊息給【")
                    + toClientId + "】（等待對方心跳收取）");
        }
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
        synchronized (metadataLock) {
            Map<String, Object> sender = getOrCreateClient(fromClientId);
            String encodedAvatar = firstNonEmptyAvatar(avatar, sender.get("avatar"));
            if (!encodedAvatar.isEmpty()) {
                sender.put("avatar", encodedAvatar);
            }
            String action = encodePeerPoke(fromClientId, encodedAvatar);
            queueClientAction(toClientId, action);
            appendClientEvent(sender, "已戳【" + toClientId + "】（等待對方心跳收取）");
        }
        return PeerResult.ok("戳一下已排入佇列，對方約 15 秒內收到");
    }

    /** 通知收件人有一筆即時直傳等待接收（檔案本身不經伺服器暫存）。 */
    public PeerResult queuePeerRelay(String toClientId, String fromClientId, String relayId,
                                     String filename, long size, String kind) {
        if (relayId == null || relayId.isEmpty()) {
            return PeerResult.fail("缺少直傳編號");
        }
        if (toClientId == null || toClientId.isEmpty() || fromClientId == null || fromClientId.isEmpty()) {
            return PeerResult.fail("缺少收件人或發送者");
        }
        if (toClientId.equals(fromClientId)) {
            return PeerResult.fail("不能傳送檔案給自己");
        }
        String action = encodePeerRelay(fromClientId, relayId, filename, size, kind);
        queueClientAction(toClientId, action, PeerFileRules.RELAY_ACCEPT_TIMEOUT_MS);
        appendClientEvent(fromClientId, "要直傳檔案「" + filename + "」給【" + toClientId + "】（等待對方接收）");
        return PeerResult.ok("已通知對方，約 15 秒內會跳出接收提示");
    }

    /** 最近 withinMs 內是否有心跳；直傳只對剛剛還在線的收件人發起，避免傳送端空等。 */
    public boolean isRecentlySeen(String clientId, long withinMs) {
        Object lastSeen;
        synchronized (metadataLock) {
            Map<String, Object> client = clients.get(PeerFileRules.normalizeClientId(clientId));
            if (client == null) {
                return false;
            }
            lastSeen = client.get("lastSeen");
        }
        long lastSeenMs = parseInstantMillis(lastSeen);
        return lastSeenMs >= 0 && System.currentTimeMillis() - lastSeenMs <= withinMs;
    }

    /** 桌面端在心跳 capabilities[] 宣告的功能（舊版不送，視為都不支援）。 */
    public boolean hasCapability(String clientId, String capability) {
        Object value;
        synchronized (metadataLock) {
            Map<String, Object> client = clients.get(PeerFileRules.normalizeClientId(clientId));
            if (client == null || capability == null) {
                return false;
            }
            value = client.get("capabilities");
        }
        if (!(value instanceof List)) {
            return false;
        }
        for (Object item : (List<?>) value) {
            if (item != null && capability.equals(String.valueOf(item))) {
                return true;
            }
        }
        return false;
    }

    static List<String> sanitizeCapabilities(Object raw) {
        List<String> list = new ArrayList<>();
        if (!(raw instanceof List)) {
            return list;
        }
        for (Object item : (List<?>) raw) {
            if (item == null) {
                continue;
            }
            String value = String.valueOf(item).trim();
            if (!value.isEmpty() && value.length() <= 32 && !list.contains(value) && list.size() < 16) {
                list.add(value);
            }
        }
        return list;
    }

    /** 心跳帶 capabilities[] 就覆蓋；沒帶（舊版桌面端）就清掉，避免降版後仍被當成支援。 */
    public static void applyCapabilities(Map<String, Object> clientInfo, Map<String, Object> heartbeatBody) {
        if (clientInfo == null) {
            return;
        }
        List<String> caps = heartbeatBody != null ? sanitizeCapabilities(heartbeatBody.get("capabilities")) : List.of();
        if (caps.isEmpty()) {
            clientInfo.remove("capabilities");
        } else {
            clientInfo.put("capabilities", caps);
        }
    }

    public List<String> drainPendingActions(Map<String, Object> existing) {
        if (existing == null) {
            return new ArrayList<>();
        }
        Object clientId;
        synchronized (metadataLock) {
            clientId = existing.get("clientId");
        }
        return drainPendingActions(clientId == null ? "" : String.valueOf(clientId));
    }

    /**
     * 依排入順序取走並移除這台 client 的待送指令，逾時的直接丟掉。
     * 與同時排入的指令不會互相覆蓋：沒被這次取到的會留給下一次心跳。
     * 取走即移除，伺服器不知道回應有沒有真的送到桌面端（至多一次）。
     */
    public List<String> drainPendingActions(String clientId) {
        String id = PeerFileRules.normalizeClientId(clientId);
        long now = System.currentTimeMillis();
        List<String> actions = new ArrayList<>();
        Deque<PendingAction> queue = pendingQueues.get(id);
        if (queue != null) {
            PendingAction item;
            while ((item = queue.pollFirst()) != null) {
                if (item.action != null && now - item.queuedAtMs < item.ttlMs) {
                    actions.add(item.action);
                }
            }
        }
        synchronized (metadataLock) {
            Map<String, Object> existing = clients.get(id);
            if (existing != null && existing.containsKey("pendingAction")) {
                long time = toLong(existing.getOrDefault("pendingActionTime", 0L));
                if (now - time < PENDING_ACTION_TTL_MS) {
                    actions.add(String.valueOf(existing.get("pendingAction")));
                }
            }
            if (existing != null) {
                existing.remove("pendingActions");
                existing.remove("pendingAction");
                existing.remove("pendingActionTime");
            }
        }
        return actions;
    }

    public List<Map<String, Object>> peerSnapshot(String excludeClientId) {
        synchronized (metadataLock) {
            return peerSnapshotLocked(excludeClientId);
        }
    }

    private List<Map<String, Object>> peerSnapshotLocked(String excludeClientId) {
        long now = System.currentTimeMillis();
        String exclude = PeerFileRules.normalizeClientId(excludeClientId);
        List<Map<String, Object>> peers = new ArrayList<>();
        for (Map<String, Object> c : clients.values()) {
            if (c == null || c.get("clientId") == null) {
                continue;
            }
            String clientId = String.valueOf(c.get("clientId"));
            if (clientId.equals(exclude)) {
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
        List<Map<String, Object>> copies = new ArrayList<>();
        synchronized (metadataLock) {
            for (Map<String, Object> client : clients.values()) {
                // 大頭照最多數十 KB 且 sanitize 會丟掉，不在鎖內序列化
                Map<String, Object> shallow = new LinkedHashMap<>(client);
                shallow.remove("avatar");
                copies.add(deepCopy(shallow));
            }
        }
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> copy : copies) {
            snapshot.add(sanitizeClientForApi(copy));
        }
        return snapshot;
    }

    public void appendClientEvent(Map<String, Object> client, String text) {
        if (client == null || text == null || text.isEmpty()) {
            return;
        }
        synchronized (metadataLock) {
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
    }

    /** 寫進目前登記中的那份 client 資料（心跳可能剛換過 Map）。 */
    public void appendClientEvent(String clientId, String text) {
        synchronized (metadataLock) {
            appendClientEvent(getOrCreateClient(clientId), text);
        }
    }

    public void logTaskTransitions(Map<String, Object> existing, List<Map<String, Object>> nextTasks) {
        synchronized (metadataLock) {
            logTaskTransitionsLocked(existing, nextTasks);
        }
    }

    private void logTaskTransitionsLocked(Map<String, Object> existing, List<Map<String, Object>> nextTasks) {
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
            String name = displayTaskName(t.get("name") != null ? t.get("name") : t.get("id"));
            Map<String, Object> prev = prevById.get(String.valueOf(t.get("id")));
            if (prev == null) {
                String when = firstNonEmpty(t.get("actualTime"), t.get("targetTime"));
                appendClientEvent(existing, "任務【" + name + "】上報狀態：" + taskStatusLabel(t.get("status"))
                        + (when.isEmpty() ? "" : "（" + when + "）"));
                logLastResultIfChanged(existing, null, t, name);
                continue;
            }
            String prevStatus = String.valueOf(prev.get("status"));
            String nextStatus = String.valueOf(t.get("status"));
            if (!prevStatus.equals(nextStatus)) {
                String detail = t.get("message") != null ? "；原因：" + t.get("message") : "";
                appendClientEvent(existing, "任務【" + name + "】" + taskStatusLabel(prev.get("status"))
                        + " → " + taskStatusLabel(t.get("status")) + detail);
            }
            logLastResultIfChanged(existing, prev, t, name);
        }
    }

    /**
     * 槽位重排後 status 會變回等待中，打卡結果改看 lastResult。
     * 內容沒變就不記，避免每 15 秒心跳重複刷事件。
     */
    private void logLastResultIfChanged(
            Map<String, Object> existing,
            Map<String, Object> prev,
            Map<String, Object> next,
            String name) {
        String lastStatus = firstNonEmpty(next.get("lastResultStatus"));
        if (!"SUCCESS".equals(lastStatus) && !"FAILED".equals(lastStatus)) {
            return;
        }
        String lastMessage = next.get("lastResultMessage") != null
                ? String.valueOf(next.get("lastResultMessage")) : "";
        String prevKey = "";
        if (prev != null) {
            prevKey = firstNonEmpty(prev.get("lastResultStatus")) + "\0"
                    + (prev.get("lastResultMessage") != null ? String.valueOf(prev.get("lastResultMessage")) : "");
        }
        String nextKey = lastStatus + "\0" + lastMessage;
        if (prevKey.equals(nextKey)) {
            return;
        }
        if (lastStatus.equals(String.valueOf(next.get("status")))) {
            return;
        }
        String detail = lastMessage.isEmpty() ? "" : "；原因：" + lastMessage;
        appendClientEvent(existing, "任務【" + name + "】回報執行結果：" + taskStatusLabel(lastStatus) + detail);
        String clientId = String.valueOf(existing.getOrDefault("clientId", ""));
        System.out.println("[Task Report] 設備 " + clientId + " 上報執行結果 (" + lastStatus + "): " + lastMessage);
    }

    public List<Map<String, Object>> getTasks(Map<String, Object> client) {
        synchronized (metadataLock) {
            return tasks(client);
        }
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
            synchronized (metadataLock) {
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
        // 待送指令可能夾帶大頭照／訊息圖片，不要推給 Dashboard
        copy.remove("pendingActions");
        copy.remove("pendingAction");
        copy.remove("pendingActionTime");
        if (copy.get("tasks") instanceof List) {
            copy.put("tasks", sanitizeTaskList(copy.get("tasks")));
        }
        return copy;
    }

    private List<Map<String, Object>> sanitizeTaskList(Object raw) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        if (!(raw instanceof List)) {
            return tasks;
        }
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> task = deepCopy((Map<String, Object>) item);
            if (task.containsKey("targetUrl")) {
                task.put("targetUrl", maskTargetUrl(String.valueOf(task.get("targetUrl"))));
            }
            task.put("name", displayTaskName(task.get("name")));
            tasks.add(task);
        }
        return tasks;
    }

    private static String encodePeerMessage(String fromClientId, String text, String avatar) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        String action = "MSG|" + fromClientId + "|" + payload + "|" + System.currentTimeMillis();
        if (avatar != null && !avatar.isEmpty()) {
            action += "|" + avatar;
        }
        return action;
    }

    /** MSGI|fromId|base64text|epochMs|avatar|image（avatar 可為空；text 可為空） */
    private static String encodePeerImageMessage(String fromClientId, String text, String avatar, String image) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return "MSGI|" + fromClientId + "|" + payload + "|" + System.currentTimeMillis()
                + "|" + (avatar == null ? "" : avatar) + "|" + image;
    }

    /** 一則訊息的圖片欄位：單張或多張（逗號分隔）皆可。不合格回傳空字串。 */
    public static String sanitizeImage(Object raw) {
        if (raw == null) {
            return "";
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || value.length() > PEER_IMAGE_MAX_CHARS) {
            return "";
        }
        if (ChatProtocol.splitPeerImages(value) == null) {
            return "";
        }
        return value;
    }

    private static String encodePeerPoke(String fromClientId, String avatar) {
        String action = "POKE|" + fromClientId + "|" + System.currentTimeMillis();
        if (avatar != null && !avatar.isEmpty()) {
            action += "|" + avatar;
        }
        return action;
    }

    /** RELAY|base64fromId|relayId|base64name|size|kind|epochMs（fromId 編碼後不會含 |） */
    static String encodePeerRelay(String fromClientId, String relayId, String filename, long size, String kind) {
        return "RELAY|" + PeerFileRules.encodeName(fromClientId)
                + "|" + relayId
                + "|" + PeerFileRules.encodeName(filename)
                + "|" + size
                + "|" + PeerFileRules.normalizeKind(kind)
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

    /** 心跳上報的任務名稱：去掉換行、最長 24 字，空白時給後台一個可讀名稱。 */
    public static void normalizeReportedTaskNames(List<Map<String, Object>> tasks) {
        if (tasks == null) {
            return;
        }
        for (Map<String, Object> task : tasks) {
            if (task == null) {
                continue;
            }
            task.put("name", displayTaskName(task.get("name")));
        }
    }

    public static String displayTaskName(Object raw) {
        String name = raw == null ? "" : String.valueOf(raw).replace('\r', ' ').replace('\n', ' ').trim();
        if (name.length() > 24) {
            name = name.substring(0, 24).trim();
        }
        return name.isEmpty() ? "未命名任務" : name;
    }

    private static String findTaskName(Map<String, Object> client, String taskId) {
        for (Map<String, Object> t : tasks(client)) {
            if (t != null && taskId.equals(String.valueOf(t.get("id")))) {
                return displayTaskName(t.get("name") != null ? t.get("name") : taskId);
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

    private static final class PendingAction {
        final String action;
        final long queuedAtMs;
        final long ttlMs;

        PendingAction(String action, long queuedAtMs, long ttlMs) {
            this.action = action;
            this.queuedAtMs = queuedAtMs;
            this.ttlMs = ttlMs;
        }
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
