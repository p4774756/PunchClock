package com.example.ui;

import java.awt.Point;
import java.awt.Rectangle;

/** 迷你橫條：拖曳與點擊展開的位移門檻與多螢幕邊界。 */
public final class MiniBarDrag {

  public static final int DRAG_THRESHOLD_PX = 5;

  private static final int MIN_VISIBLE = 24;

  private MiniBarDrag() {}

  /** 位移小於門檻視為點擊；否則為拖曳。 */
  public static boolean isClick(int dx, int dy) {
    return Math.abs(dx) < DRAG_THRESHOLD_PX && Math.abs(dy) < DRAG_THRESHOLD_PX;
  }

  public static boolean isClick(Point press, Point release) {
    if (press == null || release == null) {
      return true;
    }
    return isClick(release.x - press.x, release.y - release.y);
  }

  /** 拖曳後至少保留一部分在任一螢幕可見範圍內。 */
  public static Point clampLocation(
      int x, int y, int width, int height, Rectangle[] screens) {
    if (screens == null || screens.length == 0) {
      return new Point(x, y);
    }
    Rectangle window = new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
    Rectangle host = null;
    int best = -1;
    for (Rectangle screen : screens) {
      if (screen == null) {
        continue;
      }
      Rectangle hit = screen.intersection(window);
      int area = Math.max(0, hit.width) * Math.max(0, hit.height);
      if (area > best) {
        best = area;
        host = screen;
      }
    }
    if (host == null) {
      host = screens[0];
    }
    int minX = host.x - width + MIN_VISIBLE;
    int maxX = host.x + host.width - MIN_VISIBLE;
    int minY = host.y - height + MIN_VISIBLE;
    int maxY = host.y + host.height - MIN_VISIBLE;
    if (maxX < minX) {
      minX = maxX = host.x;
    }
    if (maxY < minY) {
      minY = maxY = host.y;
    }
    return new Point(Math.max(minX, Math.min(x, maxX)), Math.max(minY, Math.min(y, maxY)));
  }
}