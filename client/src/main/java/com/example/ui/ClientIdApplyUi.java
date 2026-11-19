package com.example.ui;

import com.example.PeerFileRules;

/** 好友與傳檔頁「裝置 ID」套用按鈕的純邏輯（方便單元測試）。 */
public final class ClientIdApplyUi {

    private ClientIdApplyUi() {
    }

    /**
     * 套用按鈕是否應亮起：草稿為非空白，且經 {@link PeerFileRules#normalizeClientId} 後與目前已生效 ID 不同。
     */
    public static boolean isApplyEnabled(String draftText, String effectiveClientId) {
        String draft = PeerFileRules.normalizeClientId(draftText);
        if (draft.isEmpty()) {
            return false;
        }
        String effective = PeerFileRules.normalizeClientId(effectiveClientId);
        return !draft.equals(effective);
    }
}