package com.cloudflare.claire;

import com.cloudflare.claire.dom.Document;
import com.cloudflare.claire.dom.Element;
import com.cloudflare.claire.dom.LocalStorage;

/**
 * The options page, migrated from {@code source/options.js}.
 *
 * <p>Two checkboxes, each mirroring one {@code localStorage} preference in both directions: the
 * stored value seeds the checkbox on load, and clicking the checkbox writes it back.
 */
public final class Options {

  private Options() {
  }

  /** Binds both preference checkboxes to their {@code localStorage} keys. */
  public static void run(Document document, LocalStorage localStorage) {
    bindPreference(document, localStorage, "debug_log_checkbox", LocalStorage.DEBUG_LOGGING);
    bindPreference(document, localStorage, "claire_guide", LocalStorage.HIDE_GUIDE);
  }

  private static void bindPreference(
      Document document, LocalStorage localStorage, String elementId, String storageKey) {
    Element checkbox = document.getElementById(elementId);
    checkbox.setChecked(LocalStorage.YES.equals(localStorage.getItem(storageKey)));
    checkbox.setOnClick(event ->
        localStorage.setItem(
            storageKey, event.target().isChecked() ? LocalStorage.YES : LocalStorage.NO));
  }
}
