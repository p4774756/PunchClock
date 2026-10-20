package com.example.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 同事聊天紀錄，只放在記憶體（關閉 App 就沒了）。每位同事各自一份，每份最多 {@link #MAX_ENTRIES} 筆。
 */
public final class PeerChatStore {

    public static final int MAX_ENTRIES = 100;

    public enum Kind {
        /** 文字、圖片訊息 */
        MESSAGE,
        /** 戳一下 */
        POKE,
        /** 系統提示（傳檔狀態等） */
        NOTE
    }

    public static final class Entry {
        public final String peerId;
        /** true 表示自己送出的 */
        public final boolean outgoing;
        public final Kind kind;
        public final String text;
        /** Base64 URL JPEG；沒有圖片是空字串 */
        public final String imageEncoded;
        public final long timeMs;

        Entry(String peerId, boolean outgoing, Kind kind, String text, String imageEncoded, long timeMs) {
            this.peerId = peerId;
            this.outgoing = outgoing;
            this.kind = kind;
            this.text = text == null ? "" : text;
            this.imageEncoded = imageEncoded == null ? "" : imageEncoded;
            this.timeMs = timeMs;
        }

        public boolean hasImage() {
            return !imageEncoded.isEmpty();
        }
    }

    private final Map<String, LinkedList<Entry>> chats = new HashMap<>();

    public synchronized Entry addMessage(String peerId, boolean outgoing, String text, String imageEncoded,
                                         Long timeMs) {
        return add(new Entry(peerId, outgoing, Kind.MESSAGE, text, imageEncoded, resolve(timeMs)));
    }

    public synchronized Entry addPoke(String peerId, boolean outgoing, Long timeMs) {
        return add(new Entry(peerId, outgoing, Kind.POKE, "", "", resolve(timeMs)));
    }

    public synchronized Entry addNote(String peerId, String text, Long timeMs) {
        return add(new Entry(peerId, true, Kind.NOTE, text, "", resolve(timeMs)));
    }

    /** 由舊到新；回傳的是複本，可放心在別的執行緒走訪。 */
    public synchronized List<Entry> entries(String peerId) {
        LinkedList<Entry> list = chats.get(peerId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public synchronized void clear(String peerId) {
        chats.remove(peerId);
    }

    public synchronized void clearAll() {
        chats.clear();
    }

    private Entry add(Entry entry) {
        LinkedList<Entry> list = chats.computeIfAbsent(entry.peerId, k -> new LinkedList<>());
        list.addLast(entry);
        while (list.size() > MAX_ENTRIES) {
            list.removeFirst();
        }
        return entry;
    }

    private static long resolve(Long timeMs) {
        return timeMs != null && timeMs > 0 ? timeMs : System.currentTimeMillis();
    }
}
