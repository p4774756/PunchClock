package com.example.server.store;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** RSS 2.0 解析；標題去「 - 來源」後綴、依標題去重。 */
public final class NewsRssParser {

    private NewsRssParser() {}

    public static List<NewsModels.Item> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagName("item");
            List<NewsModels.Item> out = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element item = (Element) node;
                String titleRaw = text(item, "title");
                String link = text(item, "link");
                String pub = normalizePubDate(text(item, "pubDate"));
                String source = sourceFromItem(item);
                if (titleRaw.isBlank() || link.isBlank()) {
                    continue;
                }
                if (source.isBlank()) {
                    source = sourceFromTitleSuffix(titleRaw);
                }
                String title = stripTitleSuffix(titleRaw, source);
                if (title.isBlank()) {
                    continue;
                }
                out.add(new NewsModels.Item(title, source, pub, link));
            }
            return out;
        } catch (Exception ex) {
            throw new IllegalArgumentException("rss parse", ex);
        }
    }

    public static List<NewsModels.Item> dedupeAndLimit(List<NewsModels.Item> items, int max) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int cap = max > 0 ? max : NewsService.MAX_ITEMS;
        Set<String> seen = new LinkedHashSet<>();
        List<NewsModels.Item> out = new ArrayList<>();
        for (NewsModels.Item item : items) {
            if (item == null) {
                continue;
            }
            String key = item.title.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            out.add(item);
            if (out.size() >= cap) {
                break;
            }
        }
        return out;
    }

    static String stripTitleSuffix(String title, String source) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (source != null && !source.isBlank()) {
            String suffix = " - " + source.trim();
            if (t.endsWith(suffix)) {
                return t.substring(0, t.length() - suffix.length()).trim();
            }
        }
        int idx = t.lastIndexOf(" - ");
        if (idx > 0) {
            return t.substring(0, idx).trim();
        }
        return t;
    }

    private static String sourceFromTitleSuffix(String title) {
        int idx = title.lastIndexOf(" - ");
        if (idx <= 0 || idx >= title.length() - 3) {
            return "";
        }
        return title.substring(idx + 3).trim();
    }

    private static String sourceFromItem(Element item) {
        NodeList sources = item.getElementsByTagName("source");
        if (sources.getLength() == 0) {
            return "";
        }
        Node n = sources.item(0);
        return n == null ? "" : n.getTextContent().trim();
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return "";
        }
        Node n = list.item(0);
        return n == null ? "" : n.getTextContent().trim();
    }

    private static String normalizePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return Instant.parse(raw.trim()).toString();
        } catch (DateTimeParseException ignored) {
            return raw.trim();
        }
    }
}