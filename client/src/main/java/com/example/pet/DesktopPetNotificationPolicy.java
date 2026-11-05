package com.example.pet;

import java.util.EnumMap;
import java.util.Map;

/**
 * 決定一個事件現在能不能提醒。總開關、分類開關與冷卻都在這裡。
 * 關掉總開關時不記得待播事件；重新打開不會補播。
 */
public final class DesktopPetNotificationPolicy {

    /** 同一種事件兩次實際顯示之間至少隔這段時間。 */
    public static final long COOLDOWN_MS = 4_000L;

    private final DesktopPetSettings settings;
    private final Map<DesktopPetEvent, Long> shownAt = new EnumMap<>(DesktopPetEvent.class);

    public DesktopPetNotificationPolicy(DesktopPetSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings");
        }
        this.settings = settings;
    }

    public boolean accepts(DesktopPetEvent event, boolean petEnabled, long nowMs) {
        if (event == null || !petEnabled) {
            return false;
        }
        if (!categoryOn(event)) {
            return false;
        }
        Long last = shownAt.get(event);
        return last == null || nowMs - last >= COOLDOWN_MS;
    }

    public void markShown(DesktopPetEvent event, long nowMs) {
        if (event != null) {
            shownAt.put(event, nowMs);
        }
    }

    /** 總開關關掉時清掉冷卻，避免把舊事件留到下次。 */
    public void forget() {
        shownAt.clear();
    }

    private boolean categoryOn(DesktopPetEvent event) {
        switch (event) {
            case MESSAGE_RECEIVED:
                return settings.notifyMessage();
            case FILE_OFFERED:
            case FILE_RECEIVED:
            case FILE_SENT:
                return settings.notifyFile();
            case TASK_COMPLETED:
            case SCHEDULE_REMINDER:
                return settings.notifyTask();
            default:
                return false;
        }
    }
}
