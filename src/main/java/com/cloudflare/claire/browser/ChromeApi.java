package com.cloudflare.claire.browser;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The {@code chrome.*} extension API, narrowed to the calls Claire actually makes.
 *
 * <p>In JavaScript {@code chrome} is an ambient global installed by the browser. Java has no
 * ambient globals, so it becomes an interface that the extension entry points receive. That is a
 * type (A) difference: the call sequence and its arguments are unchanged, only the way the object
 * is reached differs &mdash; and it is what makes the listener wiring testable at all.
 *
 * <p>Names keep their {@code namespace.member} shape ({@code tabsSendMessage} for
 * {@code chrome.tabs.sendMessage}) so each call site maps back to one line of source.
 */
public interface ChromeApi {

  /** {@code chrome.windows.WINDOW_ID_CURRENT}. */
  int WINDOW_ID_CURRENT = -2;

  /** {@code chrome.webRequest.onCompleted.addListener(listener, filter, extraInfoSpec)}. */
  void webRequestOnCompletedAddListener(
      Consumer<RequestDetails> listener, WebRequestFilter filter, List<String> extraInfoSpec);

  /** {@code chrome.webNavigation.onDOMContentLoaded.addListener(listener)}. */
  void webNavigationOnDomContentLoadedAddListener(Consumer<NavigationDetails> listener);

  /** {@code chrome.runtime.onMessage.addListener(listener)}. */
  void runtimeOnMessageAddListener(MessageListener listener);

  /** {@code chrome.runtime.sendMessage(message, responseCallback)}. */
  void runtimeSendMessage(Object message, Consumer<Object> responseCallback);

  /** {@code chrome.tabs.onReplaced.addListener(listener)}. */
  void tabsOnReplacedAddListener(TabReplacedListener listener);

  /** {@code chrome.tabs.onRemoved.addListener(listener)}. */
  void tabsOnRemovedAddListener(Consumer<Integer> listener);

  /** {@code chrome.tabs.sendMessage(tabId, message, responseCallback)}. */
  void tabsSendMessage(int tabId, Object message, Consumer<Object> responseCallback);

  /** {@code chrome.tabs.query(queryInfo, callback)}. */
  void tabsQuery(TabQueryInfo queryInfo, Consumer<List<Tab>> callback);

  /** {@code chrome.pageAction.setIcon({tabId, path}, callback)}. */
  void pageActionSetIcon(int tabId, Map<Integer, String> path, Runnable callback);

  /** {@code chrome.pageAction.setPopup({tabId, popup})}. */
  void pageActionSetPopup(int tabId, String popup);

  /** {@code chrome.pageAction.show(tabId)}. */
  void pageActionShow(int tabId);

  /** {@code chrome.extension.getBackgroundPage()}. */
  BackgroundPage extensionGetBackgroundPage();

  /** {@code chrome.extension.lastError()}. */
  Object extensionLastError();

  /** A {@code chrome.runtime.onMessage} listener: {@code (message, sender, sendResponse)}. */
  @FunctionalInterface
  interface MessageListener {
    void onMessage(Object message, MessageSender sender, Consumer<Object> sendResponse);
  }

  /** A {@code chrome.tabs.onReplaced} listener: {@code (addedTabId, removedTabId)}. */
  @FunctionalInterface
  interface TabReplacedListener {
    void onReplaced(int addedTabId, int removedTabId);
  }
}
