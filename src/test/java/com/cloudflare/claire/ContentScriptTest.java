package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.ContentScriptMessage;
import com.cloudflare.claire.browser.MessageSender;
import com.cloudflare.claire.browser.Tab;
import com.cloudflare.claire.testing.FakeChrome;
import com.cloudflare.claire.testing.FakePerformance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The per-page content script, migrated from {@code source/contentscript.js}. */
class ContentScriptTest {

  @Test
  @DisplayName("reports the first hop protocol of the navigation entry")
  void reportsTheFirstHopProtocolOfTheNavigationEntry() {
    ContentScript script = new ContentScript(FakePerformance.withProtocol("h2"), new FakeChrome());

    assertEquals(new ConnectionInfo("h2"), script.determineConnectionInfo());
  }

  @Test
  @DisplayName("reports an unknown protocol when there is no navigation entry")
  void reportsAnUnknownProtocolWhenThereIsNoNavigationEntry() {
    ContentScript script =
        new ContentScript(FakePerformance.withoutNavigationEntry(), new FakeChrome());

    assertEquals(new ConnectionInfo(null), script.determineConnectionInfo());
  }

  @Test
  @DisplayName("reports nothing at all without the Performance Timeline API")
  void reportsNothingAtAllWithoutThePerformanceTimelineApi() {
    ContentScript script = new ContentScript(null, new FakeChrome());

    assertNull(script.determineConnectionInfo());
  }

  @Test
  @DisplayName("notifies the extension about the connection as soon as it loads")
  void notifiesTheExtensionAboutTheConnectionAsSoonAsItLoads() {
    FakeChrome chrome = new FakeChrome();

    ContentScript.start(FakePerformance.withProtocol("h3"), chrome);

    assertEquals(List.of(new ConnectionInfo("h3")), chrome.sentRuntimeMessages());
  }

  @Test
  @DisplayName("answers the background page's connection info query")
  void answersTheBackgroundPagesConnectionInfoQuery() {
    FakeChrome chrome = new FakeChrome();
    ContentScript.start(FakePerformance.withProtocol("h2"), chrome);
    List<Object> replies = new ArrayList<>();

    chrome.fireMessage(
        ContentScriptMessage.checkConnectionInfo(), new MessageSender(new Tab(1)), replies::add);

    assertEquals(List.of(new ConnectionInfo("h2")), replies);
  }

  @Test
  @DisplayName("stays silent for a message it does not recognise")
  void staysSilentForAMessageItDoesNotRecognise() {
    FakeChrome chrome = new FakeChrome();
    ContentScript.start(FakePerformance.withProtocol("h2"), chrome);
    List<Object> replies = new ArrayList<>();

    chrome.fireMessage(
        new ContentScriptMessage("something_else"), new MessageSender(new Tab(1)), replies::add);
    chrome.fireMessage("not a message object", new MessageSender(new Tab(1)), replies::add);

    assertEquals(List.of(), replies);
  }
}
