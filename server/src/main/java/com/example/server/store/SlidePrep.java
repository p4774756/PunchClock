package com.example.server.store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 把飲品牆收到的簡報準備成 PDF。
 * PDF 原樣留下；Pages 取出檔內預覽；PowerPoint 交給 {@link OfficePdfConverter}（同時只轉一份）。
 */
public final class SlidePrep {

    public enum Kind {
        IMAGE, PDF, PAGES, PPT, REJECT
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final byte[] pdf;

        private Result(boolean ok, String message, byte[] pdf) {
            this.ok = ok;
            this.message = message;
            this.pdf = pdf;
        }

        static Result success(byte[] pdf) {
            return new Result(true, "上傳成功", pdf);
        }

        static Result fail(String message) {
            return new Result(false, message, null);
        }
    }

    private final OfficePdfConverter office;
    private final Object converting = new Object();
    private boolean busy;

    public SlidePrep(OfficePdfConverter office) {
        this.office = office;
    }

    public static SlidePrep create() {
        return new SlidePrep(new LibreOfficePdfConverter());
    }

    public static Kind classify(String filename, String mime) {
        String ext = extension(filename);
        if (isImageExt(ext)) {
            return Kind.IMAGE;
        }
        if ("pdf".equals(ext)) {
            return Kind.PDF;
        }
        if ("pages".equals(ext)) {
            return Kind.PAGES;
        }
        if ("ppt".equals(ext) || "pptx".equals(ext)) {
            return Kind.PPT;
        }
        String type = mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) {
            return Kind.IMAGE;
        }
        if ("application/pdf".equals(type)) {
            return Kind.PDF;
        }
        return Kind.REJECT;
    }

    public Result prepare(String filename, byte[] data) {
        if (data == null || data.length == 0) {
            return Result.fail("檔案內容不可為空");
        }
        if (data.length > PhotoStore.MAX_DOCUMENT_BYTES) {
            return Result.fail("單份簡報不可超過 60 MB");
        }
        Kind kind = classify(filename, null);
        try {
            switch (kind) {
                case PDF:
                    return acceptPdf(data, false);
                case PAGES:
                    return extractPagesPreview(data);
                case PPT:
                    return convertPowerPoint(filename, data);
                default:
                    return Result.fail("僅支援 PDF、PowerPoint 或 Pages");
            }
        } catch (IOException ex) {
            String message = ex.getMessage();
            return Result.fail(message == null || message.isBlank() ? "轉檔失敗" : message);
        }
    }

    private Result convertPowerPoint(String filename, byte[] data) throws IOException {
        if (office == null) {
            return Result.fail("這台伺服器轉不了 PowerPoint");
        }
        synchronized (converting) {
            if (busy) {
                return Result.fail("正在轉另一份簡報，請稍後再試");
            }
            busy = true;
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("punchclock-slide-");
            String ext = "ppt".equals(extension(filename)) ? ".ppt" : ".pptx";
            Path input = work.resolve("input" + ext);
            Files.write(input, data);
            byte[] pdf;
            try {
                pdf = office.convert(input);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("轉檔被中斷");
            }
            return acceptPdf(pdf, true);
        } finally {
            deleteTree(work);
            synchronized (converting) {
                busy = false;
            }
        }
    }

    private static Result acceptPdf(byte[] data, boolean converted) {
        if (data == null || data.length == 0 || !isPdf(data)) {
            return Result.fail(converted ? "轉出來的不是 PDF" : "這不是 PDF");
        }
        if (data.length > PhotoStore.MAX_DOCUMENT_BYTES) {
            return Result.fail("單份簡報不可超過 60 MB");
        }
        return Result.success(data);
    }

    static boolean isPdf(byte[] data) {
        return data != null
                && data.length >= 5
                && data[0] == '%'
                && data[1] == 'P'
                && data[2] == 'D'
                && data[3] == 'F'
                && data[4] == '-';
    }

    private static Result extractPagesPreview(byte[] data) throws IOException {
        Path tmp = Files.createTempFile("punchclock-pages-", ".pages");
        try {
            Files.write(tmp, data);
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                ZipEntry best = null;
                int bestScore = 0;
                boolean oversized = false;
                int seen = 0;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (++seen > 8000) {
                        break;
                    }
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    int score = previewScore(entry.getName());
                    if (score < 0) {
                        continue;
                    }
                    if (entry.getSize() > PhotoStore.MAX_DOCUMENT_BYTES) {
                        oversized = true;
                        continue;
                    }
                    if (best == null || score > bestScore) {
                        best = entry;
                        bestScore = score;
                    }
                }
                if (best == null) {
                    return Result.fail(oversized
                            ? "預覽 PDF 超過 60 MB"
                            : "這份 Pages 沒有預覽，請先在 Pages 輸出成 PDF 再上傳");
                }
                try (InputStream in = zip.getInputStream(best)) {
                    Result preview = acceptPdf(readCapped(in), false);
                    if (!preview.ok && "這不是 PDF".equals(preview.message)) {
                        return Result.fail("這份 Pages 沒有預覽，請先在 Pages 輸出成 PDF 再上傳");
                    }
                    return preview;
                }
            } catch (ZipException ex) {
                return Result.fail("這份 Pages 沒有預覽，請先在 Pages 輸出成 PDF 再上傳");
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static int previewScore(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            return -1;
        }
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) {
                return -1;
            }
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".pdf")) {
            return -1;
        }
        if (lower.endsWith("quicklook/preview.pdf")) {
            return 100;
        }
        if (lower.equals("preview.pdf") || lower.endsWith("/preview.pdf")) {
            return 80;
        }
        return 10;
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > PhotoStore.MAX_DOCUMENT_BYTES) {
                throw new IOException("預覽 PDF 超過 60 MB");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isImageExt(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext)
                || "gif".equals(ext) || "webp".equals(ext) || "bmp".equals(ext)
                || "svg".equals(ext);
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount())).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 暫存清不掉不影響下一份
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }
}
