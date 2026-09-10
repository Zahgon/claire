package com.cloudflare.claire.browser;

/**
 * The {@code console} global.
 *
 * <p>Debug logging is a user-visible feature of this extension &mdash; the options page toggles
 * it and {@code Request.logToConsole} honours the flag &mdash; so the sink is a collaborator
 * rather than a hardcoded {@code System.out}, and its output can be asserted on.
 */
public interface Console {

  /** {@code console.log(...args)}. */
  void log(Object... args);
}
