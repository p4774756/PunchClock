package com.example.pet;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * 依寵物視窗在螢幕上的位置，把選單放在旁邊（左右或上下），不與角色重疊且留在可見範圍內。
 */
final class PetMenuPlacement {

    static final int GAP = 8;

    private PetMenuPlacement() {
    }

    static Point placeBesidePet(
            int petX, int petY, int petW, int petH,
            int menuW, int menuH,
            Rectangle[] screens) {
        if (screens == null || screens.length == 0) {
            return new Point(petX + petW + GAP, petY);
        }
        Rectangle pet = new Rectangle(petX, petY, Math.max(1, petW), Math.max(1, petH));
        Rectangle host = hostScreen(pet, screens);
        if (host == null) {
            host = screens[0];
        }
        HorizontalSide hSide = pickHorizontal(pet, menuW, menuH, host, screens);
        Point seed = seedPoint(pet, menuW, menuH, hSide);
        Point clamped = DesktopPetWindow.clampToScreens(seed.x, seed.y, menuW, menuH, screens);
        if (!overlapsPet(clamped.x, clamped.y, menuW, menuH, pet)) {
            return clamped;
        }
        HorizontalSide other = hSide == HorizontalSide.RIGHT ? HorizontalSide.LEFT : HorizontalSide.RIGHT;
        Point altSeed = seedPoint(pet, menuW, menuH, other);
        Point alt = DesktopPetWindow.clampToScreens(altSeed.x, altSeed.y, menuW, menuH, screens);
        if (!overlapsPet(alt.x, alt.y, menuW, menuH, pet)) {
            return alt;
        }
        VerticalSide vSide = pickVertical(pet, menuW, menuH, host);
        Point below = DesktopPetWindow.clampToScreens(
                pet.x, pet.y + pet.height + GAP, menuW, menuH, screens);
        if (!overlapsPet(below.x, below.y, menuW, menuH, pet)) {
            return below;
        }
        Point above = DesktopPetWindow.clampToScreens(
                pet.x, pet.y - menuH - GAP, menuW, menuH, screens);
        if (!overlapsPet(above.x, above.y, menuW, menuH, pet)) {
            return above;
        }
        if (vSide == VerticalSide.BELOW) {
            return below;
        }
        return above;
    }

    private enum HorizontalSide { LEFT, RIGHT }

    private enum VerticalSide { ABOVE, BELOW }

    private static HorizontalSide pickHorizontal(
            Rectangle pet, int menuW, int menuH, Rectangle host, Rectangle[] screens) {
        int spaceRight = host.x + host.width - (pet.x + pet.width + GAP);
        int spaceLeft = pet.x - GAP - host.x;
        boolean fitsRight = spaceRight >= menuW;
        boolean fitsLeft = spaceLeft >= menuW;
        if (fitsRight && !fitsLeft) {
            return HorizontalSide.RIGHT;
        }
        if (fitsLeft && !fitsRight) {
            return HorizontalSide.LEFT;
        }
        if (spaceRight >= spaceLeft) {
            return HorizontalSide.RIGHT;
        }
        return HorizontalSide.LEFT;
    }

    private static VerticalSide pickVertical(Rectangle pet, int menuW, int menuH, Rectangle host) {
        int spaceBelow = host.y + host.height - (pet.y + pet.height + GAP);
        int spaceAbove = pet.y - GAP - host.y;
        if (spaceBelow >= menuH && spaceBelow >= spaceAbove) {
            return VerticalSide.BELOW;
        }
        if (spaceAbove >= menuH) {
            return VerticalSide.ABOVE;
        }
        return VerticalSide.BELOW;
    }

    private static Point seedPoint(Rectangle pet, int menuW, int menuH, HorizontalSide side) {
        int x = side == HorizontalSide.RIGHT
                ? pet.x + pet.width + GAP
                : pet.x - menuW - GAP;
        int y = pet.y + Math.max(0, (pet.height - menuH) / 2);
        return new Point(x, y);
    }

    private static boolean overlapsPet(int mx, int my, int menuW, int menuH, Rectangle pet) {
        Rectangle menu = new Rectangle(mx, my, menuW, menuH);
        Rectangle padded = new Rectangle(
                pet.x - GAP, pet.y - GAP, pet.width + GAP * 2, pet.height + GAP * 2);
        return menu.intersects(padded);
    }

    private static Rectangle hostScreen(Rectangle spot, Rectangle[] screens) {
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
        return target;
    }
}