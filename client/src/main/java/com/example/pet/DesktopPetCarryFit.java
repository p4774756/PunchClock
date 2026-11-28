package com.example.pet;

/**
 * 拖曳旋轉時縮放角色，讓四角留在畫布留白內。
 */
final class DesktopPetCarryFit {

    private DesktopPetCarryFit() {
    }

    static double fitScale(
            int spriteX, int spriteY, int spriteSize,
            int pivotX, int pivotY, double radians,
            int canvasWidth, int canvasHeight, int margin) {
        if (Math.abs(radians) < 1e-4) {
            return 1;
        }
        if (cornersFit(1, spriteX, spriteY, spriteSize, pivotX, pivotY, radians,
                canvasWidth, canvasHeight, margin)) {
            return 1;
        }
        double low = 0.45;
        double high = 1;
        for (int i = 0; i < 14; i++) {
            double mid = (low + high) / 2;
            if (cornersFit(mid, spriteX, spriteY, spriteSize, pivotX, pivotY, radians,
                    canvasWidth, canvasHeight, margin)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static boolean cornersFit(
            double scale, int spriteX, int spriteY, int spriteSize,
            int pivotX, int pivotY, double radians,
            int canvasWidth, int canvasHeight, int margin) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int[] xs = {spriteX, spriteX + spriteSize};
        int[] ys = {spriteY, spriteY + spriteSize};
        for (int x : xs) {
            for (int y : ys) {
                double dx = x - pivotX;
                double dy = y - pivotY;
                double rx = pivotX + scale * (dx * cos - dy * sin);
                double ry = pivotY + scale * (dx * sin + dy * cos);
                if (rx < margin || ry < margin
                        || rx > canvasWidth - margin || ry > canvasHeight - margin) {
                    return false;
                }
            }
        }
        return true;
    }

}
