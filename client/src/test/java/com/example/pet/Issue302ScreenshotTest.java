package com.example.pet;

import com.example.ui.UiFonts;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本機驗收截圖；輸出目錄由環境變數 SHOTS302_DIR 指定（不 commit）。 */
public class Issue302ScreenshotTest {

  @Test
  public void captureMenuBesidePetLayout() throws Exception {
    String dir = System.getenv("SHOTS302_DIR");
    Assume.assumeTrue("SHOTS302_DIR not set", dir != null && !dir.isBlank());
    Path out = Path.of(dir);
    Files.createDirectories(out);

    long t = System.currentTimeMillis();
    DesktopPetCare care = new DesktopPetCare(() -> t);
    care.setEnabled(true, t);
    final BufferedImage[] shots = new BufferedImage[6];

    SwingUtilities.invokeAndWait(() -> {
      try {
        UiFonts.chinesePlain(13);
        PetContextMenu menu = PetContextMenu.build(true, true, false, care, t, noopListener());
        Dimension menuSize = menu.getPreferredSize();
        menu.setSize(menuSize);

        Rectangle screen = new Rectangle(0, 0, 1280, 800);
        int petW = 160;
        int petH = 200;

        int petX = 320;
        int petY = 280;
        Point menuAt = PetContextMenu.placeBesidePet(
                petX, petY, petW, petH, menuSize.width, menuSize.height, new Rectangle[] {screen});
        shots[0] = composeScene(petX, petY, petW, petH, menu, menuAt, screen);

        int petRightX = screen.width - petW - 20;
        Point menuLeft = PetContextMenu.placeBesidePet(
                petRightX, petY, petW, petH, menuSize.width, menuSize.height, new Rectangle[] {screen});
        shots[1] = composeScene(petRightX, petY, petW, petH, menu, menuLeft, screen);

        Rectangle wide = new Rectangle(0, 0, 1440, 900);
        int bodyW = 137;
        int bodyH = 176;
        Point hung = DesktopPetWindow.clampDragToScreens(1416, 480, bodyW, bodyH, new Rectangle[] {wide});
        Point settled = DesktopPetWindow.clampToScreens(hung.x, hung.y, bodyW, bodyH, new Rectangle[] {wide});
        Point menuAfterRelease = PetContextMenu.placeBesidePet(
                settled.x, settled.y, bodyW, bodyH, menuSize.width, menuSize.height, new Rectangle[] {wide});
        shots[2] = composeScene(settled.x, settled.y, bodyW, bodyH, menu, menuAfterRelease, wide);

        Rectangle narrow = new Rectangle(0, 0, 480, 900);
        Point below = PetContextMenu.placeBesidePet(
                150, 220, 180, 160, menuSize.width, menuSize.height, new Rectangle[] {narrow});
        shots[3] = composeScene(150, 220, 180, 160, menu, below, narrow);
        Point above = PetContextMenu.placeBesidePet(
                150, 620, 180, 160, menuSize.width, menuSize.height, new Rectangle[] {narrow});
        shots[4] = composeScene(150, 620, 180, 160, menu, above, narrow);

        Rectangle tall = new Rectangle(0, 0, 480, 1400);
        Point moreAbove = PetContextMenu.placeBesidePet(
                150, 780, 180, 100, menuSize.width, menuSize.height, new Rectangle[] {tall});
        shots[5] = composeScene(150, 780, 180, 100, menu, moreAbove, tall);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });

    ImageIO.write(shots[0], "png", out.resolve("01-menu-beside-pet-not-covered.png").toFile());
    ImageIO.write(shots[1], "png", out.resolve("02-menu-left-when-pet-near-right-edge.png").toFile());
    ImageIO.write(shots[2], "png", out.resolve("03-menu-follows-drag-release.png").toFile());
    ImageIO.write(shots[3], "png", out.resolve("04-menu-below-when-sides-do-not-fit.png").toFile());
    ImageIO.write(shots[4], "png", out.resolve("05-menu-above-when-below-would-cover.png").toFile());
    ImageIO.write(shots[5], "png", out.resolve("06-menu-above-when-upper-side-has-more-room.png").toFile());
  }

  private static BufferedImage composeScene(
          int petX, int petY, int petW, int petH,
          PetContextMenu menu, Point menuAt, Rectangle screen) {
    int pad = 24;
    int w = screen.width;
    int h = screen.height;
    BufferedImage img = new BufferedImage(w + pad * 2, h + pad * 2, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.translate(pad, pad);
      g.setColor(new Color(0xE8, 0xF4, 0xFC));
      g.fillRect(0, 0, w, h);
      g.setColor(new Color(0xFF, 0xD6, 0xE8, 180));
      g.fillRoundRect(petX, petY, petW, petH, 28, 28);
      g.setColor(new Color(0xF0, 0x9E, 0xC0));
      g.drawRoundRect(petX, petY, petW, petH, 28, 28);
      g.translate(menuAt.x, menuAt.y);
      menu.paint(g);
    } finally {
      g.dispose();
    }
    return img;
  }

  private static PetContextMenu.Listener noopListener() {
    return new PetContextMenu.Listener() {
      @Override public void onToggleControl(boolean enable) {}
      @Override public void onCare(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode) {}
      @Override public void onToggleDebug() {}
      @Override public void onDismiss() {}
    };
  }
}