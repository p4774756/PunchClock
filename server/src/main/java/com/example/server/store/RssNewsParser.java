package com.example.server.store;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 安全解析 RSS / Atom 標題；關閉 DTD 與外部實體。 */
public final class RssNewsParser {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY_NUM = Pattern.compile("&#(\\d+);");
    private static final Pattern ENTITY_HEX = Pattern.compile("&#x([0-9a-fA-F]+);");
    private static final Pattern SOURCE_SUFFIX = Pattern.compile("\\s*[-–—|｜]\\s*[^\\s].{0,40}$");

    private RssNewsParser() {
    }

    public static List<String> parseHeadlines(byte[] body, int maxItems) throws IOException {
        if (body == null || body.length == 0) {
            return List.of();
        }
        int limit = Math.min(body.length, NewsFilterConstants.MAX_RESPONSE_BYTES);
        String xml = new String(body, 0, limit, StandardCharsets.UTF_8);
        List<String> raw = parseTitles(xml);
        List<String> out = new ArrayList<>();
        for (String title : raw) {
            String cleaned = cleanTitle(title);
            if (cleaned.isEmpty() || NewsFilterConstants.isBlockedTitle(cleaned)) {
                continue;
            }
            out.add(cleaned);
            if (out.size() >= maxItems) {
                break;
            }
        }
        return out;
    }

    static String cleanTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        t = HTML_TAG.matcher(t).replaceAll("");
        t = decodeEntities(t);
        t = SOURCE_SUFFIX.matcher(t).replaceAll("");
        t = t.replaceAll("\\s+", " ").trim();
        t = stripControl(t);
        if (t.isEmpty()) {
            return "";
        }
        int max = NewsFilterConstants.TITLE_MAX_CHARS;
        if (t.length() <= max) {
            return t;
        }
        if (max <= 1) {
            return "…";
        }
        return t.substring(0, max - 1) + "…";
    }

    private static String stripControl(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 || c == '\t') {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String decodeEntities(String s) {
        String t = s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
        Matcher num = ENTITY_NUM.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (num.find()) {
            int cp = Integer.parseInt(num.group(1));
            num.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        num.appendTail(sb);
        t = sb.toString();
        Matcher hex = ENTITY_HEX.matcher(t);
        sb = new StringBuffer();
        while (hex.find()) {
            int cp = Integer.parseInt(hex.group(1), 16);
            hex.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        hex.appendTail(sb);
        return sb.toString();
    }

    private static void setFactoryFeature(SAXParserFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (SAXException | javax.xml.parsers.ParserConfigurationException | RuntimeException ignored) {
            // 部分 JDK 不支援個別 feature
        }
    }

    private static void setReaderFeature(XMLReader reader, String name, boolean value) {
        try {
            reader.setFeature(name, value);
        } catch (SAXException | RuntimeException ignored) {
            // 部分 JDK 不支援個別 feature
        }
    }

    private static List<String> parseTitles(String xml) throws IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        setFactoryFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFactoryFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFactoryFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFactoryFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            factory.setXIncludeAware(false);
        } catch (RuntimeException ignored) {
            // JDK 差異
        }
        TitleCollector handler = new TitleCollector();
        try {
            SAXParser parser = factory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            setReaderFeature(reader, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setReaderFeature(reader, "http://xml.org/sax/features/external-general-entities", false);
            setReaderFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false);
            setReaderFeature(reader, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            reader.setContentHandler(handler);
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IOException("rss parse", ex);
        }
        return handler.titles;
    }

    /** 測試用：外部實體不應展開。 */
    public static boolean containsExternalEntityExpansion(byte[] body) throws IOException {
        try {
            parseHeadlines(body, 1);
            return false;
        } catch (IOException ex) {
            return true;
        }
    }

    private static final class TitleCollector extends DefaultHandler {
        private final List<String> titles = new ArrayList<>();
        private boolean inItem;
        private boolean inTitle;
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                inItem = true;
            } else if (inItem && "title".equalsIgnoreCase(name)) {
                inTitle = true;
                buf.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inTitle) {
                buf.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if ("title".equalsIgnoreCase(name) && inTitle) {
                titles.add(buf.toString());
                inTitle = false;
            } else if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                inItem = false;
            }
        }
    }

    /** 允許測試注入截斷後的位元組。 */
    public static byte[] truncateBody(byte[] body) {
        if (body == null) {
            return new byte[0];
        }
        if (body.length <= NewsFilterConstants.MAX_RESPONSE_BYTES) {
            return body;
        }
        byte[] cut = new byte[NewsFilterConstants.MAX_RESPONSE_BYTES];
        System.arraycopy(body, 0, cut, 0, cut.length);
        return cut;
    }

    public static List<String> parseHeadlinesFromString(String xml, int maxItems) throws IOException {
        return parseHeadlines(xml.getBytes(StandardCharsets.UTF_8), maxItems);
    }
}