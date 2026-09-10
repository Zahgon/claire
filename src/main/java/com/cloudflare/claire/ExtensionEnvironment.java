package com.cloudflare.claire;

import com.cloudflare.claire.browser.ChromeApi;
import com.cloudflare.claire.browser.Console;
import com.cloudflare.claire.dom.LocalStorage;
import java.util.Objects;

/**
 * The ambient globals the extension code reads: {@code chrome}, {@code console},
 * {@code localStorage}, and the background page's {@code window.requests}.
 *
 * <p>JavaScript resolves these off the global object at each use site. Java has no equivalent, so
 * they are gathered into one value that the entry points are given. This is a type (A) difference
 * &mdash; the same objects, reached explicitly instead of implicitly.
 */
public record ExtensionEnvironment(
    ChromeApi chrome,
    Console console,
    LocalStorage localStorage,
    RequestRegistry requests) {

  public ExtensionEnvironment {
    Objects.requireNonNull(chrome, "chrome");
    Objects.requireNonNull(console, "console");
    Objects.requireNonNull(localStorage, "localStorage");
    Objects.requireNonNull(requests, "requests");
  }
}
