package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.testing.FakeChrome;
import com.cloudflare.claire.testing.FakeConsole;
import com.cloudflare.claire.testing.FakeLocalStorage;
import com.cloudflare.claire.testing.TestEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code window.requests}, the background page's tab-id to request map. */
class RequestRegistryTest {

  private static Request request() {
    return new Request(TestEnvironment.create(), RequestDetails.withHeaders().build());
  }

  @Test
  @DisplayName("stores, finds and removes a request by tab id")
  void storesFindsAndRemovesARequestByTabId() {
    RequestRegistry registry = new RequestRegistry();
    Request request = request();

    assertFalse(registry.has(1));
    assertNull(registry.get(1));
    assertEquals(0, registry.size());

    registry.put(1, request);

    assertTrue(registry.has(1));
    assertSame(request, registry.get(1));
    assertEquals(1, registry.size());

    registry.remove(1);

    assertFalse(registry.has(1));
    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("replaces the request recorded for a tab that navigates again")
  void replacesTheRequestRecordedForATabThatNavigatesAgain() {
    RequestRegistry registry = new RequestRegistry();
    Request first = request();
    Request second = request();

    registry.put(1, first);
    registry.put(1, second);

    assertSame(second, registry.get(1));
    assertEquals(1, registry.size());
  }

  @Test
  @DisplayName("removing an unknown tab is harmless")
  void removingAnUnknownTabIsHarmless() {
    RequestRegistry registry = new RequestRegistry();

    registry.remove(404);

    assertEquals(0, registry.size());
  }

  @Test
  @DisplayName("requires every ambient global to be supplied")
  void requiresEveryAmbientGlobalToBeSupplied() {
    FakeChrome chrome = new FakeChrome();
    FakeConsole console = new FakeConsole();
    FakeLocalStorage storage = FakeLocalStorage.empty();
    RequestRegistry registry = new RequestRegistry();

    assertThrows(
        NullPointerException.class,
        () -> new ExtensionEnvironment(null, console, storage, registry));
    assertThrows(
        NullPointerException.class,
        () -> new ExtensionEnvironment(chrome, null, storage, registry));
    assertThrows(
        NullPointerException.class,
        () -> new ExtensionEnvironment(chrome, console, null, registry));
    assertThrows(
        NullPointerException.class,
        () -> new ExtensionEnvironment(chrome, console, storage, null));
  }
}
