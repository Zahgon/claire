package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudflare.claire.dom.LocalStorage;
import com.cloudflare.claire.testing.FakeDocument;
import com.cloudflare.claire.testing.FakeLocalStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migrated 1:1 from {@code source/options.test.js}.
 *
 * <p>The source drives the page with jQuery against a two-checkbox fragment; here the same two
 * checkboxes are clicked directly on the document fake.
 */
class OptionsTest {

  @Test
  @DisplayName("update localStorage")
  void updateLocalStorage() {
    FakeLocalStorage localStorage = FakeLocalStorage.empty();
    FakeDocument document = new FakeDocument();

    Options.run(document, localStorage);

    document.element("claire_guide").click();
    assertTrue(document.element("claire_guide").isChecked());
    assertEquals(LocalStorage.YES, localStorage.getItem(LocalStorage.HIDE_GUIDE));

    document.element("debug_log_checkbox").click();
    assertTrue(document.element("debug_log_checkbox").isChecked());
    assertEquals(LocalStorage.YES, localStorage.getItem(LocalStorage.DEBUG_LOGGING));

    document.element("claire_guide").click();
    assertFalse(document.element("claire_guide").isChecked());
    assertEquals(LocalStorage.NO, localStorage.getItem(LocalStorage.HIDE_GUIDE));

    document.element("debug_log_checkbox").click();
    assertFalse(document.element("debug_log_checkbox").isChecked());
    assertEquals(LocalStorage.NO, localStorage.getItem(LocalStorage.DEBUG_LOGGING));
  }

  @Test
  @DisplayName("renders from localStorage")
  void rendersFromLocalStorage() {
    FakeLocalStorage localStorage = FakeLocalStorage.empty();
    localStorage.setItem(LocalStorage.HIDE_GUIDE, LocalStorage.YES);
    localStorage.setItem(LocalStorage.DEBUG_LOGGING, LocalStorage.NO);
    FakeDocument document = new FakeDocument();

    Options.run(document, localStorage);

    assertTrue(document.element("claire_guide").isChecked());
    assertFalse(document.element("debug_log_checkbox").isChecked());
  }
}
