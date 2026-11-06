package com.example.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * macOS 不讓視窗上緣越過選單列。首頁角色的頭在視窗上緣，所以頭一頂到螢幕，紫色面板就上不去。
 * 這裡放行主視窗跨出螢幕上緣；拖曳時仍把面板上緣停在選單列，不讓整扇視窗飛出去。
 */
final class MacFrames {

    private static boolean attempted;

    private MacFrames() {
    }

    static void install() {
        if (attempted || !isMac()) {
            return;
        }
        attempted = true;
        try (InputStream in = MacFrames.class.getResourceAsStream("/native/libpunchclock.dylib")) {
            if (in == null) {
                return;
            }
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "punchclock-native");
            Files.createDirectories(dir);
            Path file = dir.resolve("libpunchclock.dylib");
            Path tmp = dir.resolve("libpunchclock.dylib.tmp");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException alreadyMapped) {
                Files.deleteIfExists(tmp);
            }
            System.load(file.toAbsolutePath().toString());
            allowOverflow();
        } catch (Throwable ignored) {
            // 載入失敗時維持系統原本的行為：視窗上緣停在選單列。
        }
    }

    /**
     * 視窗上緣最低可以到 {@code screenTop - overflowTop}，讓角色的頭離開螢幕，
     * 紫色面板的上緣則停在選單列。
     */
    static int lowestY(int screenTop, int overflowTop, int requestedY) {
        int min = screenTop - Math.max(0, overflowTop);
        return Math.max(min, requestedY);
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "");
        return os.startsWith("Mac") || os.startsWith("Darwin");
    }

    private static native void allowOverflow();
}
