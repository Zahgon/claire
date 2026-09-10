package com.cloudflare.claire.testing;

import com.cloudflare.claire.ExtensionEnvironment;
import com.cloudflare.claire.RequestRegistry;

/**
 * Assembles the ambient globals for a test.
 *
 * <p>Stands in for the browser installing {@code chrome}, {@code console} and
 * {@code localStorage} on the extension's global object.
 */
public final class TestEnvironment {

  private TestEnvironment() {
  }

  /** An environment with fresh, empty fakes throughout. */
  public static ExtensionEnvironment create() {
    return create(new FakeChrome());
  }

  /** An environment around a chrome fake the test has already scripted. */
  public static ExtensionEnvironment create(FakeChrome chrome) {
    return new ExtensionEnvironment(
        chrome, new FakeConsole(), FakeLocalStorage.empty(), new RequestRegistry());
  }

  /** An environment whose parts the test holds references to. */
  public static ExtensionEnvironment create(
      FakeChrome chrome, FakeConsole console, FakeLocalStorage localStorage) {
    return new ExtensionEnvironment(chrome, console, localStorage, new RequestRegistry());
  }
}
