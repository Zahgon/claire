package com.cloudflare.claire.dom;

import java.util.List;

/** The {@code performance} global, narrowed to what the content script needs. */
public interface Performance {

  /** The {@code 'navigation'} entry type the content script asks for. */
  String NAVIGATION = "navigation";

  /**
   * {@code performance.getEntriesByType(type)}.
   *
   * <p>Returning {@code null} models a {@code performance} object that predates the
   * Performance Timeline API, which is the case the source guards with
   * {@code if (performance && performance.getEntriesByType)}.
   */
  List<PerformanceEntry> getEntriesByType(String type);
}
