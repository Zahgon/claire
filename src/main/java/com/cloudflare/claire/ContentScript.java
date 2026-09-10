package com.cloudflare.claire;

import com.cloudflare.claire.browser.ChromeApi;
import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.ContentScriptMessage;
import com.cloudflare.claire.browser.MessageSender;
import com.cloudflare.claire.dom.Performance;
import com.cloudflare.claire.dom.PerformanceEntry;
import java.util.List;
import java.util.function.Consumer;

/**
 * The per-page content script, migrated from {@code source/contentscript.js}.
 *
 * <p>It is the only part of the extension that can see how the page's own connection was made,
 * because {@code nextHopProtocol} is only exposed to the page's Performance Timeline.
 */
public final class ContentScript {

  private final Performance performance;
  private final ChromeApi chrome;

  public ContentScript(Performance performance, ChromeApi chrome) {
    this.performance = performance;
    this.chrome = chrome;
  }

  /** Registers the message listener and reports the connection info unprompted, as on load. */
  public static ContentScript start(Performance performance, ChromeApi chrome) {
    ContentScript script = new ContentScript(performance, chrome);
    chrome.runtimeOnMessageAddListener(script::onMessage);
    script.notifyExtension();
    return script;
  }

  /**
   * The protocol used to reach the first hop, for the first navigation event available.
   *
   * <p>{@code null} when the Performance Timeline API is unavailable &mdash; the source guards on
   * {@code performance && performance.getEntriesByType}, and a {@code null} {@code performance}
   * stands in for both halves of that check, since a Java interface method cannot be missing.
   *
   * <p>A present API with no navigation entry still answers, with an unknown protocol: that is
   * the {@code entry && entry.nextHopProtocol} short-circuit, which yields
   * {@code {type: undefined}} rather than nothing at all.
   */
  public ConnectionInfo determineConnectionInfo() {
    if (performance == null) {
      return null;
    }
    List<PerformanceEntry> entries = performance.getEntriesByType(Performance.NAVIGATION);
    PerformanceEntry entry = entries == null || entries.isEmpty() ? null : entries.get(0);
    String proto = entry == null ? null : entry.nextHopProtocol();
    return new ConnectionInfo(proto);
  }

  /** Answers the background page's {@code check_connection_info} query. */
  void onMessage(Object request, MessageSender sender, Consumer<Object> sendResponse) {
    if (request instanceof ContentScriptMessage message
        && ContentScriptMessage.CHECK_CONNECTION_INFO.equals(message.action())) {
      sendResponse.accept(determineConnectionInfo());
    }
  }

  /** Tells the extension about this page's connection without waiting to be asked. */
  void notifyExtension() {
    chrome.runtimeSendMessage(determineConnectionInfo(), response -> {
    });
  }
}
