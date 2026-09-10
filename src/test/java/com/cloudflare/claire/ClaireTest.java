package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.Header;
import com.cloudflare.claire.browser.MessageSender;
import com.cloudflare.claire.browser.NavigationDetails;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.browser.Tab;
import com.cloudflare.claire.dom.LocalStorage;
import com.cloudflare.claire.testing.FakeChrome;
import com.cloudflare.claire.testing.FakeConsole;
import com.cloudflare.claire.testing.FakeLocalStorage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The background page's event wiring, migrated from {@code source/claire.js}. */
class ClaireTest {

  private FakeChrome chrome;
  private FakeConsole console;
  private ExtensionEnvironment env;
  private Claire claire;

  @BeforeEach
  void setUp() {
    chrome = new FakeChrome();
    console = new FakeConsole();
    env = new ExtensionEnvironment(
        chrome, console, FakeLocalStorage.empty(), new RequestRegistry());
    claire = Claire.start(env);
  }

  @Test
  @DisplayName("listens for completed main-frame requests on every URL")
  void listensForCompletedMainFrameRequestsOnEveryUrl() {
    assertTrue(chrome.hasWebRequestListener());
    assertEquals(List.of("<all_urls>"), chrome.registeredFilter().urls());
    assertEquals(List.of("main_frame"), chrome.registeredFilter().types());
    assertEquals(List.of("responseHeaders"), chrome.registeredExtraInfoSpec());
  }

  @Test
  @DisplayName("records a completed request against its tab")
  void recordsACompletedRequestAgainstItsTab() {
    chrome.fireRequestCompleted(
        RequestDetails.withHeaders(new Header("server", "cloudflare")).tabId(4).build());

    Request stored = env.requests().get(4);
    assertNotNull(stored);
    assertTrue(stored.servedByCloudFlare());
    assertSame(stored, claire.requests().get(4));
  }

  @Test
  @DisplayName("writes a completed request to the debug log when the preference is on")
  void writesACompletedRequestToTheDebugLogWhenThePreferenceIsOn() {
    FakeLocalStorage storage = FakeLocalStorage.of(LocalStorage.DEBUG_LOGGING, LocalStorage.YES);
    FakeChrome loggingChrome = new FakeChrome();
    ExtensionEnvironment loggingEnv = new ExtensionEnvironment(
        loggingChrome, console, storage, new RequestRegistry());
    Claire.start(loggingEnv);

    loggingChrome.fireRequestCompleted(
        RequestDetails.withHeaders().tabId(1).url("https://example.com/").build());

    assertFalse(console.calls().isEmpty());
  }

  @Test
  @DisplayName("moves a request across when the browser swaps tab ids")
  void movesARequestAcrossWhenTheBrowserSwapsTabIds() {
    chrome.fireRequestCompleted(RequestDetails.withHeaders().tabId(10).build());
    Request original = env.requests().get(10);

    chrome.fireTabReplaced(11, 10);

    assertSame(original, env.requests().get(11));
    assertFalse(env.requests().has(10));
  }

  @Test
  @DisplayName("logs when the replaced tab has no recorded request")
  void logsWhenTheReplacedTabHasNoRecordedRequest() {
    chrome.fireTabReplaced(11, 10);

    assertEquals(
        List.of("Could not find an entry in window.requests when replacing  10"),
        console.lines());
    assertFalse(env.requests().has(11));
  }

  @Test
  @DisplayName("paints the page action once the top-level document is ready")
  void paintsThePageActionOnceTheTopLevelDocumentIsReady() {
    chrome.withTabMessageReply(new ConnectionInfo("h2"));
    chrome.fireRequestCompleted(
        RequestDetails.withHeaders(new Header("server", "cloudflare")).tabId(2).build());

    chrome.fireDomContentLoaded(new NavigationDetails(2, 0));

    assertEquals(List.of(2), chrome.shownTabs());
  }

  @Test
  @DisplayName("ignores sub-frame navigation")
  void ignoresSubFrameNavigation() {
    chrome.fireRequestCompleted(RequestDetails.withHeaders().tabId(2).build());

    chrome.fireDomContentLoaded(new NavigationDetails(2, 1));

    assertEquals(List.of(), chrome.iconCalls());
  }

  @Test
  @DisplayName("ignores navigation for a tab with no recorded request")
  void ignoresNavigationForATabWithNoRecordedRequest() {
    chrome.fireDomContentLoaded(new NavigationDetails(99, 0));

    assertEquals(List.of(), chrome.iconCalls());
  }

  @Test
  @DisplayName("skips a request the browser served from its cache")
  void skipsARequestTheBrowserServedFromItsCache() {
    chrome.fireRequestCompleted(
        RequestDetails.withHeaders().tabId(2).fromCache(true).build());

    chrome.fireDomContentLoaded(new NavigationDetails(2, 0));

    assertEquals(List.of(), chrome.iconCalls());
  }

  @Test
  @DisplayName("accepts the content script's unsolicited connection report")
  void acceptsTheContentScriptsUnsolicitedConnectionReport() {
    chrome.fireRequestCompleted(RequestDetails.withHeaders().tabId(8).build());
    List<Object> responses = new ArrayList<>();

    chrome.fireMessage(new ConnectionInfo("h2"), new MessageSender(new Tab(8)), responses::add);

    assertTrue(env.requests().get(8).servedOverH2());
    assertEquals(1, responses.size());
  }

  @Test
  @DisplayName("still answers a message about a tab it has no request for")
  void stillAnswersAMessageAboutATabItHasNoRequestFor() {
    List<Object> responses = new ArrayList<>();

    chrome.fireMessage(new ConnectionInfo("h2"), new MessageSender(new Tab(77)), responses::add);

    assertEquals(1, responses.size());
    assertNull(env.requests().get(77));
  }

  @Test
  @DisplayName("clears request data when a tab is destroyed")
  void clearsRequestDataWhenATabIsDestroyed() {
    chrome.fireRequestCompleted(RequestDetails.withHeaders().tabId(6).build());
    assertEquals(1, env.requests().size());

    chrome.fireTabRemoved(6);

    assertEquals(0, env.requests().size());
    assertFalse(env.requests().has(6));
  }
}
