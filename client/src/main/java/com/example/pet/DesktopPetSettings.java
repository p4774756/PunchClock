package com.example.pet;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * 桌面寵物開關與位置。存在 {@link Preferences} 使用者節點，重新啟動後讀回。
 */
public final class DesktopPetSettings {

    static final String KEY_ENABLED = "desktopPet.enabled";
    static final String KEY_X = "desktopPet.x";
    static final String KEY_Y = "desktopPet.y";
    static final String KEY_NOTIFY_MESSAGE = "desktopPet.notify.message";
    static final String KEY_NOTIFY_FILE = "desktopPet.notify.file";
    static final String KEY_NOTIFY_TASK = "desktopPet.notify.task";
    static final String KEY_NOTIFY_SOUND = "desktopPet.notify.sound";
    static final String KEY_CURSOR_REACT = "desktopPet.cursor.react";
    static final String KEY_CARE_SCHEMA = "desktopPet.care.schema";
    static final String KEY_CARE_ENABLED = "desktopPet.care.enabled";
    static final String KEY_CARE_HUNGER = "desktopPet.care.hunger";
    static final String KEY_CARE_MOOD = "desktopPet.care.mood";
    static final String KEY_CARE_ENERGY = "desktopPet.care.energy";
    static final String KEY_CARE_SAVED_AT = "desktopPet.care.savedAt";
    static final String KEY_CARE_LAST_FEED = "desktopPet.care.lastFeedAt";
    static final String KEY_CARE_LAST_PAT = "desktopPet.care.lastPatAt";
    static final String KEY_CARE_LAST_PLAY = "desktopPet.care.lastPlayAt";
    static final String KEY_CARE_LAST_TASK = "desktopPet.care.lastTaskAt";
    static final String KEY_CARE_LAST_CURSOR = "desktopPet.care.lastCursorAt";
    static final String KEY_CARE_FROZEN = "desktopPet.care.frozen";

    private final Preferences prefs;
    /** 執行期開關；不再寫入偏好，預設開啟。 */
    private boolean cursorReactLive = true;

    public DesktopPetSettings() {
        this(Preferences.userNodeForPackage(DesktopPetSettings.class));
    }

    public DesktopPetSettings(Preferences prefs) {
        if (prefs == null) {
            throw new IllegalArgumentException("prefs");
        }
        this.prefs = prefs;
    }

    public boolean isEnabled() {
        try {
            return prefs.getBoolean(KEY_ENABLED, false);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void setEnabled(boolean enabled) {
        try {
            prefs.putBoolean(KEY_ENABLED, enabled);
            flush();
        } catch (RuntimeException ex) {
            // 偏好儲存失敗時維持目前畫面，不把主程式一起帶走。
        }
    }

    public boolean hasPosition() {
        try {
            return prefs.get(KEY_X, null) != null && prefs.get(KEY_Y, null) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public int x() {
        return prefs.getInt(KEY_X, 0);
    }

    public int y() {
        return prefs.getInt(KEY_Y, 0);
    }

    public void savePosition(int x, int y) {
        try {
            prefs.putInt(KEY_X, x);
            prefs.putInt(KEY_Y, y);
            flush();
        } catch (RuntimeException ex) {
            // 位置只是下次出現的起點。
        }
    }

    /** 固定開啟；設定頁不再提供開關。 */
    public boolean notifyMessage() {
        return true;
    }

    public void setNotifyMessage(boolean on) {
        // 不再寫入偏好；舊鍵略過。
    }

    /** 固定開啟。 */
    public boolean notifyFile() {
        return true;
    }

    public void setNotifyFile(boolean on) {
    }

    /** 固定開啟。 */
    public boolean notifyTask() {
        return true;
    }

    public void setNotifyTask(boolean on) {
    }

    /** 固定關閉。 */
    public boolean notifySound() {
        return false;
    }

    public void setNotifySound(boolean on) {
    }

    /** 執行期 Cursor 反應；預設開啟，不讀寫偏好。 */
    public boolean cursorReact() {
        return cursorReactLive;
    }

    public void setCursorReact(boolean on) {
        cursorReactLive = on;
    }

    /** 養成。沒存過時預設開啟。總開關關掉時這個值仍保留。 */
    public boolean careEnabled() {
        return readFlag(KEY_CARE_ENABLED, true);
    }

    public void setCareEnabled(boolean on) {
        writeFlag(KEY_CARE_ENABLED, on);
    }

    /**
     * 讀回養成狀態。schema 不符、缺欄或壞值時回到預設，不丟例外。
     */
    public DesktopPetCare.Snapshot loadCare() {
        try {
            int schema = prefs.getInt(KEY_CARE_SCHEMA, 0);
            if (schema != DesktopPetCare.SCHEMA) {
                return defaultCareSnapshot();
            }
            return new DesktopPetCare.Snapshot(
                    DesktopPetCare.clamp(prefs.getInt(KEY_CARE_HUNGER, DesktopPetCare.DEFAULT)),
                    DesktopPetCare.clamp(prefs.getInt(KEY_CARE_MOOD, DesktopPetCare.DEFAULT)),
                    DesktopPetCare.clamp(prefs.getInt(KEY_CARE_ENERGY, DesktopPetCare.DEFAULT)),
                    Math.max(0L, prefs.getLong(KEY_CARE_SAVED_AT, 0L)),
                    Math.max(0L, prefs.getLong(KEY_CARE_LAST_FEED, 0L)),
                    Math.max(0L, prefs.getLong(KEY_CARE_LAST_PAT, 0L)),
                    Math.max(0L, prefs.getLong(KEY_CARE_LAST_PLAY, 0L)),
                    Math.max(0L, prefs.getLong(KEY_CARE_LAST_TASK, 0L)),
                    Math.max(0L, prefs.getLong(KEY_CARE_LAST_CURSOR, 0L)),
                    prefs.getBoolean(KEY_CARE_ENABLED, true),
                    prefs.getBoolean(KEY_CARE_FROZEN, false));
        } catch (RuntimeException ex) {
            return defaultCareSnapshot();
        }
    }

    public void saveCare(DesktopPetCare.Snapshot snap) {
        if (snap == null) {
            return;
        }
        try {
            prefs.putInt(KEY_CARE_SCHEMA, DesktopPetCare.SCHEMA);
            prefs.putBoolean(KEY_CARE_ENABLED, snap.enabled);
            prefs.putBoolean(KEY_CARE_FROZEN, snap.frozen);
            prefs.putInt(KEY_CARE_HUNGER, DesktopPetCare.clamp(snap.hunger));
            prefs.putInt(KEY_CARE_MOOD, DesktopPetCare.clamp(snap.mood));
            prefs.putInt(KEY_CARE_ENERGY, DesktopPetCare.clamp(snap.energy));
            prefs.putLong(KEY_CARE_SAVED_AT, Math.max(0L, snap.savedAt));
            prefs.putLong(KEY_CARE_LAST_FEED, Math.max(0L, snap.lastFeedAt));
            prefs.putLong(KEY_CARE_LAST_PAT, Math.max(0L, snap.lastPatAt));
            prefs.putLong(KEY_CARE_LAST_PLAY, Math.max(0L, snap.lastPlayAt));
            prefs.putLong(KEY_CARE_LAST_TASK, Math.max(0L, snap.lastTaskAt));
            prefs.putLong(KEY_CARE_LAST_CURSOR, Math.max(0L, snap.lastCursorAt));
            flush();
        } catch (RuntimeException ex) {
            // 養成寫入失敗時維持畫面，下次再試。
        }
    }

    private static DesktopPetCare.Snapshot defaultCareSnapshot() {
        long now = System.currentTimeMillis();
        return new DesktopPetCare.Snapshot(
                DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT, DesktopPetCare.DEFAULT,
                now, 0L, 0L, 0L, 0L, 0L, true, false);
    }

    private boolean readFlag(String key, boolean fallback) {
        try {
            return prefs.getBoolean(key, fallback);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private void writeFlag(String key, boolean on) {
        try {
            prefs.putBoolean(key, on);
            flush();
        } catch (RuntimeException ex) {
            // 這次沒寫入時，下次仍用上一次成功的值。
        }
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            // 下次啟動再讀到上一次成功寫入的值。
        }
    }
}
