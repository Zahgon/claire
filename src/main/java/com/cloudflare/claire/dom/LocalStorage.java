package com.cloudflare.claire.dom;

/**
 * The {@code localStorage} global.
 *
 * <p>The source reaches it two ways &mdash; {@code localStorage.debug_logging} in
 * {@code options.js} and {@code page-action-popup.js}, {@code localStorage.getItem(...)} in
 * {@code request.js}. Both are the same store, so both collapse onto {@link #getItem}. Reading
 * an absent key yields {@code undefined} in the property form and {@code null} in the accessor
 * form; neither equals {@code 'yes'}, so {@code null} covers both.
 */
public interface LocalStorage {

  /** {@code localStorage.getItem(key)} &mdash; {@code null} when the key was never set. */
  String getItem(String key);

  /** {@code localStorage.setItem(key, value)}. */
  void setItem(String key, String value);

  /** The {@code debug_logging} preference key. */
  String DEBUG_LOGGING = "debug_logging";

  /** The {@code hide_guide} preference key. */
  String HIDE_GUIDE = "hide_guide";

  /** The value both preferences use to mean "on". */
  String YES = "yes";

  /** The value both preferences use to mean "off". */
  String NO = "no";
}
