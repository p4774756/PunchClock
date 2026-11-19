package com.example.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 開發筆電心跳（記憶體）；重啟後清空。 */
public final class DevMachineStore {

    public static final long ONLINE_WINDOW_MS = 3L * 60L * 1000L;
    public static final long MIN_REPORT_INTERVAL_MS = 10L * 1000L;
    public static final int MAX_BODY_BYTES = 8192;
    public static final int MAX_RUNS = 12;

    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]{1,64}$");
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^[0-9a-fA-F]{7,40}$");
    private static final Pattern WORKTREE_PATTERN = Pattern.compile("^[A-Za-z0-9._/~-]{1,128}$");

    private static final List<String> MACHINE_ORDER = List.of("home", "office");
    private static final Map<String, String> LABELS = Map.of(
            "home", "小貓",
            "office", "猛虎"
    );

    @FunctionalInterface
    public interface Clock {
        long now();
    }

    public static final class RunEntry {
        public final int issue;
        public final int round;
        public final String branch;
        public final Long startedAt;
        public final String worktree;
        public final String commit;

        RunEntry(int issue, int round, String branch, Long startedAt, String worktree, String commit) {
            this.issue = issue;
            this.round = round;
            this.branch = branch;
            this.startedAt = startedAt;
            this.worktree = worktree;
            this.commit = commit;
        }
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
        public final List<RunEntry> runs;

        Snapshot(String machine, long lastSeen, Integer issue, String branch, Long runStartedAt,
                 boolean agentRunning, String commit, Integer battery, Boolean charging,
                 List<RunEntry> runs) {
            this.machine = machine;
            this.lastSeen = lastSeen;
            this.issue = issue;
            this.branch = branch;
            this.runStartedAt = runStartedAt;
            this.agentRunning = agentRunning;
            this.commit = commit;
            this.battery = battery;
            this.charging = charging;
            this.runs = runs == null ? List.of() : List.copyOf(runs);
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
                parsed.charging,
                parsed.runs
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
                row.put("runs", runsForApi(snap.runs, titles));
            }
            out.add(row);
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Map<String, Object>> runsForApi(List<RunEntry> runs, IssueTitleResolver titles) {
        List<RunEntry> sorted = new ArrayList<>(runs == null ? List.of() : runs);
        sorted.sort(Comparator.comparing(
                (RunEntry r) -> r.startedAt == null ? Long.MAX_VALUE : r.startedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RunEntry run : sorted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("issue", run.issue);
            row.put("round", run.round);
            row.put("branch", run.branch);
            row.put("startedAt", run.startedAt);
            if (run.worktree != null) {
                row.put("worktree", run.worktree);
            }
            if (run.commit != null) {
                row.put("commit", run.commit);
            }
            String title = titles == null ? "" : titles.title(run.issue);
            if (title != null && !title.isEmpty()) {
                row.put("issueTitle", title);
            }
            out.add(row);
        }
        return out;
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

    static String worktreeBasename(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        if (end == 0) {
            return path;
        }
        int start = end - 1;
        while (start >= 0 && path.charAt(start) != '/') {
            start--;
        }
        return path.substring(start + 1, end);
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
        final List<RunEntry> runs;

        private Parsed(boolean valid, Integer issue, String branch, Long runStartedAt, boolean agentRunning,
                       String commit, Integer battery, Boolean charging, List<RunEntry> runs) {
            this.valid = valid;
            this.issue = issue;
            this.branch = branch;
            this.runStartedAt = runStartedAt;
            this.agentRunning = agentRunning;
            this.commit = commit;
            this.battery = battery;
            this.charging = charging;
            this.runs = runs == null ? List.of() : runs;
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
                    Number num = (Number) raw;
                    if (!isWholeNumber(num)) {
                        return invalid();
                    }
                    int n = num.intValue();
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
            List<RunEntry> runs = List.of();
            if (fields.containsKey("runs")) {
                ParseRuns parsedRuns = parseRuns(fields.get("runs"));
                if (parsedRuns == null) {
                    return invalid();
                }
                runs = parsedRuns.entries;
            }
            return new Parsed(true, issue, branch, runStartedAt, agentRunning, commit, battery, charging, runs);
        }

        static Parsed invalid() {
            return new Parsed(false, null, null, null, false, null, null, null, List.of());
        }
    }

    private static final class ParseRuns {
        final List<RunEntry> entries;

        ParseRuns(List<RunEntry> entries) {
            this.entries = entries;
        }
    }

    private static ParseRuns parseRuns(Object raw) {
        if (raw == null) {
            return new ParseRuns(List.of());
        }
        if (!(raw instanceof List)) {
            return null;
        }
        List<?> list = (List<?>) raw;
        if (list.size() > MAX_RUNS) {
            return null;
        }
        List<RunEntry> out = new ArrayList<>();
        for (Object item : list) {
            RunEntry entry = parseRunEntry(item);
            if (entry == null) {
                return null;
            }
            out.add(entry);
        }
        return new ParseRuns(out);
    }

    private static RunEntry parseRunEntry(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) raw;
        Object issueRaw = map.get("issue");
        if (!(issueRaw instanceof Number)) {
            return null;
        }
        Number issueNum = (Number) issueRaw;
        if (!isWholeNumber(issueNum)) {
            return null;
        }
        int issue = issueNum.intValue();
        if (issue < 1) {
            return null;
        }
        Object roundRaw = map.get("round");
        if (!(roundRaw instanceof Number)) {
            return null;
        }
        Number roundNum = (Number) roundRaw;
        if (!isWholeNumber(roundNum)) {
            return null;
        }
        int round = roundNum.intValue();
        if (round < 0 || round > 9) {
            return null;
        }
        Object branchRaw = map.get("branch");
        if (!(branchRaw instanceof String) && !(branchRaw instanceof Number)) {
            return null;
        }
        String branch = String.valueOf(branchRaw);
        if (!BRANCH_PATTERN.matcher(branch).matches()) {
            return null;
        }
        Long startedAt = null;
        if (map.containsKey("startedAt")) {
            Object startedRaw = map.get("startedAt");
            if (startedRaw == null) {
                startedAt = null;
            } else if (startedRaw instanceof Number) {
                long n = ((Number) startedRaw).longValue();
                if (n < 0) {
                    return null;
                }
                startedAt = n;
            } else {
                return null;
            }
        }
        String worktree = null;
        if (map.containsKey("worktree")) {
            Object wtRaw = map.get("worktree");
            if (wtRaw == null) {
                worktree = null;
            } else {
                String wt = String.valueOf(wtRaw);
                if (!WORKTREE_PATTERN.matcher(wt).matches()) {
                    return null;
                }
                worktree = worktreeBasename(wt);
                if (worktree == null || worktree.isEmpty()) {
                    return null;
                }
            }
        }
        String commit = null;
        if (map.containsKey("commit")) {
            Object commitRaw = map.get("commit");
            if (commitRaw == null) {
                commit = null;
            } else {
                String s = String.valueOf(commitRaw);
                if (!COMMIT_PATTERN.matcher(s).matches()) {
                    return null;
                }
                commit = s.toLowerCase();
            }
        }
        return new RunEntry(issue, round, branch, startedAt, worktree, commit);
    }

    private static boolean isWholeNumber(Number num) {
        if (num == null) {
            return false;
        }
        double d = num.doubleValue();
        return d == Math.rint(d) && !Double.isInfinite(d) && !Double.isNaN(d);
    }
}