package com.example;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DonateLinkTest {

  @After
  public void tearDown() {
    DonateLink.setEnvLookupForTests(null);
    System.clearProperty("punchclock.donateUrl");
  }

  @Test
  public void defaultIsPlaceholder() {
    assertEquals(DonateLink.PLACEHOLDER_URL, DonateLink.resolve());
    assertFalse(DonateLink.isConfigured(DonateLink.resolve()));
  }

  @Test
  public void systemPropertyOverrides() {
    System.setProperty("punchclock.donateUrl", "https://ko-fi.com/demo");
    assertEquals("https://ko-fi.com/demo", DonateLink.resolve());
    assertTrue(DonateLink.isConfigured(DonateLink.resolve()));
  }

  @Test
  public void envOverridesWhenPropertyMissing() {
    DonateLink.setEnvLookupForTests(key -> {
      if ("PUNCHCLOCK_DONATE_URL".equals(key)) {
        return "https://portaly.cc/user/page";
      }
      return null;
    });
    assertEquals("https://portaly.cc/user/page", DonateLink.resolve());
  }

  @Test
  public void propertyBeatsEnv() {
    System.setProperty("punchclock.donateUrl", "https://ko-fi.com/a");
    DonateLink.setEnvLookupForTests(key -> "PUNCHCLOCK_DONATE_URL".equals(key) ? "https://ko-fi.com/b" : null);
    assertEquals("https://ko-fi.com/a", DonateLink.resolve());
  }

  @Test
  public void invalidUrlsFallBackToPlaceholder() {
    assertEquals(DonateLink.PLACEHOLDER_URL, DonateLink.acceptOrPlaceholder("http://ko-fi.com/x"));
    assertEquals(DonateLink.PLACEHOLDER_URL, DonateLink.acceptOrPlaceholder("https://x.com/a b"));
    assertEquals(DonateLink.PLACEHOLDER_URL, DonateLink.acceptOrPlaceholder("https://x.com/\"evil"));
    assertEquals(DonateLink.PLACEHOLDER_URL, DonateLink.acceptOrPlaceholder("https://x.com/<script>"));
    assertEquals(DonateLink.PLACEHOLDER_URL, DonateLink.acceptOrPlaceholder("https://" + "a".repeat(295)));
  }

  @Test
  public void isConfiguredRejectsPlaceholderAndExampleDomain() {
    assertFalse(DonateLink.isConfigured(DonateLink.PLACEHOLDER_URL));
    assertFalse(DonateLink.isConfigured("https://example.com/other"));
    assertFalse(DonateLink.isConfigured("https://sub.example.com/x"));
    assertTrue(DonateLink.isConfigured("https://ko-fi.com/test"));
  }
}