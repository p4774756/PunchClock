package com.example.pet;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

/**
 * 桌面萌寵「操控模式」的鍵盤與跳躍邏輯，不含 Swing 視窗，方便單元測試。
 */
public final class DesktopPetControl {

    public static final String HINT = "方向鍵或 A/D 走路，空白鍵跳躍，Esc 離開";
    public static final String HINT_REFOCUS = "方向鍵或 A/D 走路，空白鍵跳躍，Esc 離開。先點一下萌寵再按鍵";
    public static final int WALK_STEP = DesktopPetAnimation.STEP;
    /** 跳躍頂點上移像素。 */
    public static final int JUMP_LIFT = 28;
    public static final int JUMP_TICKS = 14;

    public enum KeyAction {
        NONE, WALK_LEFT, WALK_RIGHT, JUMP, EXIT
    }

    private boolean active;
    private boolean walkLeft;
    private boolean walkRight;
    private int jumpTick;
    private boolean jumping;

    public boolean isActive() {
        return active;
    }

    public String hintText() {
        return hintText(true);
    }

    public String hintText(boolean windowFocused) {
        if (!active) {
            return null;
        }
        return windowFocused ? HINT : HINT_REFOCUS;
    }

    public void enter() {
        active = true;
        walkLeft = false;
        walkRight = false;
        cancelJump();
    }

    public void exit() {
        active = false;
        walkLeft = false;
        walkRight = false;
        cancelJump();
    }

    public int walkDirection() {
        if (!active) {
            return 0;
        }
        if (walkLeft && !walkRight) {
            return -1;
        }
        if (walkRight && !walkLeft) {
            return 1;
        }
        return 0;
    }

    public boolean isJumping() {
        return jumping;
    }

    /** 這一格視窗應額外上移的像素（非負）。 */
    public int jumpLift() {
        if (!jumping) {
            return 0;
        }
        return jumpOffsetAt(jumpTick);
    }

    public void applyKeyPressed(int keyCode) {
        if (!active) {
            return;
        }
        KeyAction action = keyAction(keyCode);
        if (action == KeyAction.WALK_LEFT) {
            walkLeft = true;
        } else if (action == KeyAction.WALK_RIGHT) {
            walkRight = true;
        } else if (action == KeyAction.JUMP) {
            startJump();
        } else if (action == KeyAction.EXIT) {
            exit();
        }
    }

    public void applyKeyReleased(int keyCode) {
        if (!active) {
            return;
        }
        KeyAction action = keyAction(keyCode);
        if (action == KeyAction.WALK_LEFT) {
            walkLeft = false;
        } else if (action == KeyAction.WALK_RIGHT) {
            walkRight = false;
        }
    }

    /** 每格 tick 推進跳躍；回傳是否仍在跳躍中。 */
    public boolean tickJump() {
        if (!jumping) {
            return false;
        }
        jumpTick++;
        if (jumpTick >= JUMP_TICKS) {
            jumping = false;
            jumpTick = 0;
            return false;
        }
        return true;
    }

    public void startJump() {
        if (!active || jumping) {
            return;
        }
        jumping = true;
        jumpTick = 0;
    }

    public void cancelJump() {
        jumping = false;
        jumpTick = 0;
    }

    public int horizontalDelta() {
        int dir = walkDirection();
        if (dir == 0 || jumping) {
            return 0;
        }
        return dir * WALK_STEP;
    }

    /**
     * 套水平步進與跳躍偏移，夾在螢幕可用範圍內。
     * {@code baseY} 是未跳躍時的視窗 Y；回傳的 Y 已扣掉 {@link #jumpLift()}。
     */
    public static Point clampedMove(
            int x, int baseY, int dx, int jumpLift,
            int windowWidth, int windowHeight, Rectangle[] screens) {
        int nextX = x + dx;
        Point clamped = DesktopPetWindow.clampToScreens(nextX, baseY, windowWidth, windowHeight, screens);
        int lift = Math.max(0, jumpLift);
        int maxY = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        if (screens != null) {
            for (Rectangle screen : screens) {
                if (screen == null) {
                    continue;
                }
                int top = screen.y;
                int bottom = screen.y + Math.max(0, screen.height - windowHeight);
                minY = Math.min(minY, top);
                maxY = Math.max(maxY, bottom);
            }
        }
        int y = clamped.y - lift;
        if (minY <= maxY) {
            y = Math.max(minY, Math.min(y, maxY));
        }
        return new Point(clamped.x, y);
    }

    static KeyAction keyAction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                return KeyAction.WALK_LEFT;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                return KeyAction.WALK_RIGHT;
            case KeyEvent.VK_SPACE:
                return KeyAction.JUMP;
            case KeyEvent.VK_ESCAPE:
                return KeyAction.EXIT;
            default:
                return KeyAction.NONE;
        }
    }

    static int jumpOffsetAt(int tick) {
        if (tick < 0 || tick >= JUMP_TICKS) {
            return 0;
        }
        double half = JUMP_TICKS / 2.0;
        double dist = Math.abs(tick - half) / half;
        double arc = 1 - dist * dist;
        return (int) Math.round(JUMP_LIFT * arc);
    }
}