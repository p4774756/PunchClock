package com.example.pet;

import com.example.ui.UiFonts;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PetMenuPlacementTest {

  private static final Rectangle SINGLE = new Rectangle(0, 0, 1280, 800);
  private static final int MENU_W = 224;
  private static final int MENU_H = 286;

  @Test
  public void placesMenuOnRightWhenSpaceAllows() {
    Rectangle[] screens = {SINGLE};
    int petX = 200;
    int petY = 300;
  int petW = 120;
    int petH = 160;
    Point placed = PetMenuPlacement.placeBesidePet(
            petX, petY, petW, petH, MENU_W, MENU_H, screens);
    assertTrue(placed.x >= petX + petW + PetMenuPlacement.GAP - 2);
    assertTrue(SINGLE.contains(new Rectangle(placed.x, placed.y, MENU_W, MENU_H)));
    assertNoOverlap(placed, petX, petY, petW, petH);
  }

  @Test
  public void placesMenuOnLeftNearRightScreenEdge() {
    Rectangle[] screens = {SINGLE};
  int petW = 140;
    int petH = 180;
    int petX = SINGLE.width - petW - 24;
    int petY = 400;
    Point placed = PetMenuPlacement.placeBesidePet(
            petX, petY, petW, petH, MENU_W, MENU_H, screens);
    assertTrue(placed.x + MENU_W <= petX - PetMenuPlacement.GAP + 2);
    assertTrue(SINGLE.contains(new Rectangle(placed.x, placed.y, MENU_W, MENU_H)));
    assertNoOverlap(placed, petX, petY, petW, petH);
  }

  @Test
  public void shiftsVerticallyWhenCenteredWouldClipBottom() {
    Rectangle[] screens = {SINGLE};
    int petW = 100;
    int petH = 120;
    int petX = 400;
    int petY = SINGLE.height - petH - 10;
    Point placed = PetMenuPlacement.placeBesidePet(
            petX, petY, petW, petH, MENU_W, MENU_H, screens);
    assertTrue(SINGLE.contains(new Rectangle(placed.x, placed.y, MENU_W, MENU_H)));
    assertNoOverlap(placed, petX, petY, petW, petH);
    assertTrue(placed.y + MENU_H <= SINGLE.y + SINGLE.height);
  }

  @Test
  public void staysInsideOverlappingScreenOnDualMonitor() {
    Rectangle[] staggered = {
            new Rectangle(0, 0, 1440, 900),
            new Rectangle(1440, -200, 1920, 1200)
    };
    int petX = 1380;
    int petY = 400;
    int petW = 100;
    int petH = 140;
    Point placed = PetMenuPlacement.placeBesidePet(
            petX, petY, petW, petH, MENU_W, MENU_H, staggered);
    Rectangle menu = new Rectangle(placed.x, placed.y, MENU_W, MENU_H);
    boolean onFirst = staggered[0].contains(menu);
    boolean onSecond = staggered[1].contains(menu);
    assertTrue(onFirst || onSecond);
    assertNoOverlap(placed, petX, petY, petW, petH);
  }

  @Test
  public void usesMeasuredMenuSizeFromPanel() throws Exception {
    long t = 1_700_000_000_000L;
    DesktopPetCare care = new DesktopPetCare(() -> t);
    care.setEnabled(true, t);
    final Dimension[] size = new Dimension[1];
    SwingUtilities.invokeAndWait(() -> {
      UiFonts.chinesePlain(13);
      PetContextMenu menu = PetContextMenu.build(true, false, false, care, t, noopListener());
      size[0] = menu.getPreferredSize();
    });
    Rectangle[] screens = {SINGLE};
    Point placed = PetContextMenu.placeBesidePet(
            900, 200, 160, 200, size[0].width, size[0].height, screens);
    assertTrue(SINGLE.contains(new Rectangle(placed.x, placed.y, size[0].width, size[0].height)));
    assertNoOverlap(placed, 900, 200, 160, 200);
  }

  @Test
  public void clampMenuOriginStillWorksForLegacyCallers() {
    Point corner = PetContextMenu.clampMenuOrigin(1200, 760, MENU_W, MENU_H, new Rectangle[] {SINGLE});
    assertEquals(1056, corner.x);
    assertEquals(514, corner.y);
  }

  private static void assertNoOverlap(Point menuOrigin, int petX, int petY, int petW, int petH) {
    Rectangle menu = new Rectangle(menuOrigin.x, menuOrigin.y, MENU_W, MENU_H);
    Rectangle pet = new Rectangle(petX, petY, petW, petH);
    Rectangle padded = new Rectangle(
            pet.x - PetMenuPlacement.GAP,
            pet.y - PetMenuPlacement.GAP,
            pet.width + PetMenuPlacement.GAP * 2,
            pet.height + PetMenuPlacement.GAP * 2);
    assertFalse(menu.intersects(padded));
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