package com.example.ui;

import com.example.DonateLink;
import org.junit.Test;

import java.awt.Component;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DonateActionTest {

  @Test
  public void placeholderShowsPreparingWithoutBrowse() {
    List<URI> browsed = new ArrayList<>();
    List<String> messages = new ArrayList<>();
    DonateAction action = new DonateAction(
        key -> DonateLink.PLACEHOLDER_URL,
        DonateLink::isConfigured,
        (uri, p) -> browsed.add(uri),
        () -> messages.add("prep"));

    action.run(null);

    assertTrue(browsed.isEmpty());
    assertEquals(List.of("prep"), messages);
  }

  @Test
  public void configuredOpensBrowser() {
    AtomicReference<URI> seen = new AtomicReference<>();
    DonateAction action = new DonateAction(
        key -> "https://ko-fi.com/demo",
        DonateLink::isConfigured,
        (uri, p) -> seen.set(uri),
        () -> {});

    action.run(null);

    assertEquals(URI.create("https://ko-fi.com/demo"), seen.get());
  }

  @Test
  public void unsupportedDesktopShowsUrl() {
    List<String> messages = new ArrayList<>();
    DonateAction action = new DonateAction(
        key -> "https://ko-fi.com/demo",
        DonateLink::isConfigured,
        (uri, p) -> actionShowUrlInstead(p, uri, messages),
        () -> {});

    action.run(null);

    assertEquals(List.of("https://ko-fi.com/demo"), messages);
  }

  private static void actionShowUrlInstead(Component parent, URI uri, List<String> messages) {
    messages.add(uri.toString());
  }
}