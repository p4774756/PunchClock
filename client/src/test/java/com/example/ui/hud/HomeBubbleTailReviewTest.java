package com.example.ui.hud;

import com.example.ui.CandyTheme;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** 首頁泡泡漫畫尖角尾巴；PNG 僅在 SHOTS_BUBBLE_TAIL_DIR / shots.bubbleTail.dir 時寫檔。 */
public class HomeBubbleTailReviewTest {

  private static final Color PANEL = new Color(248, 248, 244, 240);
  private static final Color BG = new Color(36, 52, 96);
  private static final String SAMPLE = "需要幫忙就叫我吧";

  @Test
  public void comicTailPointsDownLeftWithoutSeamLine() throws Exception {
    double scale = 2;
    Rectangle stage = new Rectangle(0, 0, 400, 220);
    int w = (int) Math.round(stage.width * scale);
    int h = (int) Math.round(stage.height * scale);
    BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = buf.createGraphics();
    Rectangle box;
    Point mouth;
    try {
      g.scale(scale, scale);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(BG);
      g.fillRect(0, 0, stage.width, stage.height);

      HomeBoard board = new HomeBoard();
      board.layout(stage);
      board.setReducedMotion(true);
      board.setIllustrationContext(HomeIllustration.ROOM_B, 800, 1067, new Rectangle());
      board.setBubble(new HomeBubbleFormat.BubbleLine(SAMPLE, false));
      board.paint(g, 0);
      box = board.hotArea(HomeBoard.BUBBLE);
      box.grow(-4, -4);
      HomeBubbleAnchor.Placement placement =
          HomeBubbleAnchor.placeBubble(
              stage, HomeIllustration.ROOM_B, 800, 1067, box.width, box.height, new Rectangle());
      mouth = placement.mouth;
    } finally {
      g.dispose();
    }

    int baseY = (int) Math.round((box.y + box.height) * scale);
    int scanLeft = Math.min(box.x, mouth.x) - 24;
    int scanRight = box.x + box.width;
    int maxPanelBelow = deepestPanelY(buf, baseY, scanLeft, scanRight, scale);
    assertTrue("tail should extend well below bubble bottom", maxPanelBelow >= baseY + (int) (12 * scale));

    int tipRegionLeft = (int) Math.round((mouth.x - 14) * scale);
    int tipRegionRight = (int) Math.round((mouth.x + 6) * scale);
    int tipLowY = (int) Math.round((box.y + box.height + 10) * scale);
    int tipHighY = (int) Math.round((mouth.y + 6) * scale);
    assertTrue("tail tip should sit left of bubble center", mouth.x < box.x + box.width / 2);
    assertTrue(
        "panel fill should reach toward mouth below bubble",
        panelPixelNear(buf, tipRegionLeft, tipRegionRight, tipLowY, tipHighY));

    int seamY = baseY + (int) scale;
    int run = longestEdgeRun(buf, seamY, box.x + 20, box.x + box.width - 20, scale);
    assertTrue("bubble–tail junction should not show a long horizontal seam", run < (int) (28 * scale));

    maybeWritePng(buf);
  }

  private static void maybeWritePng(BufferedImage buf) throws Exception {
    String dir = System.getenv("SHOTS_BUBBLE_TAIL_DIR");
    if (dir == null || dir.isBlank()) {
      dir = System.getProperty("shots.bubbleTail.dir");
    }
    if (dir == null || dir.isBlank()) {
      return;
    }
    Path out = Path.of(dir);
    Files.createDirectories(out);
    ImageIO.write(buf, "png", out.resolve("bubble-tail-review.png").toFile());
  }

  private static boolean isPanel(Color c) {
    if (c.getAlpha() < 80) {
      return false;
    }
    if (Math.abs(c.getRed() - PANEL.getRed()) <= 20
        && Math.abs(c.getGreen() - PANEL.getGreen()) <= 20
        && Math.abs(c.getBlue() - PANEL.getBlue()) <= 24) {
      return true;
    }
    return c.getRed() >= 215 && c.getGreen() >= 215 && c.getBlue() >= 205;
  }

  private static boolean isEdge(Color c) {
    Color edge = CandyTheme.EDGE;
    return Math.abs(c.getRed() - edge.getRed()) <= 18
        && Math.abs(c.getGreen() - edge.getGreen()) <= 18
        && Math.abs(c.getBlue() - edge.getBlue()) <= 18
        && c.getAlpha() >= 160;
  }

  private static int deepestPanelY(BufferedImage buf, int fromY, int x0, int x1, double scale) {
    int maxY = fromY;
    int scanX0 = (int) Math.round(x0 * scale);
    int scanX1 = (int) Math.round(x1 * scale);
    for (int y = fromY; y < buf.getHeight(); y++) {
      for (int x = scanX0; x < scanX1; x++) {
        if (x < 0 || x >= buf.getWidth()) {
          continue;
        }
        if (isPanel(new Color(buf.getRGB(x, y), true))) {
          maxY = Math.max(maxY, y);
        }
      }
    }
    return maxY;
  }

  private static boolean panelPixelNear(
          BufferedImage buf, int x0, int x1, int y0, int y1) {
    for (int y = y0; y <= y1 && y < buf.getHeight(); y++) {
      for (int x = x0; x <= x1 && x < buf.getWidth(); x++) {
        if (isPanel(new Color(buf.getRGB(x, y), true))) {
          return true;
        }
      }
    }
    return false;
  }

  private static int longestEdgeRun(BufferedImage buf, int y, int x0, int x1, double scale) {
    int left = (int) Math.round(x0 * scale);
    int right = (int) Math.round(x1 * scale);
    y = Math.min(buf.getHeight() - 1, Math.max(0, y));
    int best = 0;
    int run = 0;
    for (int x = left; x <= right && x < buf.getWidth(); x++) {
      if (isEdge(new Color(buf.getRGB(x, y), true))) {
        run++;
        best = Math.max(best, run);
      } else {
        run = 0;
      }
    }
    return best;
  }
}