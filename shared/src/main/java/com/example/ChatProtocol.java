package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 同事訊息裡的圖片欄位。多張圖用逗號串在一起；Base64 URL 不含逗號。
 */
public final class ChatProtocol {

    /** 一則同事訊息的文字上限。 */
    public static final int MAX_TEXT_CHARS = 10_000;
    /** 一則同事訊息裡幾張圖的 Base64 加總上限（含分隔逗號），約 0.9 MB JPEG。 */
    public static final int MAX_PEER_IMAGE_CHARS = 1_200_000;
    /** 一則同事訊息最多幾張圖。大小通常會先碰到 {@link #MAX_PEER_IMAGE_CHARS}。 */
    public static final int MAX_PEER_IMAGES = 6;
    /** Base64 URL 不會用到逗號，用來把多張圖串在同一個欄位。 */
    public static final char PEER_IMAGE_SEPARATOR = ',';

    private ChatProtocol() {
    }

    /** 多張圖串成一個欄位；空清單是空字串。 */
    public static String joinPeerImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (String image : images) {
            if (joined.length() > 0) {
                joined.append(PEER_IMAGE_SEPARATOR);
            }
            joined.append(image);
        }
        return joined.toString();
    }

    /**
     * 拆開一則訊息的圖片欄位。沒有圖時回傳空清單。
     * 格式不對（空段、非法字元、張數過多）回傳 {@code null}。
     */
    public static List<String> splitPeerImages(String field) {
        if (field == null || field.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = field.split(",", -1);
        if (parts.length > MAX_PEER_IMAGES) {
            return null;
        }
        List<String> images = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!isPeerImageToken(part)) {
                return null;
            }
            images.add(part);
        }
        return images;
    }

    /** 再加一張會超過張數或總字元時，回傳給使用者看的原因；可以加則回傳 {@code null}。 */
    public static String rejectExtraPeerImage(List<String> existing, String next) {
        if (!isPeerImageToken(next)) {
            return "圖片格式不正確";
        }
        int count = existing == null ? 0 : existing.size();
        if (count >= MAX_PEER_IMAGES) {
            return "一則訊息最多 " + MAX_PEER_IMAGES + " 張圖片";
        }
        int total = next.length();
        if (existing != null) {
            for (String image : existing) {
                total += 1 + image.length();
            }
        }
        if (total > MAX_PEER_IMAGE_CHARS) {
            return "這幾張圖加起來超過大小上限，這張沒有附上";
        }
        return null;
    }

    public static boolean isPeerImageToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
