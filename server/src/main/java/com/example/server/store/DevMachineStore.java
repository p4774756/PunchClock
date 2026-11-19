package com.example.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 開發筆電心跳（記憶體）；重啟後清空。 */
public final class DevMachineStore {

    public static final long ONLINE_WINDOW_MS = 3L * 60L * 1000L;
    public static final long MIN_REPORT_INTERVAL_MS = 10L * 1000L;
    public static final int MAX_BODY_BYTES = 2048;

    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]{1,64}$");
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^[0-9a-fA-F]{7,40}$");

    private static final List<String> MACHINE_ORDER = List.of("home", "office");
    private static final Map<String, String> LABELS = Map.of(
            "home", "小貓",
            "office", "猛虎"
    );

    @FunctionalInterface
    public interface Clock {
        long now();
    }

    public static final class Snapshot {
        public final String machine;
        public final long lastSeen;
        public final Integer issue;
        public final String branch;
        public final Long runStartedAt;
        public final boolean agentRunning;
        public final String commit;
        public final Integer battery;
        public final Boolean charging;

        Snapshot(String machine, long lastSeen, Integer issue, String branch, Long runStartedAt,
                 boolean agentRunning, String commit, Integer battery, Boolean charging) {
            this.machine = machine;
            this.lastSeen = lastSeen;
            this.issue = issue;
            this.branch = branch;
            this.runStartedAt = runStartedAt;
            this.agentRunning = agentRunning;
            this.commit = commit;
            this.battery = battery;
            this.charging = charging;
        }
    }

    public enum ApplyResult {
        OK,
        BAD_MACHINE,
        BAD_BODY
    }

    private final ConcurrentHashMap<String, Snapshot> byMachine = new ConcurrentHashMap<>();
    private final Clock clock;

    public DevMachineStore() {
        this(System::currentTimeMillis);
    }

    public DevMachineStore(Clock clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public static boolean isAllowedMachine(String machine) {
        return machine != null && LABELS.containsKey(machine);
    }

    public static String labelFor(String machine) {
        return LABELS.getOrDefault(machine, machine);
    }

    public ApplyResult applyHeartbeat(String machine, Map<String, Object> fields) {
        if (!isAllowedMachine(machine)) {
            return ApplyResult.BAD_MACHINE;
        }
        long now = clock.now();
        Snapshot previous = byMachine.get(machine);
        if (previous != null && now - previous.lastSeen < MIN_REPORT_INTERVAL_MS) {
            // 10 秒內重複回報：仍更新欄位，不做額外處理
        }
        Parsed parsed = Parsed.from(fields);
        if (!parsed.valid) {
            return ApplyResult.BAD_BODY;
        }
        Snapshot next = new Snapshot(
                machine,
                now,
                parsed.issue,
                parsed.branch,
                parsed.runStartedAt,
                parsed.agentRunning,
                parsed.commit,
                parsed.battery,
                parsed.charging
        );
        byMachine.put(machine, next);
        return ApplyResult.OK;
    }

    public List<Map<String, Object>> listForApi(long now, IssueTitleResolver titles) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String machine : MACHINE_ORDER) {
            Snapshot snap = byMachine.get(machine);
            boolean online = snap != null && now - snap.lastSeen <= ONLINE_WINDOW_MS;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("machine", machine);
            row.put("label", labelFor(machine));
            row.put("online", online);
            if (snap == null) {
                row.put("lastSeen", null);
            } else {
                row.put("lastSeen", snap.lastSeen);
            }
            if (online && snap != null) {
                row.put("issue", snap.issue);
                if (snap.issue != null) {
                    String title = titles == null ? "" : titles.title(snap.issue);
                    if (title != null && !title.isEmpty()) {
                        row.put("issueTitle", title);
                    }
                }
                row.put("branch", snap.branch);
                row.put("runStartedAt", snap.runStartedAt);
                row.put("agentRunning", snap.agentRunning);
                if (snap.commit != null) {
                    row.put("commit", snap.commit);
                }
                if (snap.battery != null) {
                    row.put("battery", snap.battery);
                }
                if (snap.charging != null) {
                    row.put("charging", snap.charging);
                }
            }
            out.add(row);
        }
        return Collections.unmodifiableList(out);
    }

    public long currentTimeMillis() {
        return clock.now();
    }

    public boolean isOnline(String machine, long now) {
        Snapshot snap = byMachine.get(machine);
        return snap != null && now - snap.lastSeen <= ONLINE_WINDOW_MS;
    }

    @FunctionalInterface
    public interface IssueTitleResolver {
        String title(int issue);
    }

    private static final class Parsed {
        final boolean valid;
        final Integer issue;
        final String branch;
        final Long runStartedAt;
        final boolean agentRunning;
        final String commit;
        final Integer battery;
        final Boolean charging;

        private Parsed(boolean valid, Integer issue, String branch, Long runStartedAt, boolean agentRunning,
                       String commit, Integer battery, Boolean charging) {
            this.valid = valid;
            this.issue = issue;
            this.branch = branch;
            this.runStartedAt = runStartedAt;
            this.agentRunning = agentRunning;
            this.commit = commit;
            this.battery = battery;
            this.charging = charging;
        }

        static Parsed from(Map<String, Object> fields) {
            if (fields == null) {
                return invalid();
            }
            Integer issue = null;
            if (fields.containsKey("issue")) {
                Object raw = fields.get("issue");
                if (raw == null) {
                    issue = null;
                } else if (raw instanceof Number) {
                    int n = ((Number) raw).intValue();
                    if (n < 1) {
                        return invalid();
                    }
                    issue = n;
                } else {
                    return invalid();
                }
            }
            String branch = null;
            if (fields.containsKey("branch")) {
                Object raw = fields.get("branch");
                if (raw == null) {
                    branch = null;
                } else {
                    String s = String.valueOf(raw);
                    if (!BRANCH_PATTERN.matcher(s).matches()) {
                        return invalid();
                    }
                    branch = s;
                }
            }
            Long runStartedAt = null;
            if (fields.containsKey("runStartedAt")) {
                Object raw = fields.get("runStartedAt");
                if (raw == null) {
                    runStartedAt = null;
                } else if (raw instanceof Number) {
                    long n = ((Number) raw).longValue();
                    if (n < 0) {
                        return invalid();
                    }
                    runStartedAt = n;
                } else {
                    return invalid();
                }
            }
            boolean agentRunning = false;
            if (fields.containsKey("agentRunning")) {
                Object raw = fields.get("agentRunning");
                if (raw instanceof Boolean) {
                    agentRunning = (Boolean) raw;
                } else {
                    return invalid();
                }
            }
            String commit = null;
            if (fields.containsKey("commit")) {
                Object raw = fields.get("commit");
                if (raw == null) {
                    commit = null;
                } else {
                    String s = String.valueOf(raw);
                    if (!COMMIT_PATTERN.matcher(s).matches()) {
                        return invalid();
                    }
                    commit = s.toLowerCase();
                }
            }
            Integer battery = null;
            if (fields.containsKey("battery")) {
                Object raw = fields.get("battery");
                if (raw instanceof Number) {
                    int n = ((Number) raw).intValue();
                    if (n < 0 || n > 100) {
                        return invalid();
                    }
                    battery = n;
                } else {
                    return invalid();
                }
            }
            Boolean charging = null;
            if (fields.containsKey("charging")) {
                Object raw = fields.get("charging");
                if (raw instanceof Boolean) {
                    charging = (Boolean) raw;
                } else {
                    return invalid();
                }
            }
            return new Parsed(true, issue, branch, runStartedAt, agentRunning, commit, battery, charging);
        }

        static Parsed invalid() {
            return new Parsed(false, null, null, null, false, null, null, null);
        }
    }
}