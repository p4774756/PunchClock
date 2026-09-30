package com.example.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 傳檔取消旗標：UI 按「取消」時呼叫 {@link #cancel()}，各階段輪詢或註冊回呼中止。
 */
public final class TransferCancel {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** @return 第一次呼叫時為 true */
    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        for (Runnable listener : listeners) {
            if (listeners.remove(listener)) {
                runQuietly(listener);
            }
        }
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() throws TransferIo.CancelledException {
        if (cancelled.get()) {
            throw new TransferIo.CancelledException();
        }
    }

    /** 已取消時立即執行；每個回呼最多執行一次。 */
    public void onCancel(Runnable listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        if (cancelled.get() && listeners.remove(listener)) {
            runQuietly(listener);
        }
    }

    public void cancelOnCancel(Future<?> future) {
        if (future != null) {
            onCancel(() -> future.cancel(true));
        }
    }

    private static void runQuietly(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }
}
