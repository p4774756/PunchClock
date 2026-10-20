package com.example.ui;

import java.util.Locale;
import java.util.Properties;

/**
 * macOS 上 JDK 17 預設走 OpenGL 繪圖管線，Swing 會零星出現字消失（例如對話框裡的 John Wick 整段空白、
 * 選檔面板的 Downloads 變 D wnlo ads）。改走 Metal 管線後實測沒有再出現。
 * 使用者自己已經用 -Dsun.java2d.metal=... 指定就不覆蓋。
 * <p>
 * 這個類別刻意不引用任何 AWT／Swing 類別，才能在 {@code main} 第一行、AWT 初始化之前安全呼叫。
 */
public final class MacGraphicsWorkaround {

    static final String METAL_PROPERTY = "sun.java2d.metal";

    private MacGraphicsWorkaround() {
    }

    public static void apply() {
        apply(System.getProperties());
    }

    /** @return 是否有改動屬性 */
    static boolean apply(Properties props) {
        String os = props.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            return false;
        }
        if (props.getProperty(METAL_PROPERTY) != null) {
            return false;
        }
        props.setProperty(METAL_PROPERTY, "true");
        return true;
    }
}
