package com.example.ui.game;

import com.example.game.GameRuleException;
import com.example.ui.NightTheme;
import com.example.ui.UiFonts;

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.Font;
import java.text.ParseException;

final class GameForms {

    private GameForms() {
    }

    static String points(int value) {
        return String.format("%,d", value);
    }

    static JLabel balanceLabel(Font boldFont) {
        JLabel label = new JLabel("點數 10,000");
        label.setFont(boldFont);
        label.setForeground(NightTheme.INK);
        return label;
    }

    static JSpinner betSpinner(Font font) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000, 10));
        spinner.setFont(font);
        return spinner;
    }

    static int readBet(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException ex) {
            throw new GameRuleException("下注額要是整數");
        }
        Object value = spinner.getValue();
        if (!(value instanceof Number)) {
            throw new GameRuleException("下注額要是整數");
        }
        int bet = ((Number) value).intValue();
        if (bet < 1) {
            throw new GameRuleException("下注額至少要 1 點");
        }
        return bet;
    }

    static void showBalance(JLabel label, int balance) {
        label.setText("點數 " + points(balance));
    }

    static JTextArea note(String text, Font font, int rows) {
        JTextArea area = new JTextArea(text, rows, 24);
        area.setFont(font);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setForeground(NightTheme.INK);
        area.setBorder(new EmptyBorder(0, 0, 0, 0));
        return area;
    }

    static JTextArea message(Font font) {
        JTextArea area = note(" ", font, 2);
        area.setForeground(NightTheme.MUTED);
        return area;
    }

    static void say(JTextArea area, String text, boolean problem) {
        area.setForeground(problem ? new java.awt.Color(255, 150, 164) : NightTheme.INK);
        area.setText(text == null || text.isBlank() ? " " : text);
        area.setCaretPosition(0);
    }

    static JLabel caption(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font == null ? UiFonts.chinesePlain(13) : font);
        label.setForeground(NightTheme.MUTED);
        return label;
    }
}
