package com.example.server.web;

import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 辦公室訪客可見的粗略角色狀態（白名單欄位，無機敏細節）。 */
public final class OfficePublicRoutes {

    private static final List<String> CHARACTER_IDS = List.of("oki", "tiger", "reviewer");

    private final DevMachineStore devMachineStore;
    private final DevStatusService devStatusService;

    public OfficePublicRoutes(DevMachineStore devMachineStore, DevStatusService devStatusService) {
        this.devMachineStore = devMachineStore;
        this.devStatusService = devStatusService;
    }

    public void register(Javalin app) {
        app.get("/api/office/public", this::getPublic);
    }

    void getPublic(Context ctx) {
        long now = devMachineStore.currentTimeMillis();
        Map<String, Object> payload = OfficePublicPayload.build(now, devMachineStore, devStatusService);
        ctx.json(payload);
    }

    static final class OfficePublicPayload {

        private OfficePublicPayload() {
        }

        static Map<String, Object> build(long now, DevMachineStore store, DevStatusService devStatusService) {
            DevStatusService.View view = devStatusService.get(now);
            DevStatusModels.Document doc = view == null ? null : view.document;
            List<DevMachineStore.Snapshot> machines = store.machineSnapshots();

            Map<String, Object> characters = new LinkedHashMap<>();
            for (String id : CHARACTER_IDS) {
                String status = coarseStatus(id, machines, doc, now);
                long updatedAtMs = updatedAtMs(id, machines, doc, now);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", status);
                row.put("updatedAt", Instant.ofEpochMilli(updatedAtMs).toString());
                characters.put(id, row);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("characters", characters);
            return out;
        }

        static String coarseStatus(String charId, List<DevMachineStore.Snapshot> machines,
                                   DevStatusModels.Document doc, long now) {
            if ("reviewer".equals(charId)) {
                int n = countReviewActive(machines, now);
                if (n > 0) {
                    return n == 1 ? "審核中" : "審核中 ×" + n;
                }
                return "待機";
            }
            if ("tiger".equals(charId)) {
                int n = countActiveLoops(machines, now);
                if (n > 0) {
                    return n == 1 ? "工作中" : "工作中 ×" + n;
                }
                return "待機";
            }
            if ("oki".equals(charId)) {
                if (doc != null && doc.now != null && doc.now.current != null && doc.now.current.issue > 0) {
                    return "工作中";
                }
                int q = doc != null && doc.now != null && doc.now.queue != null ? doc.now.queue.size() : 0;
                return "佇列 " + q;
            }
            return "待機";
        }

        static long updatedAtMs(String charId, List<DevMachineStore.Snapshot> machines,
                                DevStatusModels.Document doc, long now) {
            if ("tiger".equals(charId)) {
                long best = 0;
                for (DevMachineStore.Snapshot row : machines) {
                    if (row == null || !isOnline(row, now)) {
                        continue;
                    }
                    if (hasActiveWork(row) && row.lastSeen > best) {
                        best = row.lastSeen;
                    }
                }
                if (best > 0) {
                    return best;
                }
            }
            if ("reviewer".equals(charId)) {
                long best = 0;
                for (DevMachineStore.Snapshot row : machines) {
                    if (row == null || !isOnline(row, now)) {
                        continue;
                    }
                    if (countReviewOnMachine(row) > 0 && row.lastSeen > best) {
                        best = row.lastSeen;
                    }
                }
                if (best > 0) {
                    return best;
                }
            }
            if ("oki".equals(charId) && doc != null && doc.now != null && doc.now.current != null) {
                long updated = doc.now.current.updatedAt;
                if (updated > 0) {
                    return updated;
                }
            }
            return now;
        }

        private static boolean isOnline(DevMachineStore.Snapshot row, long now) {
            return row != null && row.lastSeen > 0 && now - row.lastSeen <= DevMachineStore.ONLINE_WINDOW_MS;
        }

        private static boolean hasActiveWork(DevMachineStore.Snapshot row) {
            if (row == null) {
                return false;
            }
            if (row.agentRunning) {
                return true;
            }
            if (row.issue != null && row.issue > 0) {
                return true;
            }
            if (row.runs != null) {
                for (DevMachineStore.RunEntry run : row.runs) {
                    if (run != null && run.issue > 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        static int countActiveLoops(List<DevMachineStore.Snapshot> machines, long now) {
            if (machines == null) {
                return 0;
            }
            int total = 0;
            for (DevMachineStore.Snapshot row : machines) {
                if (row == null || !isOnline(row, now)) {
                    continue;
                }
                int onMachine = loopsOnMachine(row);
                if (onMachine > 0) {
                    total += onMachine;
                }
            }
            return total;
        }

        private static int loopsOnMachine(DevMachineStore.Snapshot row) {
            if (row.runs != null && !row.runs.isEmpty()) {
                return row.runs.size();
            }
            if (row.agentRunning || (row.issue != null && row.issue > 0)) {
                return 1;
            }
            return 0;
        }

        static int countReviewActive(List<DevMachineStore.Snapshot> machines, long now) {
            if (machines == null) {
                return 0;
            }
            int total = 0;
            for (DevMachineStore.Snapshot row : machines) {
                if (row == null || !isOnline(row, now)) {
                    continue;
                }
                total += countReviewOnMachine(row);
            }
            return total;
        }

        private static int countReviewOnMachine(DevMachineStore.Snapshot row) {
            int n = 0;
            if (row.runs != null) {
                for (DevMachineStore.RunEntry run : row.runs) {
                    if (run != null && "review".equals(run.role)) {
                        n++;
                    }
                }
            }
            if (n == 0 && "review".equals(row.role)) {
                n = 1;
            }
            return n;
        }
    }
}