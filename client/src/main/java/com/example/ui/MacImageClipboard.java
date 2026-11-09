package com.example.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * macOS 圖片剪貼簿。JDK 的 imageFlavor 會把同一份 TIFF 同時標成 public.png／public.jpeg，
 * 其他 App 依標籤當 PNG 解析就報「格式不支援」。這裡改用 ImageIO 編真正的 PNG，
 * 再交給系統 osascript 以 «class PNGf» 寫入。
 */
final class MacImageClipboard {

    private static final long TIMEOUT_MS = 5000;

    private MacImageClipboard() {
    }

    /** @return 寫入成功；失敗時呼叫端改走 Java 剪貼簿。 */
    static boolean copyPng(BufferedImage image) {
        if (image == null) {
            return false;
        }
        Path png = null;
        try {
            png = Files.createTempFile("punchclock-clip-", ".png");
            if (!ImageIO.write(image, "png", png.toFile())) {
                return false;
            }
            Process process = new ProcessBuilder(command(png))
                    .redirectErrorStream(true)
                    .start();
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (png != null) {
                try {
                    Files.deleteIfExists(png);
                } catch (IOException ignored) {
                    // 暫存檔留在系統 temp，下次開機清掉即可。
                }
            }
        }
    }

    /** 路徑走 argv 傳入，不拼進 AppleScript 字串，避免引號問題。 */
    static List<String> command(Path png) {
        return Arrays.asList(
                "osascript",
                "-e", "on run argv",
                "-e", "set the clipboard to (read (POSIX file (item 1 of argv)) as «class PNGf»)",
                "-e", "end run",
                png.toAbsolutePath().toString());
    }
}
