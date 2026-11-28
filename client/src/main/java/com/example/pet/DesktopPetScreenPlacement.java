package com.example.pet;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;

/**
 * 多螢幕邊界、拖曳貼合與避開主視窗的落點計算。
 */
final class DesktopPetScreenPlacement {

    static final int MAIN_AVOID_GAP = 6;

    private DesktopPetScreenPlacement() {
    }

    static Rectangle[] screenRects() {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        Rectangle[] screens = new Rectangle[devices.length];
        for (int i = 0; i < devices.length; i++) {
            screens[i] = usable(devices[i].getDefaultConfiguration());
        }
        return screens;
    }

    static Rectangle usable(GraphicsConfiguration gc) {
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(1, bounds.width - insets.left - insets.right),
                Math.max(1, bounds.height - insets.top - insets.bottom));
    }

    static Point defaultSpot(Rectangle usable, int windowWidth, int windowHeight) {
        int x = usable.x + Math.max(0, usable.width - windowWidth - DesktopPetWindow.MARGIN);
        int y = usable.y + Math.max(0, usable.height - windowHeight - DesktopPetWindow.MARGIN);
        return new Point(Math.max(usable.x, x), Math.max(usable.y, y));
    }

    /**
     * 拖曳時讓視窗上緣貼到選單列下緣。視窗已經縮到角色，上緣就是可見頭頂。
     */
    static Point clampDragToScreens(int x, int y, int windowWidth, int windowHeight, Rectangle[] screens) {
        return clampDragToScreens(x, y, windowWidth, windowHeight, 0, screens);
    }

    /**
     * @param visibleTop 視窗座標裡第一列可見像素。貼合角色後是 0，頭頂才能貼上選單列。
     */
    static Point clampDragToScreens(
            int x, int y, int windowWidth, int windowHeight, int visibleTop, Rectangle[] screens) {
        if (screens == null || screens.length == 0) {
            return new Point(x, y);
        }
        int contentTop = 0;
        if (windowHeight > 0) {
            contentTop = Math.max(0, Math.min(visibleTop, windowHeight - 1));
        }
        int contentHeight = Math.max(1, windowHeight - contentTop);
        Rectangle content = new Rectangle(x, y + contentTop, Math.max(1, windowWidth), contentHeight);
        Rectangle host = null;
        int bestArea = -1;
        for (Rectangle screen : screens) {
            if (screen == null) {
                continue;
            }
            Rectangle hit = screen.intersection(content);
            int area = Math.max(0, hit.width) * Math.max(0, hit.height);
            if (area > bestArea) {
                bestArea = area;
                host = screen;
            }
        }
        if (host == null) {
            host = screens[0];
        }
        int minVisible = 24;
        int minX = host.x - windowWidth + minVisible;
        int maxX = host.x + host.width - minVisible;
        int minY = host.y - contentTop;
        int maxY = host.y + host.height - Math.min(minVisible, windowHeight);
        if (maxX < minX) {
            minX = maxX = host.x;
        }
        if (maxY < minY) {
            minY = maxY = host.y;
        }
        return new Point(
                Math.max(minX, Math.min(x, maxX)),
                Math.max(minY, Math.min(y, maxY)));
    }

    static Point clampToScreens(int x, int y, int windowWidth, int windowHeight, Rectangle[] screens) {
        if (screens == null || screens.length == 0) {
            return new Point(x, y);
        }
        Rectangle spot = new Rectangle(x, y, windowWidth, windowHeight);
        Rectangle target = null;
        int bestArea = -1;
        for (Rectangle screen : screens) {
            if (screen == null) {
                continue;
            }
            Rectangle hit = screen.intersection(spot);
            int area = Math.max(0, hit.width) * Math.max(0, hit.height);
            if (area > bestArea) {
                bestArea = area;
                target = screen;
            }
        }
        if (target == null) {
            return new Point(x, y);
        }
        int maxX = target.x + Math.max(0, target.width - windowWidth);
        int maxY = target.y + Math.max(0, target.height - windowHeight);
        return new Point(
                Math.max(target.x, Math.min(x, maxX)),
                Math.max(target.y, Math.min(y, maxY)));
    }



    /**
     * 呼喚時把寵物移出主視窗外框，並把整扇留在螢幕裡。
     * 外框變大後的平移可能不再和主視窗相交，但仍會伸出螢幕右緣；不相交時也要拉回來。
     * 四個方向都放不進螢幕時，改挑重疊最少的落點。
     */
    static Point nudgeOutsideMainWindow(
            int x,
            int y,
            int windowWidth,
            int windowHeight,
            Rectangle main,
            Rectangle usable,
            Rectangle[] screens) {
        Point onScreen = clampToScreens(x, y, windowWidth, windowHeight, screens);
        if (main == null || main.isEmpty() || windowWidth < 1 || windowHeight < 1) {
            return onScreen;
        }
        Rectangle pet = new Rectangle(onScreen.x, onScreen.y, windowWidth, windowHeight);
        if (!pet.intersects(main)) {
            return onScreen;
        }
        x = onScreen.x;
        y = onScreen.y;
        int gap = DesktopPetScreenPlacement.MAIN_AVOID_GAP;
        Point bestHoriz = null;
        long bestHorizDistance = Long.MAX_VALUE;
        Point bestVert = null;
        long bestVertDistance = Long.MAX_VALUE;
        int[][] proposals = {
            {main.x + main.width + gap, y},
            {main.x - gap - windowWidth, y},
            {x, main.y + main.height + gap},
            {x, main.y - gap - windowHeight},
        };
        for (int[] proposal : proposals) {
            Point candidate = clampToScreens(proposal[0], proposal[1], windowWidth, windowHeight, screens);
            Rectangle placed = new Rectangle(candidate.x, candidate.y, windowWidth, windowHeight);
            if (placed.intersects(main)) {
                continue;
            }
            boolean horizClear = placed.x >= main.x + main.width + gap
                    || placed.x + placed.width <= main.x - gap;
            boolean vertClear = placed.y >= main.y + main.height + gap
                    || placed.y + placed.height <= main.y - gap;
            if (!horizClear && !vertClear) {
                continue;
            }
            long distance = distanceSquared(x, y, candidate.x, candidate.y);
            if (horizClear) {
                if (distance < bestHorizDistance) {
                    bestHorizDistance = distance;
                    bestHoriz = candidate;
                }
            } else if (distance < bestVertDistance) {
                bestVertDistance = distance;
                bestVert = candidate;
            }
        }
        if (bestHoriz != null) {
            return bestHoriz;
        }
        if (bestVert != null) {
            return bestVert;
        }
        return clearestSpot(x, y, windowWidth, windowHeight, main, usable, screens);
    }

    /**
     * 微移失敗時的落點。能完全避開就避開；螢幕剩下的縫比寵物小，就貼著空出的那一側，讓重疊面積最小。
     */
    static Point clearestSpot(
            int x,
            int y,
            int windowWidth,
            int windowHeight,
            Rectangle main,
            Rectangle usable,
            Rectangle[] screens) {
        java.util.ArrayList<Point> candidates = new java.util.ArrayList<Point>();
        if (usable != null && usable.width > 0 && usable.height > 0) {
            candidates.add(defaultSpot(usable, windowWidth, windowHeight));
            candidates.add(new Point(usable.x, usable.y));
            candidates.add(new Point(
                    Math.max(usable.x, usable.x + usable.width - windowWidth), usable.y));
            candidates.add(new Point(
                    usable.x, Math.max(usable.y, usable.y + usable.height - windowHeight)));
            candidates.add(new Point(
                    Math.max(usable.x, usable.x + usable.width - windowWidth),
                    Math.max(usable.y, usable.y + usable.height - windowHeight)));
        }
        if (screens != null) {
            for (Rectangle screen : screens) {
                if (screen == null) {
                    continue;
                }
                candidates.add(new Point(screen.x, screen.y));
                candidates.add(new Point(
                        Math.max(screen.x, screen.x + screen.width - windowWidth), screen.y));
                candidates.add(new Point(
                        screen.x, Math.max(screen.y, screen.y + screen.height - windowHeight)));
                candidates.add(new Point(
                        Math.max(screen.x, screen.x + screen.width - windowWidth),
                        Math.max(screen.y, screen.y + screen.height - windowHeight)));
            }
        }
        Point bestClear = null;
        long bestClearDistance = Long.MAX_VALUE;
        Point bestPartial = null;
        long bestPartialArea = Long.MAX_VALUE;
        long bestPartialDistance = Long.MAX_VALUE;
        for (Point raw : candidates) {
            Point candidate = clampToScreens(raw.x, raw.y, windowWidth, windowHeight, screens);
            Rectangle placed = new Rectangle(candidate.x, candidate.y, windowWidth, windowHeight);
            long distance = distanceSquared(x, y, candidate.x, candidate.y);
            if (!placed.intersects(main) && clearsMainWithGap(placed, main, DesktopPetScreenPlacement.MAIN_AVOID_GAP)) {
                if (distance < bestClearDistance) {
                    bestClearDistance = distance;
                    bestClear = candidate;
                }
                continue;
            }
            Rectangle overlap = placed.intersection(main);
            long area = (long) Math.max(0, overlap.width) * Math.max(0, overlap.height);
            if (area < bestPartialArea || (area == bestPartialArea && distance < bestPartialDistance)) {
                bestPartialArea = area;
                bestPartialDistance = distance;
                bestPartial = candidate;
            }
        }
        if (bestClear != null) {
            return bestClear;
        }
        if (bestPartial != null) {
            return bestPartial;
        }
        return new Point(x, y);
    }

    static boolean clearsMainWithGap(Rectangle placed, Rectangle main, int gap) {
        if (main == null || main.isEmpty() || placed == null) {
            return true;
        }
        if (placed.intersects(main)) {
            return false;
        }
        boolean horizClear = placed.x >= main.x + main.width + gap
                || placed.x + placed.width <= main.x - gap;
        boolean vertClear = placed.y >= main.y + main.height + gap
                || placed.y + placed.height <= main.y - gap;
        return horizClear || vertClear;
    }

    static long distanceSquared(int x1, int y1, int x2, int y2) {
        long dx = (long) x1 - x2;
        long dy = (long) y1 - y2;
        return dx * dx + dy * dy;
    }

    static HorizontalStep stepHorizontal(int x, int dx, int minX, int maxX) {
        if (maxX < minX) {
            maxX = minX;
        }
        if (dx == 0) {
            return new HorizontalStep(Math.max(minX, Math.min(maxX, x)), false);
        }
        int next = x + dx;
        if (next < minX || next > maxX) {
            int parked = Math.max(minX, Math.min(maxX, next));
            boolean walkingOut = (next < minX && dx < 0) || (next > maxX && dx > 0);
            return new HorizontalStep(parked, walkingOut);
        }
        return new HorizontalStep(next, false);
    }

    static final class HorizontalStep {
        final int x;
        final boolean bounce;

        HorizontalStep(int x, boolean bounce) {
            this.x = x;
            this.bounce = bounce;
        }
    }

}
