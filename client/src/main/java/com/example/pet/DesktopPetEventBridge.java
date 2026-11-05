package com.example.pet;

import javax.swing.SwingUtilities;
import java.util.ArrayDeque;

/**
 * 聊天、傳檔與任務只把事件交到這裡，不直接碰寵物視窗。
 * 顯示中的提醒不會被同一種事件疊加；拖曳或泡泡還沒結束時，最多留下三筆不同事件。
 */
public final class DesktopPetEventBridge {

    public static final int MAX_WAITING = 3;

    /** 寵物視窗那一側。實作必須在 EDT 上被呼叫。 */
    public interface Surface {
        boolean petEnabled();

        /** 看得到、沒在拖、也沒有另一則提醒。 */
        boolean readyForNotice();

        /** @return 泡泡是否真的畫出來了 */
        boolean showNotice(String phrase, Runnable open);

        void playSound();
    }

    private final DesktopPetSettings settings;
    private final DesktopPetNotificationPolicy policy;
    private final Surface surface;
    private final ArrayDeque<Item> waiting = new ArrayDeque<>();
    private DesktopPetEvent showing;

    public DesktopPetEventBridge(DesktopPetSettings settings, Surface surface) {
        if (settings == null || surface == null) {
            throw new IllegalArgumentException("bridge");
        }
        this.settings = settings;
        this.policy = new DesktopPetNotificationPolicy(settings);
        this.surface = surface;
    }

    public void offer(DesktopPetEvent event, Runnable open) {
        long now = System.currentTimeMillis();
        if (SwingUtilities.isEventDispatchThread()) {
            offerAt(event, open, now);
            return;
        }
        SwingUtilities.invokeLater(() -> offerAt(event, open, now));
    }

    /** 角色點擊把提醒換成打招呼時，只放開這一筆，等打招呼結束再播下一筆。 */
    public void releaseShowing() {
        if (SwingUtilities.isEventDispatchThread()) {
            showing = null;
            return;
        }
        SwingUtilities.invokeLater(() -> showing = null);
    }

    void noticeEndedAt(long nowMs) {
        showing = null;
        pumpAt(nowMs);
    }

    /** 提醒泡泡結束。接著才播下一筆，不會在打招呼泡泡還在時插隊。 */
    public void noticeEnded() {
        if (SwingUtilities.isEventDispatchThread()) {
            showing = null;
            pumpAt(System.currentTimeMillis());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            showing = null;
            pumpAt(System.currentTimeMillis());
        });
    }

    /** 拖曳放開或回到待機時，試播已排好的下一筆。 */
    public void pump() {
        if (SwingUtilities.isEventDispatchThread()) {
            pumpAt(System.currentTimeMillis());
            return;
        }
        SwingUtilities.invokeLater(() -> pumpAt(System.currentTimeMillis()));
    }

    /** 總開關關閉時丟掉排隊，重新開啟不補播。 */
    public void discard() {
        if (SwingUtilities.isEventDispatchThread()) {
            discardNow();
            return;
        }
        SwingUtilities.invokeLater(this::discardNow);
    }

    void offerAt(DesktopPetEvent event, Runnable open, long nowMs) {
        if (!policy.accepts(event, surface.petEnabled(), nowMs)) {
            return;
        }
        if (event == showing || queued(event)) {
            return;
        }
        Item item = new Item(event, open);
        if (showing == null && tryShow(item, nowMs)) {
            return;
        }
        if (!surface.petEnabled() || waiting.size() >= MAX_WAITING) {
            return;
        }
        waiting.addLast(item);
    }

    void pumpAt(long nowMs) {
        if (showing != null || waiting.isEmpty() || !surface.readyForNotice()) {
            return;
        }
        Item item = waiting.removeFirst();
        if (tryShow(item, nowMs)) {
            return;
        }
        if (surface.petEnabled() && !surface.readyForNotice()) {
            waiting.addFirst(item);
            return;
        }
        pumpAt(nowMs);
    }

    int waitingCount() {
        return waiting.size();
    }

    private void discardNow() {
        waiting.clear();
        showing = null;
        policy.forget();
    }

    private boolean tryShow(Item item, long nowMs) {
        if (!surface.readyForNotice() || !policy.accepts(item.event, surface.petEnabled(), nowMs)) {
            return false;
        }
        boolean shown;
        try {
            shown = surface.showNotice(item.event.phrase(), item.open);
        } catch (RuntimeException ex) {
            return false;
        }
        if (!shown) {
            return false;
        }
        policy.markShown(item.event, nowMs);
        showing = item.event;
        if (settings.notifySound()) {
            try {
                surface.playSound();
            } catch (RuntimeException ex) {
                // 聲音失敗時提醒已經在畫面上。
            }
        }
        return true;
    }

    private boolean queued(DesktopPetEvent event) {
        for (Item item : waiting) {
            if (item.event == event) {
                return true;
            }
        }
        return false;
    }

    private static final class Item {
        final DesktopPetEvent event;
        final Runnable open;

        Item(DesktopPetEvent event, Runnable open) {
            this.event = event;
            this.open = open;
        }
    }
}
