package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudflare.claire.browser.ChromeApi;
import com.cloudflare.claire.browser.Header;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.browser.Tab;
import com.cloudflare.claire.browser.TabQueryInfo;
import com.cloudflare.claire.dom.LocalStorage;
import com.cloudflare.claire.testing.FakeChrome;
import com.cloudflare.claire.testing.FakeConsole;
import com.cloudflare.claire.testing.FakeDocument;
import com.cloudflare.claire.testing.FakeElement;
import com.cloudflare.claire.testing.FakeLocalStorage;
import com.cloudflare.claire.testing.FakeMouseEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The popup, migrated from {@code source/page-action-popup.js}. */
class PageActionPopupTest {

  private static final int TAB_ID = 12;

  private record Fixture(FakeDocument document, ExtensionEnvironment env) {
  }

  private static Fixture open(FakeLocalStorage storage, Header... headers) {
    return open(storage, RequestDetails.withHeaders(headers).tabId(TAB_ID));
  }

  private static Fixture open(FakeLocalStorage storage, RequestDetails.Builder details) {
    FakeChrome chrome = new FakeChrome();
    ExtensionEnvironment env = new ExtensionEnvironment(
        chrome, new FakeConsole(), storage, new RequestRegistry());
    Claire backgroundPage = new Claire(env);
    env.requests().put(TAB_ID, new Request(env, details.build()));
    chrome.withBackgroundPage(backgroundPage).withQueryResult(new Tab(TAB_ID));

    FakeDocument document = new FakeDocument();
    PageActionPopup.start(document, env);
    return new Fixture(document, env);
  }

  @Test
  @DisplayName("queries the active tab of the current window")
  void queriesTheActiveTabOfTheCurrentWindow() {
    FakeChrome chrome = new FakeChrome();
    ExtensionEnvironment env = new ExtensionEnvironment(
        chrome, new FakeConsole(), FakeLocalStorage.empty(), new RequestRegistry());
    env.requests().put(TAB_ID, new Request(env, RequestDetails.withHeaders().build()));
    chrome.withBackgroundPage(new Claire(env)).withQueryResult(new Tab(TAB_ID));

    PageActionPopup.start(new FakeDocument(), env);

    assertEquals(
        List.of(new TabQueryInfo(true, ChromeApi.WINDOW_ID_CURRENT)), chrome.tabQueries());
  }

  @Test
  @DisplayName("renders the IP and the matching popup illustration")
  void rendersTheIpAndTheMatchingPopupIllustration() {
    Fixture fixture = open(
        FakeLocalStorage.empty(),
        RequestDetails.withHeaders(new Header("server", "cloudflare"))
            .tabId(TAB_ID)
            .url("https://example.com/")
            .ip("2001:db8::1"));

    assertEquals("2001:db8::1", fixture.document().element("ip").getValue());
    assertEquals(
        "images/claire-3-popup-on-ipv6.png",
        fixture.document().selected("#claireInfoImage img").src());
  }

  @Test
  @DisplayName("shows the Ray ID, location and trace link for a Cloudflare response")
  void showsTheRayIdLocationAndTraceLinkForACloudflareResponse() {
    Fixture fixture = open(
        FakeLocalStorage.empty(),
        RequestDetails.withHeaders(
                new Header("server", "cloudflare"), new Header("cf-ray", "deadbeef-SFO"))
            .tabId(TAB_ID)
            .url("https://example.com/page"));

    FakeDocument document = fixture.document();
    assertEquals("deadbeef", document.element("rayID").getValue());
    assertEquals("SFO", document.element("locationCode").getTextContent());
    assertEquals("San Francisco, United States", document.element("locationName").getTextContent());
    assertEquals("https://example.com/cdn-cgi/trace", document.element("traceURL").href());
    assertFalse(document.element("ray").hasClass("hidden"));
  }

  @Test
  @DisplayName("hides the Cloudflare cards for a response from another origin")
  void hidesTheCloudflareCardsForAResponseFromAnotherOrigin() {
    Fixture fixture = open(FakeLocalStorage.empty(), new Header("server", "nginx"));

    FakeDocument document = fixture.document();
    assertTrue(document.element("ray").hasClass("hidden"));
    assertTrue(document.element("loc").hasClass("hidden"));
    assertTrue(document.element("actions").hasClass("hidden"));
    assertTrue(document.element("railgun").hasClass("hidden"));
  }

  @Test
  @DisplayName("renders the word undefined when a Cloudflare response carries no Ray ID")
  void rendersTheWordUndefinedWhenACloudflareResponseCarriesNoRayId() {
    Fixture fixture = open(
        FakeLocalStorage.empty(),
        RequestDetails.withHeaders(new Header("server", "cloudflare"))
            .tabId(TAB_ID)
            .url("https://example.com/"));

    assertEquals("undefined", fixture.document().element("rayID").getValue());
    assertEquals("", fixture.document().element("locationCode").getTextContent());
  }

  @Test
  @DisplayName("shows compression and time only for a non-normal Railgun header")
  void showsCompressionAndTimeOnlyForANonNormalRailgunHeader() {
    Fixture nonNormal =
        open(FakeLocalStorage.empty(), new Header("cf-railgun", "foobar 46 700 96 9001"));

    assertEquals("foobar", nonNormal.document().element("railgunID").getTextContent());
    assertEquals("54%", nonNormal.document().element("railgunCompression").getTextContent());
    assertEquals("700sec", nonNormal.document().element("railgunTime").getTextContent());

    Fixture normal =
        open(FakeLocalStorage.empty(), new Header("cf-railgun", "normal 96 foobar 9001"));

    assertEquals("normal", normal.document().element("railgunID").getTextContent());
    assertEquals("", normal.document().element("railgunCompression").getTextContent());
    assertEquals("", normal.document().element("railgunTime").getTextContent());
  }

  @Test
  @DisplayName("hides the icon guide when the preference asks for it")
  void hidesTheIconGuideWhenThePreferenceAsksForIt() {
    Fixture hidden =
        open(FakeLocalStorage.of(LocalStorage.HIDE_GUIDE, LocalStorage.YES));
    Fixture shown = open(FakeLocalStorage.of(LocalStorage.HIDE_GUIDE, LocalStorage.NO));

    assertTrue(hidden.document().element("claireInfoImage").hasClass("hidden"));
    assertFalse(shown.document().element("claireInfoImage").hasClass("hidden"));
  }

  @Test
  @DisplayName("copies the referenced field when a copy button is clicked")
  void copiesTheReferencedFieldWhenACopyButtonIsClicked() {
    Fixture fixture = open(FakeLocalStorage.empty());
    FakeDocument document = fixture.document();
    FakeElement button = new FakeElement("copy").matching(".copy-button").withData("copyId", "ip");
    FakeMouseEvent event = new FakeMouseEvent(button);

    document.dispatchClick(event);

    assertEquals(1, document.element("ip").selectCount());
    assertEquals(List.of("copy"), document.executedCommands());
    assertTrue(event.defaultPrevented());
    assertTrue(event.propagationStopped());
  }

  @Test
  @DisplayName("ignores a click that did not land on a copy button")
  void ignoresAClickThatDidNotLandOnACopyButton() {
    Fixture fixture = open(FakeLocalStorage.empty());
    FakeDocument document = fixture.document();
    FakeMouseEvent event = new FakeMouseEvent(new FakeElement("elsewhere"));

    document.dispatchClick(event);

    assertEquals(List.of(), document.executedCommands());
    assertFalse(event.defaultPrevented());
  }
}
