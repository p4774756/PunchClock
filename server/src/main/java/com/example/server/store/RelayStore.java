package com.example.server.store;

import com.example.PeerFileRules;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 同事即時直傳：雙方同時在線，檔案分段只在記憶體轉手（每筆最多 {@link PeerFileRules#RELAY_WINDOW_CHUNKS} 段），不寫磁碟。
 * 接收端索取下一個位移時，視同確認前面的分段，伺服器隨即釋放。
 */
public final class RelayStore {

    public static final int MAX_ACTIVE = 8;
    /** 結束後只保留中繼資料一段時間，讓雙方查得到結果。 */
    public static final long FINISHED_RETENTION_MS = 10L * 60L * 1000L;
    /** 單段上限稍寬於桌面端分段大小，容許未來調整。 */
    public static final int MAX_CHUNK_BYTES = PeerFileRules.RELAY_CHUNK_BYTES * 2;

    public enum State {
        WAITING, ACCEPTED, COMPLETED, DECLINED, CANCELLED, EXPIRED, FAILED;

        public boolean terminal() {
            return this != WAITING && this != ACCEPTED;
        }
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public RelayStore() {
        this(System::currentTimeMillis);
    }

    RelayStore(LongSupplier clock) {
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    public Result begin(String fromClientId, String toClientId, String rawFilename, String kind, long size) {
        purgeExpired();
        String from = PeerFileRules.normalizeClientId(fromClientId);
        String to = PeerFileRules.normalizeClientId(toClientId);
        String filename = PeerFileRules.sanitizeFilename(rawFilename);
        if (from.isEmpty() || to.isEmpty()) {
            return Result.failed("缺少收件人或發送者");
        }
        if (from.equals(to)) {
            return Result.failed("不能傳送檔案給自己");
        }
        if (filename.isEmpty() || !PeerFileRules.isAllowedFilename(filename)) {
            return Result.failed("檔名無效");
        }
        if (size <= 0) {
            return Result.failed("檔案不可為空");
        }
        if (size > PeerFileRules.MAX_BYTES) {
            return Result.failed("檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
        }
        synchronized (sessions) {
            if (activeCount() >= MAX_ACTIVE) {
                return Result.busy("伺服器同時進行的直傳已達上限，請稍後再試");
            }
            String relayId = UUID.randomUUID().toString().replace("-", "");
            Session session = new Session(relayId, from, to, filename,
                    PeerFileRules.normalizeKind(kind), size, clock.getAsLong());
            sessions.put(relayId, session);
            return Result.ok(session);
        }
    }

    /** 查狀態；sinceVersion 之後沒有變化時最多等 waitMs（長輪詢）。 */
    public Result await(String relayId, String requesterClientId, long sinceVersion, long waitMs) {
        Lookup lookup = find(relayId, requesterClientId, Role.EITHER);
        if (lookup.error != null) {
            return lookup.error;
        }
        Session s = lookup.session;
        long deadline = System.nanoTime() + Math.max(0L, waitMs) * 1_000_000L;
        synchronized (s) {
            s.touch(lookup.role, clock.getAsLong());
            while (true) {
                expireIfDue(s);
                if (s.version > sinceVersion || s.state.terminal()) {
                    break;
                }
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0) {
                    break;
                }
                waitQuietly(s, Math.min(remainingMs, 1_000L));
            }
            s.touch(lookup.role, clock.getAsLong());
            return Result.ok(s);
        }
    }

    public Result accept(String relayId, String requesterClientId) {
        Lookup lookup = find(relayId, requesterClientId, Role.RECIPIENT);
        if (lookup.error != null) {
            return lookup.error;
        }
        Session s = lookup.session;
        synchronized (s) {
            expireIfDue(s);
            if (s.state.terminal()) {
                return Result.gone(s);
            }
            s.touch(Role.RECIPIENT, clock.getAsLong());
            if (s.state == State.WAITING) {
                s.state = State.ACCEPTED;
                s.message = "對方已接收，直傳中";
                s.senderSeenMs = clock.getAsLong();
                s.changed();
            }
            return Result.ok(s);
        }
    }

    /** 傳送端送入 offset 起的一段；視窗已滿或位移不符時回 CONFLICT。 */
    public Result push(String relayId, String requesterClientId, long offset, InputStream content) {
        Lookup lookup = find(relayId, requesterClientId, Role.SENDER);
        if (lookup.error != null) {
            return lookup.error;
        }
        Session s = lookup.session;
        long limit;
        synchronized (s) {
            expireIfDue(s);
            if (s.state.terminal()) {
                return Result.gone(s);
            }
            s.touch(Role.SENDER, clock.getAsLong());
            if (s.state != State.ACCEPTED) {
                return Result.conflict(s, "對方尚未接收");
            }
            if (offset != s.pushed) {
                return Result.conflict(s, "位移不符，伺服器目前已收到 " + s.pushed + " bytes");
            }
            if (s.pushInFlight || s.buffer.size() >= PeerFileRules.RELAY_WINDOW_CHUNKS) {
                return Result.conflict(s, "對方還沒取走前面的分段");
            }
            if (content == null) {
                return Result.failed("分段內容為空");
            }
            limit = Math.min(MAX_CHUNK_BYTES, s.size - s.pushed);
            s.pushInFlight = true;
        }

        byte[] data;
        try {
            data = readLimited(content, limit);
        } catch (IOException ex) {
            synchronized (s) {
                s.pushInFlight = false;
                s.touch(Role.SENDER, clock.getAsLong());
                s.changed();
            }
            return Result.failed(ex instanceof ChunkTooLargeException
                    ? "分段超過上限或超出宣告大小"
                    : "分段中斷：" + (ex.getMessage() == null ? "IO 錯誤" : ex.getMessage()));
        }

        synchronized (s) {
            s.pushInFlight = false;
            s.touch(Role.SENDER, clock.getAsLong());
            if (s.state.terminal()) {
                s.changed();
                return Result.gone(s);
            }
            if (data.length == 0) {
                s.changed();
                return Result.failed("分段內容為空");
            }
            s.buffer.addLast(new Chunk(s.pushed, data));
            s.pushed += data.length;
            s.changed();
            return Result.ok(s);
        }
    }

    /**
     * 接收端取 offset 起的資料；offset 之前的分段視為已收到並釋放。
     * 還沒有資料時最多等 waitMs，逾時回 {@link ChunkResult.Status#NO_DATA}。
     */
    public ChunkResult take(String relayId, String requesterClientId, long offset, long waitMs) {
        Lookup lookup = find(relayId, requesterClientId, Role.RECIPIENT);
        if (lookup.error != null) {
            return ChunkResult.of(lookup.error);
        }
        Session s = lookup.session;
        long deadline = System.nanoTime() + Math.max(0L, waitMs) * 1_000_000L;
        synchronized (s) {
            s.touch(Role.RECIPIENT, clock.getAsLong());
            while (true) {
                expireIfDue(s);
                if (s.state.terminal()) {
                    return ChunkResult.of(Result.gone(s));
                }
                if (s.state != State.ACCEPTED) {
                    return ChunkResult.of(Result.conflict(s, "尚未接收"));
                }
                if (offset < s.acked || offset > s.size) {
                    return ChunkResult.of(Result.conflict(s, "位移 " + offset + " 已無法取得（伺服器已釋放）"));
                }
                if (release(s, offset)) {
                    s.changed();
                }
                for (Chunk chunk : s.buffer) {
                    if (offset >= chunk.offset && offset < chunk.end()) {
                        byte[] slice = offset == chunk.offset
                                ? chunk.data
                                : Arrays.copyOfRange(chunk.data, (int) (offset - chunk.offset), chunk.data.length);
                        s.touch(Role.RECIPIENT, clock.getAsLong());
                        return ChunkResult.data(s, offset, slice);
                    }
                }
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0) {
                    s.touch(Role.RECIPIENT, clock.getAsLong());
                    return ChunkResult.noData(s);
                }
                waitQuietly(s, Math.min(remainingMs, 1_000L));
            }
        }
    }

    /** 接收端已寫完全部內容。 */
    public Result complete(String relayId, String requesterClientId) {
        Lookup lookup = find(relayId, requesterClientId, Role.RECIPIENT);
        if (lookup.error != null) {
            return lookup.error;
        }
        Session s = lookup.session;
        synchronized (s) {
            expireIfDue(s);
            if (s.state == State.COMPLETED) {
                return Result.ok(s);
            }
            if (s.state.terminal()) {
                return Result.gone(s);
            }
            if (s.state != State.ACCEPTED || s.pushed != s.size) {
                return Result.conflict(s, "檔案尚未傳完（"
                        + PeerFileRules.formatSize(s.pushed) + " / " + PeerFileRules.formatSize(s.size) + "）");
            }
            s.acked = s.size;
            finish(s, State.COMPLETED, "對方已收到");
            return Result.ok(s);
        }
    }

    /** 任一方中止；收件人在接收前呼叫視為拒收。重複呼叫回目前狀態。 */
    public Result cancel(String relayId, String requesterClientId) {
        Lookup lookup = find(relayId, requesterClientId, Role.EITHER);
        if (lookup.error != null) {
            return lookup.error;
        }
        Session s = lookup.session;
        synchronized (s) {
            expireIfDue(s);
            if (s.state.terminal()) {
                return Result.ok(s);
            }
            if (lookup.role == Role.RECIPIENT && s.state == State.WAITING) {
                finish(s, State.DECLINED, "對方拒絕接收");
            } else if (lookup.role == Role.RECIPIENT) {
                finish(s, State.CANCELLED, "對方已取消接收");
            } else {
                finish(s, State.CANCELLED, "傳送端已取消");
            }
            return Result.ok(s);
        }
    }

    public int activeCount() {
        int count = 0;
        for (Session s : sessions.values()) {
            if (s != null && !s.state.terminal()) {
                count++;
            }
        }
        return count;
    }

    public long bufferedBytes() {
        long total = 0L;
        for (Session s : sessions.values()) {
            if (s == null) {
                continue;
            }
            synchronized (s) {
                for (Chunk chunk : s.buffer) {
                    total += chunk.data.length;
                }
            }
        }
        return total;
    }

    public List<Map<String, Object>> publicSnapshot() {
        purgeExpired();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (s != null) {
                synchronized (s) {
                    list.add(toMap(s, Role.EITHER));
                }
            }
        }
        list.sort((a, b) -> Long.compare(
                ((Number) b.get("createdAtMs")).longValue(),
                ((Number) a.get("createdAtMs")).longValue()));
        return list;
    }

    void purgeExpired() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            if (s == null) {
                it.remove();
                continue;
            }
            synchronized (s) {
                expireIfDue(s);
                if (s.state.terminal() && now - s.endedAtMs > FINISHED_RETENTION_MS) {
                    it.remove();
                }
            }
        }
    }

    private void expireIfDue(Session s) {
        if (s.state.terminal()) {
            return;
        }
        long now = clock.getAsLong();
        if (s.state == State.WAITING && now - s.createdAtMs > PeerFileRules.RELAY_ACCEPT_TIMEOUT_MS) {
            finish(s, State.EXPIRED, "對方未在 " + PeerFileRules.RELAY_ACCEPT_TIMEOUT_LABEL + " 內接收");
        } else if (s.state == State.ACCEPTED && !s.pushInFlight
                && (now - s.senderSeenMs > PeerFileRules.RELAY_IDLE_TIMEOUT_MS
                || now - s.recipientSeenMs > PeerFileRules.RELAY_IDLE_TIMEOUT_MS)) {
            boolean senderGone = now - s.senderSeenMs > PeerFileRules.RELAY_IDLE_TIMEOUT_MS;
            finish(s, State.FAILED, (senderGone ? "傳送端" : "接收端") + "連線中斷太久，已中止直傳");
        }
    }

    private void finish(Session s, State state, String message) {
        s.state = state;
        s.message = message;
        s.buffer.clear();
        s.endedAtMs = clock.getAsLong();
        s.changed();
    }

    /** 釋放結束位置不超過 offset 的分段；有釋放時回 true。 */
    private static boolean release(Session s, long offset) {
        boolean released = false;
        while (!s.buffer.isEmpty() && s.buffer.peekFirst().end() <= offset) {
            s.buffer.pollFirst();
            released = true;
        }
        long acked = Math.min(offset, s.pushed);
        if (acked > s.acked) {
            s.acked = acked;
            released = true;
        }
        return released;
    }

    private Lookup find(String relayId, String requesterClientId, Role required) {
        purgeExpired();
        String id = relayId == null ? "" : relayId.trim();
        Session s = id.isEmpty() ? null : sessions.get(id);
        if (s == null) {
            return Lookup.error(Result.notFound("直傳不存在或已結束"));
        }
        String requester = PeerFileRules.normalizeClientId(requesterClientId);
        Role role;
        if (!requester.isEmpty() && requester.equals(s.fromClientId)) {
            role = Role.SENDER;
        } else if (!requester.isEmpty() && requester.equals(s.toClientId)) {
            role = Role.RECIPIENT;
        } else {
            return Lookup.error(Result.forbidden("無權操作此直傳"));
        }
        if (required != Role.EITHER && required != role) {
            return Lookup.error(Result.forbidden(required == Role.SENDER ? "只有傳送端可以上傳" : "只有收件人可以操作"));
        }
        return Lookup.ok(s, role);
    }

    private static byte[] readLimited(InputStream in, long limit) throws IOException {
        byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE, limit));
        if (in.read() >= 0) {
            throw new ChunkTooLargeException();
        }
        return data;
    }

    private static void waitQuietly(Object monitor, long ms) {
        try {
            monitor.wait(Math.max(1L, ms));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    static Map<String, Object> toMap(Session s, Role role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("relayId", s.relayId);
        map.put("fromClientId", s.fromClientId);
        map.put("toClientId", s.toClientId);
        map.put("filename", s.filename);
        map.put("kind", s.kind);
        map.put("size", s.size);
        map.put("state", s.state.name());
        map.put("pushed", s.pushed);
        map.put("acked", s.acked);
        map.put("version", s.version);
        map.put("chunkSize", PeerFileRules.RELAY_CHUNK_BYTES);
        map.put("window", PeerFileRules.RELAY_WINDOW_CHUNKS);
        map.put("createdAtMs", s.createdAtMs);
        if (s.state == State.WAITING) {
            map.put("acceptDeadlineMs", s.createdAtMs + PeerFileRules.RELAY_ACCEPT_TIMEOUT_MS);
        }
        if (role == Role.SENDER) {
            map.put("canPush", s.state == State.ACCEPTED && !s.pushInFlight
                    && s.pushed < s.size && s.buffer.size() < PeerFileRules.RELAY_WINDOW_CHUNKS);
        }
        return map;
    }

    enum Role { SENDER, RECIPIENT, EITHER }

    private static final class ChunkTooLargeException extends IOException {
    }

    private static final class Chunk {
        final long offset;
        final byte[] data;

        Chunk(long offset, byte[] data) {
            this.offset = offset;
            this.data = data;
        }

        long end() {
            return offset + data.length;
        }
    }

    static final class Session {
        final String relayId;
        final String fromClientId;
        final String toClientId;
        final String filename;
        final String kind;
        final long size;
        final long createdAtMs;
        final ArrayDeque<Chunk> buffer = new ArrayDeque<>();
        State state = State.WAITING;
        String message = "等待對方接收";
        long pushed;
        long acked;
        long version = 1L;
        long senderSeenMs;
        long recipientSeenMs;
        long endedAtMs;
        boolean pushInFlight;

        Session(String relayId, String fromClientId, String toClientId, String filename,
                String kind, long size, long nowMs) {
            this.relayId = relayId;
            this.fromClientId = fromClientId;
            this.toClientId = toClientId;
            this.filename = filename;
            this.kind = kind;
            this.size = size;
            this.createdAtMs = nowMs;
            this.senderSeenMs = nowMs;
            this.recipientSeenMs = nowMs;
        }

        void touch(Role role, long nowMs) {
            if (role == Role.SENDER) {
                senderSeenMs = nowMs;
            } else if (role == Role.RECIPIENT) {
                recipientSeenMs = nowMs;
            }
        }

        void changed() {
            version++;
            notifyAll();
        }
    }

    private static final class Lookup {
        final Session session;
        final Role role;
        final Result error;

        private Lookup(Session session, Role role, Result error) {
            this.session = session;
            this.role = role;
            this.error = error;
        }

        static Lookup ok(Session session, Role role) {
            return new Lookup(session, role, null);
        }

        static Lookup error(Result error) {
            return new Lookup(null, null, error);
        }
    }

    public static final class Result {
        public enum Status { OK, CONFLICT, NOT_FOUND, FORBIDDEN, GONE, BUSY, FAILED }

        public final Status status;
        public final String message;
        public final String relayId;
        public final String fromClientId;
        public final String toClientId;
        public final String filename;
        public final String kind;
        public final long size;
        public final State state;
        private final Map<String, Object> senderView;
        private final Map<String, Object> otherView;

        private Result(Status status, String message, Session s) {
            this.status = status;
            this.relayId = s != null ? s.relayId : "";
            this.fromClientId = s != null ? s.fromClientId : "";
            this.toClientId = s != null ? s.toClientId : "";
            this.filename = s != null ? s.filename : "";
            this.kind = s != null ? s.kind : PeerFileRules.KIND_FILE;
            this.size = s != null ? s.size : 0L;
            this.state = s != null ? s.state : null;
            this.message = message != null ? message : (s != null ? s.message : "");
            this.senderView = s != null ? toMap(s, Role.SENDER) : null;
            this.otherView = s != null ? toMap(s, Role.RECIPIENT) : null;
        }

        public boolean ok() {
            return status == Status.OK;
        }

        /** 給 API 回應用的狀態欄位；傳送端多一個 canPush。 */
        public Map<String, Object> view(String requesterClientId) {
            if (senderView == null) {
                return new LinkedHashMap<>();
            }
            String requester = PeerFileRules.normalizeClientId(requesterClientId);
            return new LinkedHashMap<>(requester.equals(fromClientId) ? senderView : otherView);
        }

        static Result ok(Session s) {
            return new Result(Status.OK, null, s);
        }

        static Result conflict(Session s, String message) {
            return new Result(Status.CONFLICT, message, s);
        }

        static Result gone(Session s) {
            return new Result(Status.GONE, null, s);
        }

        static Result notFound(String message) {
            return new Result(Status.NOT_FOUND, message, null);
        }

        static Result forbidden(String message) {
            return new Result(Status.FORBIDDEN, message, null);
        }

        static Result busy(String message) {
            return new Result(Status.BUSY, message, null);
        }

        static Result failed(String message) {
            return new Result(Status.FAILED, message, null);
        }
    }

    public static final class ChunkResult {
        public enum Status { DATA, NO_DATA, ERROR }

        public final Status status;
        public final Result result;
        public final long offset;
        public final byte[] data;

        private ChunkResult(Status status, Result result, long offset, byte[] data) {
            this.status = status;
            this.result = result;
            this.offset = offset;
            this.data = data;
        }

        static ChunkResult data(Session s, long offset, byte[] data) {
            return new ChunkResult(Status.DATA, Result.ok(s), offset, data);
        }

        static ChunkResult noData(Session s) {
            return new ChunkResult(Status.NO_DATA, Result.ok(s), 0L, null);
        }

        static ChunkResult of(Result error) {
            return new ChunkResult(Status.ERROR, error, 0L, null);
        }
    }
}
