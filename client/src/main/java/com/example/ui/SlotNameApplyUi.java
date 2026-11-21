package com.example.ui;

import com.example.model.WorkSlot;

/** 排程卡片「名稱」套用按鈕的純邏輯（方便單元測試）。 */
public final class SlotNameApplyUi {

    private SlotNameApplyUi() {
    }

  /**
   * 套用按鈕是否應亮起：草稿經正規化後與目前已儲存的名稱不同。
   */
    public static boolean isApplyEnabled(WorkSlot.Kind kind, String draftText, String savedName) {
        if (kind == null) {
            return false;
        }
        String draft = WorkSlot.normalizeName(kind, draftText);
        String saved = WorkSlot.normalizeName(kind, savedName);
        return !draft.equals(saved);
    }
}