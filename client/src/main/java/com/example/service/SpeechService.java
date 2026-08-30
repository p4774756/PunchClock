package com.example.service;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 朗讀英文台詞（macOS say / Windows SAPI / Linux espeak）。
 */
public final class SpeechService {

    private SpeechService() {
    }

    /** 從 "Joey: \"How you doin'?\"" 取出可朗讀英文 */
    public static String toSpeakableEnglish(String line) {
        if (line == null) return "";
        String text = line.trim();
        int colon = text.indexOf(':');
        if (colon >= 0 && colon < text.length() - 1) {
            text = text.substring(colon + 1).trim();
        }
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.trim();
    }

    public static void speakEnglish(String quoteLine, Consumer<String> logger) {
        String text = toSpeakableEnglish(quoteLine);
        if (text.isEmpty()) {
            log(logger, "[警告] 沒有可朗讀的英文台詞");
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Thread speaker = new Thread(() -> {
            try {
                if (os.contains("mac")) {
                    speakMac(text);
                } else if (os.contains("win")) {
                    speakWindows(text);
                } else {
                    speakLinux(text);
                }
                log(logger, "[朗讀] 已朗讀：" + text);
            } catch (Exception ex) {
                log(logger, "[警告] 朗讀失敗：" + ex.getMessage());
            }
        }, "speech-synth");
        speaker.setDaemon(true);
        speaker.start();
    }

    private static void speakMac(String text) throws Exception {
        new ProcessBuilder("say", text).start().waitFor();
    }

    private static void speakLinux(String text) throws Exception {
        try {
            new ProcessBuilder("espeak", text).start().waitFor();
        } catch (Exception first) {
            new ProcessBuilder("spd-say", text).start().waitFor();
        }
    }

    private static void speakWindows(String text) throws Exception {
        String escaped = text.replace("'", "''");
        String ps = "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$s.Speak('" + escaped + "')";
        new ProcessBuilder("powershell", "-NoProfile", "-Command", ps).start().waitFor();
    }

    private static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
