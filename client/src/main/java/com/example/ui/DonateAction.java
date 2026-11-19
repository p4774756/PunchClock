package com.example.ui;

import com.example.DonateLink;

import java.awt.Component;
import java.net.URI;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** 神器側欄「贊助」：已設定則開瀏覽器，否則提示準備中。 */
public final class DonateAction {

  static final String PREPARING_MESSAGE = "贊助頁準備中，謝謝你的心意 ♥";

  private final Function<String, String> resolveUrl;
  private final Function<String, Boolean> isConfigured;
  private final BiConsumer<URI, Component> browse;
  private final Runnable showPreparing;
  private final BiConsumer<Component, String> showUrlCopy;

  public DonateAction(
      Function<String, String> resolveUrl,
      Function<String, Boolean> isConfigured,
      BiConsumer<URI, Component> browse,
      Runnable showPreparing,
      BiConsumer<Component, String> showUrlCopy) {
    this.resolveUrl = resolveUrl;
    this.isConfigured = isConfigured;
    this.browse = browse;
    this.showPreparing = showPreparing;
    this.showUrlCopy = showUrlCopy;
  }

  public static DonateAction production() {
    return new DonateAction(
        key -> DonateLink.resolve(),
        DonateLink::isConfigured,
        (uri, p) -> {
          try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
              desktop.browse(uri);
            } else if (p != null) {
              UiFonts.showMessage(p, uri.toString(), "贊助網址", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
          } catch (Exception ex) {
            if (p != null) {
              UiFonts.showMessage(p, uri.toString(), "贊助網址", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
          }
        },
        () -> {},
        (p, url) -> {
          if (p != null) {
            UiFonts.showMessage(p, url, "贊助網址", javax.swing.JOptionPane.INFORMATION_MESSAGE);
          }
        });
  }

  public void run(Component parent) {
    String url = resolveUrl.apply("");
    if (!Boolean.TRUE.equals(isConfigured.apply(url))) {
      if (parent != null) {
        UiFonts.showMessage(
            parent, PREPARING_MESSAGE, "贊助", javax.swing.JOptionPane.INFORMATION_MESSAGE);
      } else {
        showPreparing.run();
      }
      return;
    }
    try {
      browse.accept(URI.create(url), parent);
    } catch (IllegalArgumentException ex) {
      showPreparing.run();
    }
  }
}