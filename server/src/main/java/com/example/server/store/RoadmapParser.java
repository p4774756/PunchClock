package com.example.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 從 GitHub issue 正文取出「## 分期」清單。格式不合的行略過，不讓整份失敗。
 */
public final class RoadmapParser {

    private static final Pattern ISSUE_HASH = Pattern.compile("#(\\d+)");
    private static final Pattern ISSUE_I = Pattern.compile("(?i)\\bi(\\d+)\\b");

    private RoadmapParser() {
    }

    public static final class Item {
        public final String text;
        public final boolean done;
        public final Integer issue;

        public Item(String text, boolean done, Integer issue) {
            this.text = text;
            this.done = done;
            this.issue = issue;
        }
    }

    public static final class Phase {
        public final Integer phase;
        public final String title;
        public final boolean done;
        public final List<Item> items;
        public final int itemsDone;
        public final int itemsTotal;
        /** 進行中分期可附短註（例如已完成的子項說明）。 */
        public final String progressNote;

        public Phase(Integer phase, String title, boolean done, List<Item> items,
                     int itemsDone, int itemsTotal, String progressNote) {
            this.phase = phase;
            this.title = title;
            this.done = done;
            this.items = items;
            this.itemsDone = itemsDone;
            this.itemsTotal = itemsTotal;
            this.progressNote = progressNote == null || progressNote.isBlank() ? null : progressNote.trim();
        }
    }

    public static final class Document {
        public final Integer current;
        public final List<Phase> phases;
        /** 「已完成：」開頭那一行原文；沒有就是 null。 */
        public final String done;

        public Document(Integer current, List<Phase> phases, String done) {
            this.current = current;
            this.phases = phases;
            this.done = done;
        }
    }

    public static Document parse(String markdown) {
        String source = markdown == null ? "" : markdown;
        String[] lines = source.split("\n", -1);
        String doneLine = null;
        boolean inSection = false;
        List<Phase> phases = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (doneLine == null && line.startsWith("已完成：")) {
                doneLine = line;
            }
            if (line.startsWith("##")) {
                if (inSection) {
                    break;
                }
                if ("## 分期".equals(line)) {
                    inSection = true;
                }
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (!isTopLevelChecklist(raw)) {
                continue;
            }
            ParsedPhase parsed = parsePhaseBlock(lines, i);
            if (parsed == null) {
                continue;
            }
            phases.add(parsed.phase);
            i = parsed.nextLineIndex - 1;
        }
        Integer current = null;
        for (int i = 0; i < phases.size(); i++) {
            Phase phase = phases.get(i);
            if (phase.phase != null && !phase.done) {
                current = phase.phase;
                break;
            }
        }
        return new Document(current, Collections.unmodifiableList(phases), doneLine);
    }

    private static final class ParsedPhase {
        final Phase phase;
        final int nextLineIndex;

        ParsedPhase(Phase phase, int nextLineIndex) {
            this.phase = phase;
            this.nextLineIndex = nextLineIndex;
        }
    }

    private static ParsedPhase parsePhaseBlock(String[] lines, int start) {
        String parentLine = lines[start].trim();
        ParentHead head = parseParentHead(parentLine);
        if (head == null) {
            return null;
        }
        List<Item> subItems = new ArrayList<>();
        int i = start + 1;
        for (; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.trim().isEmpty()) {
                continue;
            }
            if (isTopLevelChecklist(raw)) {
                break;
            }
            Item sub = parseIndentedItem(raw);
            if (sub == null) {
                break;
            }
            subItems.add(sub);
        }
        List<String> descItems = splitDescItems(head.afterColon);
        List<Item> items;
        int done;
        int total;
        String note;
        if (subItems.isEmpty()) {
            items = new ArrayList<>();
            if (descItems.isEmpty()) {
                total = 1;
                done = head.done ? 1 : 0;
            } else {
                for (int d = 0; d < descItems.size(); d++) {
                    items.add(new Item(descItems.get(d), head.done, extractIssue(descItems.get(d))));
                }
                total = descItems.size();
                done = head.done ? total : 0;
            }
            note = null;
        } else {
            items = new ArrayList<>(subItems);
            List<String> uncovered = new ArrayList<>();
            for (int d = 0; d < descItems.size(); d++) {
                String desc = descItems.get(d);
                if (!descCoveredBySubs(desc, subItems)) {
                    uncovered.add(desc);
                }
            }
            for (int u = 0; u < uncovered.size(); u++) {
                String text = uncovered.get(u);
                items.add(new Item(text, false, extractIssue(text)));
            }
            done = 0;
            for (int s = 0; s < subItems.size(); s++) {
                if (subItems.get(s).done) {
                    done++;
                }
            }
            total = subItems.size() + uncovered.size();
            note = pickProgressNote(subItems);
        }
        Phase phase = new Phase(
                head.number,
                head.title,
                head.done,
                Collections.unmodifiableList(items),
                done,
                total,
                note
        );
        return new ParsedPhase(phase, i);
    }

    private static String pickProgressNote(List<Item> subItems) {
        for (int i = 0; i < subItems.size(); i++) {
            Item item = subItems.get(i);
            if (item.done && item.text != null && !item.text.isEmpty()) {
                String text = item.text.trim();
                if (!text.endsWith("。") && !text.endsWith(".")) {
                    text = text + "。";
                }
                return text;
            }
        }
        return null;
    }

    private static boolean descCoveredBySubs(String desc, List<Item> subs) {
        String d = normalizeMatchKey(desc);
        if (d.isEmpty()) {
            return true;
        }
        for (int i = 0; i < subs.size(); i++) {
            String s = normalizeMatchKey(subs.get(i).text);
            if (s.contains(d) || d.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeMatchKey(String raw) {
        return stripMarkdown(raw).replaceAll("\\s+", "");
    }

    private static List<String> splitDescItems(String afterColon) {
        List<String> out = new ArrayList<>();
        if (afterColon == null || afterColon.isEmpty()) {
            return out;
        }
        String[] parts = afterColon.split("、", -1);
        for (int i = 0; i < parts.length; i++) {
            String item = stripMarkdown(parts[i]);
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    private static Item parseIndentedItem(String raw) {
        if (raw == null) {
            return null;
        }
        int dash = indexOfDashChecklist(raw);
        if (dash < 0) {
            return null;
        }
        String trimmed = raw.substring(dash).trim();
        if (trimmed.length() < 5 || !trimmed.startsWith("- [") || trimmed.charAt(4) != ']') {
            return null;
        }
        char mark = trimmed.charAt(3);
        if (mark != ' ' && mark != 'x' && mark != 'X') {
            return null;
        }
        boolean done = mark == 'x' || mark == 'X';
        String text = stripMarkdown(trimmed.substring(5).trim());
        if (text.isEmpty()) {
            return null;
        }
        return new Item(text, done, extractIssue(text));
    }

    private static int indexOfDashChecklist(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\t') {
                continue;
            }
            if (c == '-') {
                return i;
            }
            return -1;
        }
        return -1;
    }

    private static boolean isTopLevelChecklist(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        char c = raw.charAt(0);
        if (c == ' ' || c == '\t') {
            return false;
        }
        String t = raw.trim();
        return t.startsWith("- [") && t.length() > 4 && t.charAt(4) == ']';
    }

    private static final class ParentHead {
        final Integer number;
        final String title;
        final boolean done;
        final String afterColon;

        ParentHead(Integer number, String title, boolean done, String afterColon) {
            this.number = number;
            this.title = title;
            this.done = done;
            this.afterColon = afterColon;
        }
    }

    private static ParentHead parseParentHead(String line) {
        if (line.length() < 5 || !line.startsWith("- [") || line.charAt(4) != ']') {
            return null;
        }
        char mark = line.charAt(3);
        if (mark != ' ' && mark != 'x' && mark != 'X') {
            return null;
        }
        boolean done = mark == 'x' || mark == 'X';
        String rest = line.substring(5).trim();
        if (!rest.startsWith("**")) {
            return null;
        }
        int endBold = rest.indexOf("**", 2);
        if (endBold < 0) {
            return null;
        }
        String bold = rest.substring(2, endBold).trim();
        String after = rest.substring(endBold + 2).trim();
        if (after.startsWith("：")) {
            after = after.substring(1).trim();
        } else if (after.startsWith(":")) {
            after = after.substring(1).trim();
        } else {
            return null;
        }
        if (bold.isEmpty()) {
            return null;
        }
        Integer number = null;
        String title = bold;
        if (bold.startsWith("Phase ")) {
            String tail = bold.substring("Phase ".length()).trim();
            int space = tail.indexOf(' ');
            String digits = space < 0 ? tail : tail.substring(0, space);
            if (space > 0 && digits.chars().allMatch(Character::isDigit)) {
                try {
                    number = Integer.valueOf(digits);
                    title = tail.substring(space + 1).trim();
                } catch (NumberFormatException ignored) {
                    number = null;
                    title = bold;
                }
            }
        }
        if (title.isEmpty()) {
            return null;
        }
        return new ParentHead(number, title, done, after);
    }

    static Integer extractIssue(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher hash = ISSUE_HASH.matcher(text);
        if (hash.find()) {
            try {
                return Integer.valueOf(hash.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher im = ISSUE_I.matcher(text);
        if (im.find()) {
            try {
                return Integer.valueOf(im.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static String stripMarkdown(String raw) {
        String text = raw == null ? "" : raw.trim();
        while (!text.isEmpty() && isMarkdownMark(text.charAt(0))) {
            text = text.substring(1).trim();
        }
        while (!text.isEmpty() && isMarkdownMark(text.charAt(text.length() - 1))) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }

    private static boolean isMarkdownMark(char c) {
        return c == '*' || c == '_' || c == '`' || c == '#' || c == '>';
    }
}