package com.example.pet;

import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.prefs.AbstractPreferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 寵物與主視窗左緣重疊時，macOS POPUP 會只露出右半；呼喚須先移開。 */
public class Issue347PetMainOverlapTest {

  @Test
  public void nudgeMovesPetOffMainLeftEdgeOverlap() {
    DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
    assertNotNull(frames);
    Rectangle shell = DesktopPetWindow.viewForPose(frames, DesktopPetWindow.shellFor(frames), new DesktopPetAnimation());
    Rectangle main = new Rectangle(180, 220, 720, 520);
    Rectangle[] screens = {new Rectangle(0, 0, 1440, 900)};
    int overlapX = main.x + shell.x;
    int overlapY = main.y + main.height - shell.height - 40;
    Point nudged = DesktopPetWindow.nudgeOutsideMainWindow(
        overlapX, overlapY, shell.width, shell.height, main, screens[0], screens);
    Rectangle placed = new Rectangle(nudged.x, nudged.y, shell.width, shell.height);
    assertFalse("pet must not straddle main left edge", placed.intersects(main));
    assertTrue(nudged.x >= main.x + main.width + DesktopPetWindow.MAIN_AVOID_GAP
        || nudged.x + shell.width <= main.x - DesktopPetWindow.MAIN_AVOID_GAP);
  }

  @Test
  public void summonPaintStaysWholeWhenMainWouldOverlap() throws Exception {
    DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
    assertNotNull(frames);
    Rectangle shell = DesktopPetWindow.viewForPose(frames, DesktopPetWindow.shellFor(frames), new DesktopPetAnimation());
    Rectangle main = new Rectangle(240, 160, 680, 500);
    Point bad = new Point(main.x + shell.x, main.y + 120);
    Point clear = DesktopPetWindow.nudgeOutsideMainWindow(
        bad.x, bad.y, shell.width, shell.height, main, new Rectangle(0, 0, 1600, 1000), new Rectangle[] {
            new Rectangle(0, 0, 1600, 1000)
        });
    assertFalse(new Rectangle(clear.x, clear.y, shell.width, shell.height).intersects(main));
    BufferedImage shot = DesktopPetWindow.paintMiluViewport(shell, frames, new DesktopPetAnimation(), true);
    Rectangle opaque = DesktopPetWindow.opaqueBounds(shot, 16);
    assertTrue("left half of milu should be painted", opaque.x < shell.width / 3);
    assertTrue("shadow should extend left of center", opaque.x + opaque.width > shell.width / 2);
    DesktopPetWindow.assertOpaqueWithinViewport(shot, 16);
  }

  @Test
  public void showPetAvoidsVisibleMainWindow() throws Exception {
    Assume.assumeFalse("needs display", GraphicsEnvironment.isHeadless());
    DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
    assertNotNull(frames);
    SwingUtilities.invokeAndWait(() -> {
      JFrame main = new JFrame("issue347-main");
      DesktopPetSettings settings = new DesktopPetSettings(new MemoryPreferences());
      DesktopPetManager manager = new DesktopPetManager(settings);
      try {
        main.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Rectangle shell = DesktopPetWindow.viewForPose(
            frames, DesktopPetWindow.shellFor(frames), new DesktopPetAnimation());
        Rectangle usable = new Rectangle(0, 25, 1440, 875);
        Point defaultSpot = DesktopPetWindow.defaultSpot(usable, shell.width, shell.height);
        main.setSize(420, 340);
        main.setLocation(defaultSpot.x - shell.width / 2, defaultSpot.y - 20);
        main.setVisible(true);
        manager.setMainBoundsSupplier(() -> {
          Point loc = main.getLocationOnScreen();
          return new Rectangle(loc.x, loc.y, main.getWidth(), main.getHeight());
        });
        manager.setCareEnabled(false);
        assertTrue(manager.setEnabled(true));
        DesktopPetWindow pet = manager.window();
        assertNotNull(pet);
        pet.useFrames(frames);
        Rectangle mainBounds = new Rectangle(
            main.getLocationOnScreen().x,
            main.getLocationOnScreen().y,
            main.getWidth(),
            main.getHeight());
        Rectangle petBounds = new Rectangle(
            pet.getLocationOnScreen().x, pet.getLocationOnScreen().y, pet.getWidth(), pet.getHeight());
        assertFalse("summoned pet must not overlap main", petBounds.intersects(mainBounds));
        Rectangle view = DesktopPetWindow.viewForPose(frames, DesktopPetWindow.shellFor(frames), pet.animation());
        BufferedImage shot = DesktopPetWindow.paintMiluViewport(view, frames, pet.animation(), true);
        Rectangle opaque = DesktopPetWindow.opaqueBounds(shot, 16);
        assertTrue(opaque.width > view.width / 2);
        DesktopPetWindow.assertOpaqueWithinViewport(shot, 16);
      } finally {
        manager.shutdown();
        main.setVisible(false);
        main.dispose();
      }
    });
  }

  @Test
  public void minimizedMainDoesNotRepositionPet() throws Exception {
    Assume.assumeFalse("needs display", GraphicsEnvironment.isHeadless());
    DesktopPetSpriteLoader.Frames frames = DesktopPetSpriteLoader.load();
    assertNotNull(frames);
    SwingUtilities.invokeAndWait(() -> {
      JFrame main = new JFrame("issue347-min");
      DesktopPetSettings settings = new DesktopPetSettings(new MemoryPreferences());
      DesktopPetManager manager = new DesktopPetManager(settings);
      try {
        main.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        main.setSize(400, 300);
        main.setLocation(60, 60);
        main.setVisible(true);
        manager.setMainBoundsSupplier(() -> {
          if ((main.getExtendedState() & Frame.ICONIFIED) != 0) {
            return null;
          }
          Point loc = main.getLocationOnScreen();
          return new Rectangle(loc.x, loc.y, main.getWidth(), main.getHeight());
        });
        manager.setCareEnabled(false);
        assertTrue(manager.setEnabled(true));
        DesktopPetWindow pet = manager.window();
        pet.useFrames(frames);
        int parkedX = main.getLocationOnScreen().x + 40;
        int parkedY = main.getLocationOnScreen().y + 40;
        pet.setLocation(parkedX, parkedY);
        main.setExtendedState(Frame.ICONIFIED);
        pet.nudgeAwayFromMainWindow();
        assertEqualsPoint(parkedX, parkedY, pet.getX(), pet.getY());
      } finally {
        manager.shutdown();
        main.setVisible(false);
        main.dispose();
      }
    });
  }

  private static void assertEqualsPoint(int expectedX, int expectedY, int actualX, int actualY) {
    assertTrue("x " + actualX + " expected " + expectedX, Math.abs(actualX - expectedX) < 2);
    assertTrue("y " + actualY + " expected " + expectedY, Math.abs(actualY - expectedY) < 2);
  }

  static final class MemoryPreferences extends AbstractPreferences {
    private final java.util.Map<String, String> map = new java.util.HashMap<>();

    MemoryPreferences() {
      super(null, "");
    }

    @Override
    protected void putSpi(String key, String value) {
      map.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
      return map.get(key);
    }

    @Override
    protected void removeSpi(String key) {
      map.remove(key);
    }

    @Override
    protected void removeNodeSpi() {}

    @Override
    protected String[] keysSpi() {
      return map.keySet().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() {
      return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
      return this;
    }

    @Override
    protected void syncSpi() {}

    @Override
    protected void flushSpi() {}
  }
}