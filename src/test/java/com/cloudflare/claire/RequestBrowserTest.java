package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.ContentScriptMessage;
import com.cloudflare.claire.browser.Header;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.dom.LocalStorage;
import com.cloudflare.claire.js.JsUrl;
import com.cloudflare.claire.testing.FakeChrome;
import com.cloudflare.claire.testing.FakeConsole;
import com.cloudflare.claire.testing.FakeLocalStorage;
import com.cloudflare.claire.testing.TestEnvironment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The parts of {@code request.js} that talk to the browser, and the Railgun and icon rules the
 * source test file left uncovered.
 */
class RequestBrowserTest {

  @Nested
  @DisplayName("Railgun decoding")
  class RailgunDecoding {

    private RailgunMetaData decode(String headerValue) {
      Request r = new Request(
          TestEnvironment.create(),
          RequestDetails.withHeaders(new Header("cf-railgun", headerValue)).build());
      return r.getRailgunMetaData();
    }

    @Test
    @DisplayName("leaves no metadata at all when the header is absent")
    void leavesNoMetadataAtAllWhenTheHeaderIsAbsent() {
      Request r = new Request(TestEnvironment.create(), RequestDetails.withHeaders().build());

      assertNull(r.getRailgunMetaData());
    }

    @Test
    @DisplayName("creates empty metadata when the header carries no text value")
    void createsEmptyMetadataWhenTheHeaderCarriesNoTextValue() {
      RailgunMetaData metaData = decode(null);

      // Every property is absent, not defaulted. The source returns before assigning any of them,
      // and a caller that reads `messages` in this state fails rather than seeing an empty list.
      assertNull(metaData.normal());
      assertNull(metaData.id());
      assertNull(metaData.version());
      assertNull(metaData.compression());
      assertNull(metaData.time());
      assertNull(metaData.flags());
      assertNull(metaData.messages());
    }

    @Test
    @DisplayName("distinguishes flags never assigned from flags assigned a non-number")
    void distinguishesFlagsNeverAssignedFromFlagsAssignedANonNumber() {
      assertNull(decode(null).flags());
      assertTrue(Double.isNaN(decode("normal").flags()));
    }

    @Test
    @DisplayName("decodes a normal header with no flags field as NaN flags and no messages")
    void decodesANormalHeaderWithNoFlagsFieldAsNanFlagsAndNoMessages() {
      RailgunMetaData metaData = decode("normal");

      assertEquals(Boolean.TRUE, metaData.normal());
      assertEquals("normal", metaData.id());
      assertNull(metaData.version());
      assertTrue(Double.isNaN(metaData.flags()));
      assertEquals(List.of(), metaData.messages());
    }

    @Test
    @DisplayName("reports a truncated non-normal header as undefined seconds")
    void reportsATruncatedNonNormalHeaderAsUndefinedSeconds() {
      RailgunMetaData metaData = decode("foobar 46");

      assertEquals(Boolean.FALSE, metaData.normal());
      assertEquals("foobar", metaData.id());
      assertEquals("54%", metaData.compression());
      assertEquals("undefinedsec", metaData.time());
      assertTrue(Double.isNaN(metaData.flags()));
    }

    @Test
    @DisplayName("reports a non-numeric compression field as NaN percent")
    void reportsANonNumericCompressionFieldAsNaNPercent() {
      assertEquals("NaN%", decode("foobar zz 700 96 9001").compression());
      assertEquals("53.5%", decode("foobar 46.5 700 96 9001").compression());
    }

    @Test
    @DisplayName("emits one message per set flag, in ascending bit order")
    void emitsOneMessagePerSetFlagInAscendingBitOrder() {
      assertEquals(
          List.of(
              "map.file used to change IP",
              "map.file default IP used",
              "Host name change",
              "Existing connection reused",
              "Railgun sender sent dictionary",
              "Dictionary found in memcache",
              "Restarted broken origin connection"),
          decode("normal 127 foobar 9001").messages());
      assertEquals(List.of("map.file used to change IP"), decode("normal 1 x 9001").messages());
      assertEquals(List.of(), decode("normal 0 x 9001").messages());
    }

    @Test
    @DisplayName("exposes every flag's bit position and message")
    void exposesEveryFlagsBitPositionAndMessage() {
      assertEquals(0x01, RailgunFlag.FLAG_DOMAIN_MAP_USED.position());
      assertEquals(0x40, RailgunFlag.FLAG_RESTART_CONNECTION.position());
      assertEquals("Host name change", RailgunFlag.FLAG_HOST_CHANGE.message());
    }

    @Test
    @DisplayName("compares and prints metadata by value")
    void comparesAndPrintsMetadataByValue() {
      RailgunMetaData first = decode("normal 96 foobar 9001");
      RailgunMetaData second = decode("normal 96 foobar 9001");

      assertEquals(first, second);
      assertEquals(first.hashCode(), second.hashCode());
      assertFalse(first.equals(decode("normal")));
      assertFalse(first.equals("not metadata"));
      assertTrue(first.equals(first));
      assertTrue(first.toString().contains("id=normal"));
    }
  }

  @Nested
  @DisplayName("icon selection")
  class IconSelection {

    private Request request(String ip, String connectionType, Header... headers) {
      Request r = new Request(
          TestEnvironment.create(), RequestDetails.withHeaders(headers).ip(ip).build());
      if (connectionType != null) {
        r.setConnectionInfo(new ConnectionInfo(connectionType));
      }
      return r;
    }

    @Test
    @DisplayName("falls back to the off icon for a plain request")
    void fallsBackToTheOffIconForAPlainRequest() {
      Request r = request(null, null);

      assertEquals("images/claire-3-off", r.getPageActionPath());
      assertEquals("images/claire-3-popup-off", r.getPopupPath());
    }

    @Test
    @DisplayName("appends one suffix per detected feature in a fixed order")
    void appendsOneSuffixPerDetectedFeatureInAFixedOrder() {
      Request all = request(
          "2001:db8::1",
          "h2",
          new Header("server", "cloudflare"),
          new Header("cf-railgun", "normal 96 f 9001"));

      assertEquals("images/claire-3-on-h2-ipv6-rg", all.getPageActionPath());
      assertEquals("images/claire-3-popup-on-h2-ipv6-rg", all.getPopupPath());
      assertEquals("x-on-h2-ipv6-rg", all.getImagePath("x-"));
    }

    @Test
    @DisplayName("marks only the features actually present")
    void marksOnlyTheFeaturesActuallyPresent() {
      assertEquals(
          "images/claire-3-on",
          request(null, null, new Header("server", "cloudflare")).getPageActionPath());
      assertEquals("images/claire-3-off-h2", request(null, "h2").getPageActionPath());
      assertEquals("images/claire-3-off-ipv6", request("::1", null).getPageActionPath());
      assertEquals(
          "images/claire-3-off-rg",
          request(null, null, new Header("cf-railgun", "normal")).getPageActionPath());
    }

    @Test
    @DisplayName("treats a non-h2 connection type as not HTTP/2")
    void treatsANonH2ConnectionTypeAsNotHttp2() {
      Request r = request(null, "http/1.1");

      assertTrue(r.hasConnectionInfo());
      assertEquals("http/1.1", r.connectionType());
      assertFalse(r.servedOverH2());
    }

    @Test
    @DisplayName("starts out with no connection information")
    void startsOutWithNoConnectionInformation() {
      Request r = request(null, null);

      assertFalse(r.hasConnectionInfo());
      assertNull(r.connectionType());
      assertFalse(r.servedOverH2());
    }
  }

  @Nested
  @DisplayName("details accessors")
  class DetailsAccessors {

    @Test
    @DisplayName("passes the tab id, URL and cache flag straight through")
    void passesTheTabIdUrlAndCacheFlagStraightThrough() {
      RequestDetails details = RequestDetails.withHeaders()
          .tabId(42)
          .url("https://example.com/a")
          .fromCache(true)
          .build();
      Request r = new Request(TestEnvironment.create(), details);

      assertEquals(42, r.getTabID());
      assertEquals("https://example.com/a", r.getRequestURL());
      assertEquals(Boolean.TRUE, r.servedFromBrowserCache());
      assertEquals(details, r.details());
    }

    @Test
    @DisplayName("reports an unset cache flag and IP as absent and empty")
    void reportsAnUnsetCacheFlagAndIpAsAbsentAndEmpty() {
      Request r = new Request(TestEnvironment.create(), RequestDetails.withHeaders().build());

      assertNull(r.servedFromBrowserCache());
      assertEquals("", r.getServerIP());
      assertFalse(r.isv6IP());
    }

    @Test
    @DisplayName("treats an empty IP string as no IP at all")
    void treatsAnEmptyIpStringAsNoIpAtAll() {
      Request r = new Request(
          TestEnvironment.create(), RequestDetails.withHeaders().ip("").build());

      assertEquals("", r.getServerIP());
    }

    @Test
    @DisplayName("accepts a header list as well as varargs")
    void acceptsAHeaderListAsWellAsVarargs() {
      Request r = new Request(
          TestEnvironment.create(),
          RequestDetails.withHeaders(List.of(new Header("server", "cloudflare"))).build());

      assertTrue(r.servedByCloudFlare());
    }

    @Test
    @DisplayName("rejects a header without a name")
    void rejectsAHeaderWithoutAName() {
      assertThrows(IllegalArgumentException.class, () -> new Header(null, "x"));
    }
  }

  @Nested
  @DisplayName("trace URL")
  class TraceUrl {

    @Test
    @DisplayName("points at the cdn-cgi trace endpoint on the same origin")
    void pointsAtTheCdnCgiTraceEndpointOnTheSameOrigin() {
      Request r = new Request(
          TestEnvironment.create(),
          RequestDetails.withHeaders().url("https://example.com/deep/path?q=1").build());

      assertEquals("https://example.com/cdn-cgi/trace?q=1", r.getCloudFlareTrace());
    }

    @Test
    @DisplayName("fails on a request with no URL, as the browser's URL constructor does")
    void failsOnARequestWithNoUrlAsTheBrowsersUrlConstructorDoes() {
      Request r = new Request(TestEnvironment.create(), RequestDetails.withHeaders().build());

      assertThrows(JsUrl.InvalidUrlException.class, r::getCloudFlareTrace);
    }
  }

  @Nested
  @DisplayName("connection info query")
  class ConnectionInfoQuery {

    @Test
    @DisplayName("asks the content script and paints the icon once it answers")
    void asksTheContentScriptAndPaintsTheIconOnceItAnswers() {
      FakeChrome chrome = new FakeChrome().withTabMessageReply(new ConnectionInfo("h2"));
      ExtensionEnvironment env = TestEnvironment.create(chrome);
      Request r = new Request(
          env,
          RequestDetails.withHeaders(new Header("server", "cloudflare")).tabId(7).build());
      env.requests().put(7, r);

      r.queryConnectionInfoAndSetIcon();

      assertEquals(
          List.of(ContentScriptMessage.checkConnectionInfo()), chrome.sentTabMessages());
      assertTrue(r.servedOverH2());
      assertEquals(
          List.of(new FakeChrome.IconCall(
              7,
              Map.of(
                  19, "images/claire-3-on-h2.png",
                  38, "images/claire-3-on-h2@2x.png"))),
          chrome.iconCalls());
      assertEquals(Map.of(7, "page-action-popup.html"), chrome.popups());
      assertEquals(List.of(7), chrome.shownTabs());
    }

    @Test
    @DisplayName("paints straight away when the connection is already known")
    void paintsStraightAwayWhenTheConnectionIsAlreadyKnown() {
      FakeChrome chrome = new FakeChrome();
      ExtensionEnvironment env = TestEnvironment.create(chrome);
      Request r = new Request(env, RequestDetails.withHeaders().tabId(3).build());
      r.setConnectionInfo(new ConnectionInfo("h2"));

      r.queryConnectionInfoAndSetIcon();

      assertEquals(List.of(), chrome.sentTabMessages());
      assertEquals(1, chrome.iconCalls().size());
      assertEquals(List.of(3), chrome.shownTabs());
    }

    @Test
    @DisplayName("does nothing when a background tab sends no reply")
    void doesNothingWhenABackgroundTabSendsNoReply() {
      FakeChrome chrome = new FakeChrome().withTabMessageReply(null);
      ExtensionEnvironment env = TestEnvironment.create(chrome);
      Request r = new Request(env, RequestDetails.withHeaders().tabId(5).build());

      r.queryConnectionInfoAndSetIcon();

      assertFalse(r.hasConnectionInfo());
      assertEquals(List.of(), chrome.iconCalls());
    }

    @Test
    @DisplayName("logs rather than propagates a failure to reach the content script")
    void logsRatherThanPropagatesAFailureToReachTheContentScript() {
      RuntimeException failure = new IllegalStateException("tab is gone");
      FakeChrome chrome = new FakeChrome()
          .failingTabsSendMessage(failure)
          .withLastError("could not establish connection");
      FakeConsole console = new FakeConsole();
      ExtensionEnvironment env = new ExtensionEnvironment(
          chrome, console, FakeLocalStorage.empty(), new RequestRegistry());
      Request r = new Request(env, RequestDetails.withHeaders().tabId(9).build());

      r.queryConnectionInfoAndSetIcon();

      assertEquals(
          List.of(
              "caught exception when sending message to content script",
              "could not establish connection",
              String.valueOf(failure)),
          console.lines());
    }

    @Test
    @DisplayName("logs rather than propagates a failure to reveal the page action")
    void logsRatherThanPropagatesAFailureToRevealThePageAction() {
      FakeChrome chrome = new FakeChrome()
          .failingPageActionShow(new IllegalStateException("no such tab"));
      FakeConsole console = new FakeConsole();
      ExtensionEnvironment env = new ExtensionEnvironment(
          chrome, console, FakeLocalStorage.empty(), new RequestRegistry());
      Request r = new Request(env, RequestDetails.withHeaders().tabId(9).build());

      r.setPageActionIconAndPopup();

      assertEquals(1, console.calls().size());
      assertEquals(
          "Exception on page action show for tab with ID: ", console.calls().get(0).get(0));
      assertEquals(List.of(), chrome.shownTabs());
    }
  }

  @Nested
  @DisplayName("debug logging")
  class DebugLogging {

    private FakeConsole logWith(String debugFlag, Header... headers) {
      FakeConsole console = new FakeConsole();
      FakeLocalStorage storage = FakeLocalStorage.empty();
      if (debugFlag != null) {
        storage.setItem(LocalStorage.DEBUG_LOGGING, debugFlag);
      }
      ExtensionEnvironment env = new ExtensionEnvironment(
          new FakeChrome(), console, storage, new RequestRegistry());
      new Request(
          env,
          RequestDetails.withHeaders(headers).url("https://example.com/").ip("1.2.3.4").build())
          .logToConsole();
      return console;
    }

    @Test
    @DisplayName("stays silent unless the debug preference is on")
    void staysSilentUnlessTheDebugPreferenceIsOn() {
      assertEquals(List.of(), logWith(null).calls());
      assertEquals(List.of(), logWith("no").calls());
    }

    @Test
    @DisplayName("logs the request summary when the debug preference is on")
    void logsTheRequestSummaryWhenTheDebugPreferenceIsOn() {
      FakeConsole console = logWith("yes");

      assertEquals(3, console.calls().size());
      assertEquals(List.of("\n"), console.calls().get(0));
      assertTrue(console.lines().get(1).contains("CF - false"));
    }

    @Test
    @DisplayName("fails on a Railgun header carrying no value, as the source does")
    void failsOnARailgunHeaderCarryingNoValueAsTheSourceDoes() {
      // The source reaches railgunMetaData.messages.join('; ') with messages still absent and
      // throws, aborting logToConsole and with it the webRequest.onCompleted handler. Preserved
      // rather than repaired: swallowing it would be a behaviour change.
      FakeConsole console = new FakeConsole();
      ExtensionEnvironment env = new ExtensionEnvironment(
          new FakeChrome(),
          console,
          FakeLocalStorage.of(LocalStorage.DEBUG_LOGGING, LocalStorage.YES),
          new RequestRegistry());
      Request request = new Request(
          env,
          RequestDetails.withHeaders(Header.withoutValue("cf-railgun"))
              .url("https://a.example/")
              .ip("1.2.3.4")
              .build());

      assertThrows(NullPointerException.class, request::logToConsole);
    }

    @Test
    @DisplayName("adds the Ray ID and Railgun lines when those features are present")
    void addsTheRayIdAndRailgunLinesWhenThoseFeaturesArePresent() {
      FakeConsole console = logWith(
          "yes",
          new Header("server", "cloudflare"),
          new Header("cf-ray", "deadbeef-SFO"),
          new Header("cf-railgun", "normal 96 foobar 9001"));

      List<String> lines = console.lines();
      assertEquals(5, lines.size());
      assertTrue(lines.get(1).contains("CF - true"));
      assertEquals("Ray ID -  deadbeef", lines.get(3));
      assertEquals(
          "Railgun -  normal Dictionary found in memcache; "
              + "Restarted broken origin connection",
          lines.get(4));
    }
  }
}
