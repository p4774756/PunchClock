package com.example.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 讀取 App jar 內 {@code /private-album/} 的插畫檔名（不對外公開）。 */
public final class PrivateAlbumCatalog {

    public static final String RESOURCE_DIR = "private-album";

    private PrivateAlbumCatalog() {
    }

    public static boolean isImageFileName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    public static List<String> listImageFileNames(Class<?> anchor) {
        Class<?> base = anchor == null ? PrivateAlbumCatalog.class : anchor;
        ClassLoader loader = base.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        TreeSet<String> names = new TreeSet<>();
        try {
            Enumeration<URL> roots = loader.getResources(RESOURCE_DIR);
            while (roots.hasMoreElements()) {
                collectFromUrl(roots.nextElement(), names);
            }
        } catch (IOException ignored) {
            // fall through
        }
        return new ArrayList<>(names);
    }

    private static void collectFromUrl(URL url, TreeSet<String> names) throws IOException {
        if (url == null) {
            return;
        }
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            Path dir;
            try {
                dir = Paths.get(url.toURI());
            } catch (Exception ex) {
                return;
            }
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    if (!Files.isRegularFile(entry)) {
                        continue;
                    }
                    String fileName = entry.getFileName().toString();
                    if (isImageFileName(fileName)) {
                        names.add(fileName);
                    }
                }
            }
            return;
        }
        if ("jar".equals(protocol)) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                String prefix = RESOURCE_DIR + "/";
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                    JarEntry entry = entries.nextElement();
                    String jarName = entry.getName();
                    if (!jarName.startsWith(prefix) || entry.isDirectory()) {
                        continue;
                    }
                    String fileName = jarName.substring(prefix.length());
                    if (isImageFileName(fileName)) {
                        names.add(fileName);
                    }
                }
            }
        }
    }

    public static BufferedImage loadImage(Class<?> anchor, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String path = "/" + RESOURCE_DIR + "/" + fileName;
        Class<?> base = anchor == null ? PrivateAlbumCatalog.class : anchor;
        try (InputStream in = base.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (IOException ex) {
            return null;
        }
    }
}