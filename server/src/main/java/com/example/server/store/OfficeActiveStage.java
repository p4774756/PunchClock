package com.example.server.store;

import com.example.server.store.DevStatusModels.Item;
import com.example.server.store.DevStatusModels.PullRow;

/** 首頁秘書「正在進行」固定階段文案。 */
public final class OfficeActiveStage {

    public static final String DEV = "開發中";
    public static final String WAIT_CI = "等 CI";
    public static final String REVIEW = "審查中";
    public static final String FIX = "修正中";
    public static final String MERGE = "合併中";

    private OfficeActiveStage() {
    }

    public static String forRun(DevMachineStore.RunEntry run, boolean machineAgentRunning,
                                String machineVerdict, String machineRole, PullRow pull) {
        if (run != null && run.stage != null) {
            return run.stage;
        }
        if (run != null && "review".equals(run.role)) {
            return REVIEW;
        }
        String verdict = run != null && run.verdict != null ? run.verdict : machineVerdict;
        boolean devAgentOnIssue = machineAgentRunning && agentDevOnRun(run, machineRole);
        if (devAgentOnIssue) {
            if ("FAIL".equals(verdict)) {
                return FIX;
            }
            return DEV;
        }
        if (pull != null && pull.merged) {
            return MERGE;
        }
        if (pull != null && pull.open && !pull.draft) {
            if ("PASS".equals(verdict)) {
                return MERGE;
            }
            return WAIT_CI;
        }
        return DEV;
    }

    public static String forMachineFields(boolean machineAgentRunning, String machineVerdict,
                                          String machineRole, PullRow pull) {
        if (machineAgentRunning) {
            if ("FAIL".equals(machineVerdict)) {
                return FIX;
            }
            if ("review".equals(machineRole)) {
                return REVIEW;
            }
            return DEV;
        }
        if (pull != null && pull.merged) {
            return MERGE;
        }
        if (pull != null && pull.open && !pull.draft) {
            if ("PASS".equals(machineVerdict)) {
                return MERGE;
            }
            return WAIT_CI;
        }
        return DEV;
    }

    public static String forGithubItem(Item item, PullRow pull) {
        if (pull != null && pull.open && !pull.draft) {
            return WAIT_CI;
        }
        if (item != null && DevStatusModels.STATUS_REVIEW.equals(item.status)) {
            return WAIT_CI;
        }
        return DEV;
    }

    private static boolean agentDevOnRun(DevMachineStore.RunEntry run, String machineRole) {
        if (run == null) {
            return !"review".equals(machineRole);
        }
        if ("review".equals(run.role)) {
            return false;
        }
        if (run.model != null && !run.model.isEmpty()) {
            return true;
        }
        return run.round > 0;
    }
}