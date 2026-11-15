package com.example.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 從 GitHub issue 正文取出「## 分期」清單。格式不合的行略過，不讓整份失敗。
 */
public final class RoadmapParser {

    private RoadmapParser() {
    }

    public static final class Phase {
        public final Integer phase;
        public final String title;
        public final boolean done;
        public final List<String> items;

        public Phase(Integer phase, String title, boolean done, List<String> items) {
            this.phase = phase;
            this.title = title;
            this.done = done;
            this.items = items;
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
            String line = lines[i].trim();
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
            Phase phase = parsePhase(line);
            if (phase != null) {
                phases.add(phase);
            }
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

    private static Phase parsePhase(String line) {
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
        List<String> items = new ArrayList<>();
        if (!after.isEmpty()) {
            String[] parts = after.split("、", -1);
            for (int i = 0; i < parts.length; i++) {
                String item = stripMarkdown(parts[i]);
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }
        return new Phase(number, title, done, Collections.unmodifiableList(items));
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
