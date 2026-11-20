package com.example.pet;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/** 操控模式鍵盤綁定（InputMap／ActionMap，WHEN_IN_FOCUSED_WINDOW）。 */
final class PetControlKeyBindings {

    interface Handler {
        boolean controlActive();

        void onKeyPressed(int vk);

        void onKeyReleased(int vk);

        boolean menuVisible();

        void hideMenu();

        void exitControl();
    }

    private PetControlKeyBindings() {
    }

    static void install(JComponent root, Handler handler) {
        InputMap pressed = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        InputMap released = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = root.getActionMap();
        bindWalkKey(pressed, released, actions, handler, KeyEvent.VK_LEFT);
        bindWalkKey(pressed, released, actions, handler, KeyEvent.VK_RIGHT);
        bindWalkKey(pressed, released, actions, handler, KeyEvent.VK_A);
        bindWalkKey(pressed, released, actions, handler, KeyEvent.VK_D);
        pressed.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "petJump");
        actions.put("petJump", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!handler.controlActive()) {
                    return;
                }
                handler.onKeyPressed(KeyEvent.VK_SPACE);
            }
        });
        pressed.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "petExitControl");
        actions.put("petExitControl", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (PetMenuDismiss.shouldCloseOnEscape(handler.menuVisible(), KeyEvent.VK_ESCAPE)) {
                    handler.hideMenu();
                    return;
                }
                if (!handler.controlActive()) {
                    return;
                }
                handler.exitControl();
            }
        });
    }

    static boolean bindingsPresent(JComponent root) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        return im.get(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false)) != null
                && im.get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false)) != null
                && im.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false)) != null
                && im.get(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)) != null;
    }

    private static void bindWalkKey(
            InputMap pressed, InputMap released, ActionMap actions, Handler handler, int vk) {
        String pressId = "petPress" + vk;
        String releaseId = "petRelease" + vk;
        pressed.put(KeyStroke.getKeyStroke(vk, 0, false), pressId);
        released.put(KeyStroke.getKeyStroke(vk, 0, true), releaseId);
        actions.put(pressId, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!handler.controlActive()) {
                    return;
                }
                handler.onKeyPressed(vk);
            }
        });
        actions.put(releaseId, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!handler.controlActive()) {
                    return;
                }
                handler.onKeyReleased(vk);
            }
        });
    }
}