package com.example;

import java.io.InputStream;
import java.util.Properties;

/** 桌面端版本，來源為 pom.xml（resources filtering）。 */
public final class AppVersion {

    public static final String VERSION = load();

    private static String load() {
        try (InputStream in = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (in == null) return "dev";
            Properties props = new Properties();
            props.load(in);
            String value = props.getProperty("version", "dev").trim();
            if (value.isEmpty() || value.contains("${")) return "dev";
            return value;
        } catch (Exception e) {
            return "dev";
        }
    }

    private AppVersion() {}
}
