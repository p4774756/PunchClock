package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** 經絡闖關進度；合併後只會變好不會倒退。 */
public final class MeridianQuestStore {

    private static final Logger LOG = Logger.getLogger(MeridianQuestStore.class.getName());
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Path PERSISTENT_ROOT = Paths.get("/var/data");
    private static final Set<String> STAGE_IDS = Set.of(
            "LU", "LI", "ST", "SP", "HT", "SI", "BL", "KI", "PC", "TE", "GB", "LR");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final Path savePath;
    private Map<String, Object> progress;

    public MeridianQuestStore() {
        this(logPath(defaultPath()));
    }

    public MeridianQuestStore(Path savePath) {
        this.savePath = savePath;
        this.progress = emptyProgress();
        load();
    }

    public static Path defaultPath() {
        String home = System.getProperty("user.home", ".");
        return resolvePath(
                null,
                System.getenv("MERIDIAN_QUEST_PATH"),
                Files.isDirectory(PERSISTENT_ROOT),
                PERSISTENT_ROOT.resolve("meridian-quest-progress.json"),
                Paths.get(home, ".punchclock", "meridian-quest-progress.json"));
    }

    public static Path resolvePath(String override, String env, boolean persistentAvailable, Path persistent, Path home) {
        return StorageLocation.resolve(override, env, persistentAvailable, persistent, home);
    }

    private static Path logPath(Path path) {
        LOG.info("經絡闖關進度：" + path);
        return path;
    }

    public synchronized Map<String, Object> current() {
        return copyProgress(progress);
    }

    public synchronized Map<String, Object> mergeAndSave(Map<String, Object> incoming) {
        Map<String, Object> sanitized = sanitizeProgress(incoming);
        progress = mergeProgress(progress, sanitized);
        save();
        return copyProgress(progress);
    }

    private void load() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }
        try {
            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            Map<String, Object> saved = GSON.fromJson(json, MAP_TYPE);
            if (saved != null) {
                progress = sanitizeProgress(saved);
            }
        } catch (Exception ignored) {
            progress = emptyProgress();
        }
    }

    private void save() {
        if (savePath == null) {
            return;
        }
        try {
            Path parent = savePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(savePath, GSON.toJson(progress), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 寫入失敗仍保留記憶體
        }
    }

    static Map<String, Object> emptyProgress() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("stages", new LinkedHashMap<String, Object>());
        return root;
    }

    static Map<String, Object> copyProgress(Map<String, Object> src) {
        String json = GSON.toJson(src);
        Map<String, Object> copy = GSON.fromJson(json, MAP_TYPE);
        return copy != null ? copy : emptyProgress();
    }

  @SuppressWarnings("unchecked")
    static Map<String, Object> sanitizeProgress(Map<String, Object> raw) {
        Map<String, Object> root = emptyProgress();
        if (raw == null) {
            return root;
        }
        Object version = raw.get("version");
        if (!(version instanceof Number) || ((Number) version).intValue() != 1) {
            return root;
        }
        Object stagesObj = raw.get("stages");
        if (!(stagesObj instanceof Map)) {
            return root;
        }
        Map<String, Object> stagesIn = (Map<String, Object>) stagesObj;
        Map<String, Object> stagesOut = new LinkedHashMap<>();
        for (String id : STAGE_IDS) {
            Object stageRaw = stagesIn.get(id);
            if (stageRaw instanceof Map) {
                Map<String, Object> clean = sanitizeStage((Map<String, Object>) stageRaw);
                if (clean != null) {
                    stagesOut.put(id, clean);
                }
            }
        }
        root.put("stages", stagesOut);
        return root;
    }

    private static Map<String, Object> sanitizeStage(Map<String, Object> s) {
        int stars = clampStar(asInt(s.get("stars"), 0));
        int bestScore = clamp(asInt(s.get("bestScore"), 0), 0, 10_000);
        Integer bestTimeMs = null;
        if (stars >= 1) {
            int t = asInt(s.get("bestTimeMs"), -1);
            if (t >= 1_000 && t <= 3_600_000) {
                bestTimeMs = t;
            }
        }
        String clearedAt = s.get("clearedAt") instanceof String ? String.valueOf(s.get("clearedAt")).trim() : null;
        if (clearedAt != null && clearedAt.isEmpty()) {
            clearedAt = null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stars", stars);
        out.put("bestScore", bestScore);
        out.put("bestTimeMs", bestTimeMs);
        out.put("clearedAt", clearedAt);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mergeProgress(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> left = sanitizeProgress(a);
        Map<String, Object> right = sanitizeProgress(b);
        Map<String, Object> merged = emptyProgress();
        Map<String, Object> stagesOut = (Map<String, Object>) merged.get("stages");
        Map<String, Object> leftStages = (Map<String, Object>) left.get("stages");
        Map<String, Object> rightStages = (Map<String, Object>) right.get("stages");
        for (String id : STAGE_IDS) {
            Map<String, Object> ls = leftStages.get(id) instanceof Map ? (Map<String, Object>) leftStages.get(id) : null;
            Map<String, Object> rs = rightStages.get(id) instanceof Map ? (Map<String, Object>) rightStages.get(id) : null;
            stagesOut.put(id, mergeStage(ls, rs));
        }
        return merged;
    }

  @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeStage(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> left = a != null ? sanitizeStage(a) : sanitizeStage(Map.of());
        Map<String, Object> right = b != null ? sanitizeStage(b) : sanitizeStage(Map.of());
        int stars = Math.max(asInt(left.get("stars"), 0), asInt(right.get("stars"), 0));
        stars = clampStar(stars);
        int bestScore = Math.max(asInt(left.get("bestScore"), 0), asInt(right.get("bestScore"), 0));
        Integer bestTime = mergeBestTime(left.get("bestTimeMs"), right.get("bestTimeMs"), stars);
        String clearedLeft = left.get("clearedAt") instanceof String ? String.valueOf(left.get("clearedAt")) : null;
        String clearedRight = right.get("clearedAt") instanceof String ? String.valueOf(right.get("clearedAt")) : null;
        String clearedAt = maxString(clearedLeft, clearedRight);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stars", stars);
        out.put("bestScore", bestScore);
        out.put("bestTimeMs", bestTime);
        out.put("clearedAt", clearedAt);
        return out;
    }

    private static Integer mergeBestTime(Object a, Object b, int stars) {
        if (stars < 1) {
            return null;
        }
        Integer left = a instanceof Number ? ((Number) a).intValue() : null;
        Integer right = b instanceof Number ? ((Number) b).intValue() : null;
        if (left != null && (left < 1_000 || left > 3_600_000)) {
            left = null;
        }
        if (right != null && (right < 1_000 || right > 3_600_000)) {
            right = null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }

    private static String maxString(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static int asInt(Object raw, int fallback) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt((String) raw);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampStar(int stars) {
        if (stars <= 0) {
            return 0;
        }
        if (stars >= 3) {
            return 3;
        }
        return stars;
    }
}