package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * client／shared 原始碼摘要。打包時寫進 client jar，伺服器用它判斷桌面端有沒有改過。
 * 不雜湊 jar 本身：Maven 每次打包的時間戳不同，原始碼沒變就不該提示更新。
 */
public final class SourceDigest {

    private SourceDigest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: SourceDigest <repo-root>");
            System.exit(2);
        }
        String digest = compute(Path.of(args[0]));
        if (digest.isEmpty()) {
            System.err.println("source digest: client/src/main or shared/src/main missing");
            System.exit(1);
        }
        System.out.print(digest);
    }

    /** 目錄不完整時回空字串。 */
    public static String compute(Path root) throws IOException {
        if (root == null) {
            return "";
        }
        Path clientMain = root.resolve("client/src/main");
        Path sharedMain = root.resolve("shared/src/main");
        if (!Files.isDirectory(clientMain) || !Files.isDirectory(sharedMain)) {
            return "";
        }
        List<Path> files = new ArrayList<>();
        collect(files, clientMain);
        collect(files, sharedMain);
        addFile(files, root.resolve("client/pom.xml"));
        addFile(files, root.resolve("shared/pom.xml"));
        files.sort(Comparator.comparing(path -> relative(root, path)));

        MessageDigest md = sha256();
        byte[] buf = new byte[8192];
        for (Path file : files) {
            md.update(relative(root, file).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            try (InputStream in = Files.newInputStream(file)) {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    md.update(buf, 0, n);
                }
            }
            md.update((byte) 0);
        }
        return hex(md.digest());
    }

    private static void collect(List<Path> files, Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(path -> addFile(files, path));
        }
    }

    private static void addFile(List<Path> files, Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        if (".DS_Store".equals(path.getFileName().toString())) {
            return;
        }
        files.add(path);
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
