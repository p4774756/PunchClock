package com.example.pet;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 依 Cursor hook 事件追蹤同時進行的 session，並對應桌寵要維持的狀態。
 * 只讀 {@code hook_event_name}／{@code conversation_id}／{@code status}，不碰 prompt 或檔名。
 */
public final class DesktopPetCursorStatus {

    public enum Phase {
        NONE, THINK, BUSY, WORRY
    }

    public enum Action {
        NONE,
        THINK,
        BUSY,
        WORRY_FLASH,
        CELEBRATE,
        CLEAR
    }

    public static final class Outcome {
        public final Action action;
        public final Phase hold;
        public final String bubble;

        Outcome(Action action, Phase hold, String bubble) {
            this.action = action;
            this.hold = hold;
            this.bubble = bubble;
        }
    }

    static final String BUBBLE_THINK = "想一下…";
    static final String BUBBLE_DONE = "做好了";
    static final String BUBBLE_ERROR = "出錯了…";

    private final Map<String, Phase> sessions = new LinkedHashMap<String, Phase>();

    public synchronized Outcome apply(String eventName, String conversationId, String status) {
        String event = normalize(eventName);
        String id = sessionKey(conversationId);
        if (event.isEmpty()) {
            return none();
        }
        if ("sessionstart".equals(event) || "beforesubmitprompt".equals(event)) {
            markThinking(id);
            return holdOutcome(BUBBLE_THINK);
        }
        if ("afteragentthought".equals(event)) {
            if (!sessions.containsKey(id)) {
                sessions.put(id, Phase.THINK);
            }
            return holdOutcome(null);
        }
        if ("afterfileedit".equals(event)
                || "aftershellexecution".equals(event)
                || "posttooluse".equals(event)
                || "aftermcpexecution".equals(event)) {
            sessions.put(id, Phase.BUSY);
            return holdOutcome(null);
        }
        if ("posttoolusefailure".equals(event)) {
            if (!sessions.containsKey(id)) {
                sessions.put(id, Phase.BUSY);
            }
            return new Outcome(Action.WORRY_FLASH, aggregate(), BUBBLE_ERROR);
        }
        if ("stop".equals(event)) {
            return applyStop(id, normalize(status));
        }
        if ("sessionend".equals(event)) {
            sessions.remove(id);
            Phase hold = aggregate();
            if (hold == Phase.NONE) {
                return new Outcome(Action.CLEAR, Phase.NONE, null);
            }
            return holdOutcome(null);
        }
        return none();
    }

    private Outcome holdOutcome(String bubble) {
        Phase hold = aggregate();
        if (hold == Phase.BUSY) {
            return new Outcome(Action.BUSY, hold, bubble);
        }
        if (hold == Phase.THINK) {
            return new Outcome(Action.THINK, hold, bubble);
        }
        return new Outcome(Action.CLEAR, Phase.NONE, bubble);
    }

    public synchronized Phase phase() {
        return aggregate();
    }

    public synchronized int activeCount() {
        return sessions.size();
    }

    public synchronized void clear() {
        sessions.clear();
    }

    private Outcome applyStop(String id, String status) {
        sessions.remove(id);
        Phase hold = aggregate();
        if ("error".equals(status)) {
            return new Outcome(Action.WORRY_FLASH, hold, BUBBLE_ERROR);
        }
        if ("aborted".equals(status)) {
            if (hold == Phase.NONE) {
                return new Outcome(Action.CLEAR, Phase.NONE, null);
            }
            if (hold == Phase.BUSY) {
                return new Outcome(Action.BUSY, hold, null);
            }
            return new Outcome(Action.THINK, hold, null);
        }
        // completed（或缺 status 時當完成）
        return new Outcome(Action.CELEBRATE, hold, BUBBLE_DONE);
    }

    private void markThinking(String id) {
        Phase current = sessions.get(id);
        if (current != Phase.BUSY) {
            sessions.put(id, Phase.THINK);
        }
    }

    private Phase aggregate() {
        if (sessions.isEmpty()) {
            return Phase.NONE;
        }
        for (Phase phase : sessions.values()) {
            if (phase == Phase.BUSY) {
                return Phase.BUSY;
            }
        }
        return Phase.THINK;
    }

    private static Outcome none() {
        return new Outcome(Action.NONE, Phase.NONE, null);
    }

    static String sessionKey(String conversationId) {
        if (conversationId == null) {
            return "_";
        }
        String trimmed = conversationId.trim();
        return trimmed.isEmpty() ? "_" : trimmed;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
