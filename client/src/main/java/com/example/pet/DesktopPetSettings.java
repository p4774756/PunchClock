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

    private final Preferences prefs;

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

    /** 沒存過時預設開啟。總開關關掉時這個值仍保留。 */
    public boolean notifyMessage() {
        return readFlag(KEY_NOTIFY_MESSAGE, true);
    }

    public void setNotifyMessage(boolean on) {
        writeFlag(KEY_NOTIFY_MESSAGE, on);
    }

    /** 收到與送出檔案共用。沒存過時預設開啟。 */
    public boolean notifyFile() {
        return readFlag(KEY_NOTIFY_FILE, true);
    }

    public void setNotifyFile(boolean on) {
        writeFlag(KEY_NOTIFY_FILE, on);
    }

    /** 任務完成與排程時間到共用。沒存過時預設開啟。 */
    public boolean notifyTask() {
        return readFlag(KEY_NOTIFY_TASK, true);
    }

    public void setNotifyTask(boolean on) {
        writeFlag(KEY_NOTIFY_TASK, on);
    }

    /** 沒存過時預設關閉。 */
    public boolean notifySound() {
        return readFlag(KEY_NOTIFY_SOUND, false);
    }

    public void setNotifySound(boolean on) {
        writeFlag(KEY_NOTIFY_SOUND, on);
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
