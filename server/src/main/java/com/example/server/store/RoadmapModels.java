package com.example.server.store;

import java.util.Collections;
import java.util.List;

/** 施工進度路線圖（GitHub Milestone 對照）。 */
public final class RoadmapModels {

    private RoadmapModels() {
    }

    public static final class Item {
        public final String text;
        public final boolean done;
        public final Integer issue;
        public final Integer pr;

        public Item(String text, boolean done, Integer issue, Integer pr) {
            this.text = text == null ? "" : text;
            this.done = done;
            this.issue = issue;
            this.pr = pr;
        }
    }

    public static final class Phase {
        public final Integer phase;
        public final String title;
        public final boolean done;
        public final List<Item> items;
        public final int itemsDone;
        public final int itemsTotal;
        public final String progressNote;

        public Phase(Integer phase, String title, boolean done, List<Item> items,
                     int itemsDone, int itemsTotal, String progressNote) {
            this.phase = phase;
            this.title = title == null ? "" : title;
            this.done = done;
            this.items = items == null ? Collections.emptyList() : items;
            this.itemsDone = itemsDone;
            this.itemsTotal = itemsTotal;
            this.progressNote = progressNote == null || progressNote.isBlank() ? null : progressNote.trim();
        }
    }

    public static final class Document {
        public final Integer current;
        public final List<Phase> phases;
        /** 舊 i36「已完成：」行；Milestone 來源一律 null。 */
        public final String done;

        public Document(Integer current, List<Phase> phases, String done) {
            this.current = current;
            this.phases = phases == null ? Collections.emptyList() : phases;
            this.done = done;
        }
    }
}