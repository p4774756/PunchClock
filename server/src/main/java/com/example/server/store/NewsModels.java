package com.example.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Google 新聞 RSS 解析後的單則頭條（僅標題、來源、時間、連結）。 */
public final class NewsModels {

    public static final class Item {
        public final String title;
        public final String source;
        public final String publishedAt;
        public final String url;

        public Item(String title, String source, String publishedAt, String url) {
            this.title = title == null ? "" : title;
            this.source = source == null ? "" : source;
            this.publishedAt = publishedAt == null ? "" : publishedAt;
            this.url = url == null ? "" : url;
        }
    }

    public static final class Feed {
        public final List<Item> items;

        public Feed(List<Item> items) {
            this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    private NewsModels() {}
}