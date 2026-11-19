package com.example.server.store;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MeridianQuestStoreTest {

    @Test
    public void mergeNeverRegresses() {
        Map<String, Object> older = stage(2, 5000, 120_000, "2020-01-01");
        Map<String, Object> newer = stage(1, 9000, 80_000, "2021-01-01");
        Map<String, Object> merged = MeridianQuestStore.mergeProgress(wrap(older), wrap(newer));
        Map<String, Object> lu = stageOf(merged, "LU");
        assertEquals(2, intVal(lu.get("stars")));
        assertEquals(9000, intVal(lu.get("bestScore")));
        assertEquals(80_000, intVal(lu.get("bestTimeMs")));
    }

    @Test
    public void sanitizeDropsInvalid() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("version", 1);
        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("LU", Map.of("stars", 9, "bestScore", 99_999, "bestTimeMs", 50));
        stages.put("ZZ", Map.of("stars", 2));
        bad.put("stages", stages);
        Map<String, Object> clean = MeridianQuestStore.sanitizeProgress(bad);
        Map<String, Object> lu = stageOf(clean, "LU");
        assertEquals(3, intVal(lu.get("stars")));
        assertEquals(10_000, intVal(lu.get("bestScore")));
        assertNull(lu.get("bestTimeMs"));
        assertTrue(((Map<?, ?>) clean.get("stages")).containsKey("LU"));
        assertEquals(1, ((Map<?, ?>) clean.get("stages")).size());
    }

    @Test
    public void persistsAcrossNewStore() throws Exception {
        Path file = Files.createTempFile("meridian-quest", ".json");
        Files.deleteIfExists(file);
        try {
            MeridianQuestStore store = new MeridianQuestStore(file);
            Map<String, Object> saved = store.mergeAndSave(wrap(stage(2, 4000, 90_000, "2024-05-01")));
            assertEquals(2, intVal(stageOf(saved, "LU").get("stars")));

            Map<String, Object> loaded = new MeridianQuestStore(file).current();
            assertEquals(2, intVal(stageOf(loaded, "LU").get("stars")));
            assertEquals(4000, intVal(stageOf(loaded, "LU").get("bestScore")));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void pathPrefersOverrideThenEnvThenDiskThenHome() {
        Path disk = Paths.get("/var/data/meridian-quest-progress.json");
        Path home = Paths.get("/home/dev/.punchclock/meridian-quest-progress.json");
        assertEquals(Paths.get("/custom/quest.json"),
                MeridianQuestStore.resolvePath("/custom/quest.json", "/from-env.json", true, disk, home));
        assertEquals(disk, MeridianQuestStore.resolvePath(null, null, true, disk, home));
        assertEquals(home, MeridianQuestStore.resolvePath(null, null, false, disk, home));
    }

    private static Map<String, Object> wrap(Map<String, Object> lu) {
        Map<String, Object> stages = new LinkedHashMap<>();
        stages.put("LU", lu);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("stages", stages);
        return root;
    }

    private static Map<String, Object> stage(int stars, int score, int timeMs, String cleared) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("stars", stars);
        s.put("bestScore", score);
        s.put("bestTimeMs", timeMs);
        s.put("clearedAt", cleared);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stageOf(Map<String, Object> progress, String id) {
        return (Map<String, Object>) ((Map<String, Object>) progress.get("stages")).get(id);
    }

    private static int intVal(Object raw) {
        return raw instanceof Number ? ((Number) raw).intValue() : -1;
    }
}