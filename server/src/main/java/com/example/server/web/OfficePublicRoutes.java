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
                if (anyReviewActive(machines, now)) {
                    return "審核中";
                }
                return "待機";
            }
            if ("tiger".equals(charId)) {
                DevMachineStore.Snapshot office = byMachine(machines, "office");
                if (isWorking(office, now)) {
                    return "工作中";
                }
                return "待機";
            }
            if ("oki".equals(charId)) {
                if (doc != null && doc.now != null && doc.now.current != null && doc.now.current.issue > 0) {
                    return "工作中";
                }
                return "待機";
            }
            return "待機";
        }

        static long updatedAtMs(String charId, List<DevMachineStore.Snapshot> machines,
                                DevStatusModels.Document doc, long fallback) {
            if ("tiger".equals(charId)) {
                DevMachineStore.Snapshot office = byMachine(machines, "office");
                if (office != null && office.lastSeen > 0) {
                    return office.lastSeen;
                }
            }
            if ("reviewer".equals(charId)) {
                long best = 0;
                for (DevMachineStore.Snapshot row : machines) {
                    if (row == null) {
                        continue;
                    }
                    if ("review".equals(row.role) && row.lastSeen > best) {
                        best = row.lastSeen;
                    }
                    for (DevMachineStore.RunEntry run : row.runs) {
                        if ("review".equals(run.role) && row.lastSeen > best) {
                            best = row.lastSeen;
                        }
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
            return fallback;
        }

        private static DevMachineStore.Snapshot byMachine(List<DevMachineStore.Snapshot> machines, String id) {
            if (machines == null) {
                return null;
            }
            for (DevMachineStore.Snapshot row : machines) {
                if (row != null && id.equals(row.machine)) {
                    return row;
                }
            }
            return null;
        }

        private static boolean isOnline(DevMachineStore.Snapshot row, long now) {
            return row != null && row.lastSeen > 0 && now - row.lastSeen <= DevMachineStore.ONLINE_WINDOW_MS;
        }

        private static boolean isWorking(DevMachineStore.Snapshot row, long now) {
            if (row == null || !isOnline(row, now)) {
                return false;
            }
            if (row.agentRunning) {
                return true;
            }
            if (row.issue != null && row.issue > 0) {
                return true;
            }
            for (DevMachineStore.RunEntry run : row.runs) {
                if (run != null && run.issue > 0) {
                    return true;
                }
            }
            return false;
        }

        private static boolean anyReviewActive(List<DevMachineStore.Snapshot> machines, long now) {
            if (machines == null) {
                return false;
            }
            for (DevMachineStore.Snapshot row : machines) {
                if (row == null || !isOnline(row, now)) {
                    continue;
                }
                if ("review".equals(row.role)) {
                    return true;
                }
                for (DevMachineStore.RunEntry run : row.runs) {
                    if (run != null && "review".equals(run.role)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}