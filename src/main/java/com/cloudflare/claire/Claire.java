package com.cloudflare.claire;

import com.cloudflare.claire.browser.BackgroundPage;
import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.MessageSender;
import com.cloudflare.claire.browser.NavigationDetails;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.browser.WebRequestFilter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The background page, migrated from {@code source/claire.js}.
 *
 * <p>Owns {@code window.requests} and wires up the five browser events the extension reacts to.
 * The source is an AMD module whose factory body runs the registrations as a side effect of being
 * loaded; here that body is {@link #start}, and each listener is a named method so it can be
 * exercised without a browser.
 */
public final class Claire implements BackgroundPage {

  /** {@code {urls: ['<all_urls>'], types: ['main_frame']}}. */
  public static final WebRequestFilter FILTER =
      new WebRequestFilter(List.of("<all_urls>"), List.of("main_frame"));

  /** {@code ['responseHeaders']}. */
  public static final List<String> EXTRA_INFO_SPEC = List.of("responseHeaders");

  private final ExtensionEnvironment env;

  public Claire(ExtensionEnvironment env) {
    this.env = Objects.requireNonNull(env, "env");
  }

  /** Registers every listener, and returns the background page the popup will later ask for. */
  public static Claire start(ExtensionEnvironment env) {
    Claire claire = new Claire(env);

    // Listen to all web requests; when one completes, build the Request that describes it.
    env.chrome().webRequestOnCompletedAddListener(
        claire::processCompletedRequest, FILTER, EXTRA_INFO_SPEC);

    env.chrome().tabsOnReplacedAddListener(claire::onTabReplaced);
    env.chrome().webNavigationOnDomContentLoadedAddListener(claire::onDomContentLoaded);
    env.chrome().runtimeOnMessageAddListener(claire::onMessage);
    env.chrome().tabsOnRemovedAddListener(claire::onTabRemoved);

    return claire;
  }

  @Override
  public RequestRegistry requests() {
    return env.requests();
  }

  /** Records a completed request against its tab and writes it to the debug log. */
  void processCompletedRequest(RequestDetails details) {
    Request request = new Request(env, details);
    env.requests().put(details.tabId(), request);
    request.logToConsole();
  }

  /**
   * Moves a tab's request across when the browser swaps tab ids.
   *
   * <p>Happens when a request starts in a background tab and the tab is then upgraded to a
   * regular, visible one.
   */
  void onTabReplaced(int addedTabId, int removedTabId) {
    if (env.requests().has(removedTabId)) {
      env.requests().put(addedTabId, env.requests().get(removedTabId));
      env.requests().remove(removedTabId);
    } else {
      env.console().log(
          "Could not find an entry in window.requests when replacing ", removedTabId);
    }
  }

  /** Paints the page action once the tab's document is ready, unless it came from the cache. */
  void onDomContentLoaded(NavigationDetails details) {
    if (details.frameId() > 0) {
      // We don't care about sub-frame requests.
      return;
    }

    if (env.requests().has(details.tabId())) {
      Request request = env.requests().get(details.tabId());
      if (!Boolean.TRUE.equals(request.details().fromCache())) {
        request.queryConnectionInfoAndSetIcon();
      }
    }
  }

  /** Accepts the content script's unsolicited report of how its page connected. */
  void onMessage(Object csRequest, MessageSender sender, Consumer<Object> sendResponse) {
    Request request = env.requests().get(sender.tab().id());
    if (request != null) {
      request.setConnectionInfo((ConnectionInfo) csRequest);
    }
    sendResponse.accept(new Object());
  }

  /** Clears request data when a tab is destroyed. */
  void onTabRemoved(int tabId) {
    env.requests().remove(tabId);
  }
}
