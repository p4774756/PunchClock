package com.example.server.store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 把飲品牆收到的簡報準備成 PDF。
 * PDF 原樣留下；Pages 取出檔內 PDF，沒有的話用存檔時附的預覽圖；PowerPoint 交給 {@link OfficePdfConverter}（同時只轉一份）。
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

    private static final String NO_PAGES_PREVIEW = "這份 Pages 沒有預覽";

    private static Result extractPagesPreview(byte[] data) throws IOException {
        Path tmp = Files.createTempFile("punchclock-pages-", ".pages");
        try {
            Files.write(tmp, data);
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                ZipEntry bestPdf = null;
                int bestPdfScore = 0;
                ZipEntry bestImage = null;
                int bestImageScore = 0;
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
                    String lower = safeZipName(entry.getName());
                    if (lower == null) {
                        continue;
                    }
                    if (entry.getSize() > PhotoStore.MAX_DOCUMENT_BYTES) {
                        if (pdfScore(lower) >= 0 || imageScore(lower) >= 0) {
                            oversized = true;
                        }
                        continue;
                    }
                    int pdf = pdfScore(lower);
                    if (pdf >= 0 && (bestPdf == null || pdf > bestPdfScore)) {
                        bestPdf = entry;
                        bestPdfScore = pdf;
                    }
                    int image = imageScore(lower);
                    if (image >= 0 && (bestImage == null || image > bestImageScore)) {
                        bestImage = entry;
                        bestImageScore = image;
                    }
                }
                if (bestPdf != null) {
                    try (InputStream in = zip.getInputStream(bestPdf)) {
                        Result preview = acceptPdf(readCapped(in), false);
                        if (preview.ok) {
                            return preview;
                        }
                    }
                }
                if (bestImage != null) {
                    try (InputStream in = zip.getInputStream(bestImage)) {
                        return acceptPdf(jpegToPdf(readCapped(in)), true);
                    }
                }
                return Result.fail(oversized ? "預覽超過 60 MB" : NO_PAGES_PREVIEW);
            } catch (ZipException ex) {
                return Result.fail(NO_PAGES_PREVIEW);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String safeZipName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
            return null;
        }
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) {
                return null;
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static int pdfScore(String lower) {
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

    /** 新版 Pages 單檔只附預覽圖，沒有整份 PDF。優先用整頁的 preview.jpg。 */
    private static int imageScore(String lower) {
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            return -1;
        }
        if (lower.equals("preview.jpg") || lower.endsWith("/preview.jpg")) {
            return 100;
        }
        if (lower.equals("preview-web.jpg") || lower.endsWith("/preview-web.jpg")) {
            return 80;
        }
        if (lower.contains("quicklook/") ) {
            return 60;
        }
        if (lower.equals("preview-micro.jpg") || lower.endsWith("/preview-micro.jpg")) {
            return 20;
        }
        return -1;
    }

    static byte[] jpegToPdf(byte[] jpeg) throws IOException {
        int[] info = jpegInfo(jpeg);
        if (info == null) {
            throw new IOException("預覽圖讀不到");
        }
        int width = info[0];
        int height = info[1];
        int components = info[2];
        String colorSpace = components == 1 ? "/DeviceGray" : components == 4 ? "/DeviceCMYK" : "/DeviceRGB";
        byte[] content = ("q\n" + width + " 0 0 " + height + " 0 0 cm\n/Im Do\nQ\n")
                .getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[6];
        offsets[1] = writePdfObject(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets[2] = writePdfObject(out, "2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n");
        offsets[3] = writePdfObject(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                + width + " " + height + "] /Contents 4 0 R /Resources << /XObject << /Im 5 0 R >> >> >>\nendobj\n");
        offsets[4] = out.size();
        out.write(("4 0 obj\n<< /Length " + content.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
        out.write(content);
        out.write("endstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        offsets[5] = out.size();
        out.write(("5 0 obj\n<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
                + " /ColorSpace " + colorSpace + " /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpeg.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
        out.write(jpeg);
        out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        int xref = out.size();
        out.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i <= 5; i++) {
            out.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static int writePdfObject(ByteArrayOutputStream out, String object) throws IOException {
        int offset = out.size();
        out.write(object.getBytes(StandardCharsets.US_ASCII));
        return offset;
    }

    static int[] jpegInfo(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8) {
            return null;
        }
        int i = 2;
        while (i + 9 < jpeg.length) {
            if ((jpeg[i] & 0xff) != 0xff) {
                i++;
                continue;
            }
            while (i < jpeg.length && (jpeg[i] & 0xff) == 0xff) {
                i++;
            }
            if (i >= jpeg.length) {
                return null;
            }
            int marker = jpeg[i] & 0xff;
            i++;
            if (marker == 0xd9 || marker == 0xda) {
                return null;
            }
            if (i + 1 >= jpeg.length) {
                return null;
            }
            int length = ((jpeg[i] & 0xff) << 8) | (jpeg[i + 1] & 0xff);
            if (length < 2 || i + length > jpeg.length) {
                return null;
            }
            boolean sof = marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
            if (sof && length >= 8) {
                int height = ((jpeg[i + 3] & 0xff) << 8) | (jpeg[i + 4] & 0xff);
                int width = ((jpeg[i + 5] & 0xff) << 8) | (jpeg[i + 6] & 0xff);
                int components = jpeg[i + 7] & 0xff;
                if (width > 0 && height > 0 && components > 0) {
                    return new int[] { width, height, components };
                }
            }
            i += length;
        }
        return null;
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
