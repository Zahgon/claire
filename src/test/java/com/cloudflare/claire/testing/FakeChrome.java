package com.cloudflare.claire.testing;

import com.cloudflare.claire.browser.BackgroundPage;
import com.cloudflare.claire.browser.ChromeApi;
import com.cloudflare.claire.browser.MessageSender;
import com.cloudflare.claire.browser.NavigationDetails;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.browser.Tab;
import com.cloudflare.claire.browser.TabQueryInfo;
import com.cloudflare.claire.browser.WebRequestFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A scriptable {@code chrome} stand-in.
 *
 * <p>Registered listeners are kept so a test can fire the browser events the extension is waiting
 * for, and every outgoing call is recorded so a test can assert what the extension asked the
 * browser to do.
 */
public final class FakeChrome implements ChromeApi {

  private final List<Object> iconCalls = new ArrayList<>();
  private final List<Integer> shownTabs = new ArrayList<>();
  private final Map<Integer, String> popups = new LinkedHashMap<>();
  private final List<Object> sentRuntimeMessages = new ArrayList<>();
  private final List<Object> sentTabMessages = new ArrayList<>();
  private final List<TabQueryInfo> tabQueries = new ArrayList<>();

  private Consumer<RequestDetails> webRequestListener;
  private WebRequestFilter registeredFilter;
  private List<String> registeredExtraInfoSpec;
  private Consumer<NavigationDetails> navigationListener;
  private MessageListener messageListener;
  private TabReplacedListener tabReplacedListener;
  private Consumer<Integer> tabRemovedListener;

  private BackgroundPage backgroundPage;
  private List<Tab> queryResult = List.of();
  private Object tabMessageReply;
  private RuntimeException tabsSendMessageFailure;
  private RuntimeException pageActionShowFailure;
  private Object lastError = "no error";
  private boolean invokeIconCallback = true;

  /** Records an icon paint: which tab, and the 19px/38px path pair. */
  public record IconCall(int tabId, Map<Integer, String> path) {
  }

  public FakeChrome withBackgroundPage(BackgroundPage page) {
    this.backgroundPage = page;
    return this;
  }

  public FakeChrome withQueryResult(Tab... tabs) {
    this.queryResult = List.of(tabs);
    return this;
  }

  /** The value the content script will answer {@code tabsSendMessage} with. */
  public FakeChrome withTabMessageReply(Object reply) {
    this.tabMessageReply = reply;
    return this;
  }

  /** Makes {@code chrome.tabs.sendMessage} throw, as it does when the tab has gone away. */
  public FakeChrome failingTabsSendMessage(RuntimeException failure) {
    this.tabsSendMessageFailure = failure;
    return this;
  }

  /** Makes {@code chrome.pageAction.show} throw, as it does for a destroyed tab. */
  public FakeChrome failingPageActionShow(RuntimeException failure) {
    this.pageActionShowFailure = failure;
    return this;
  }

  public FakeChrome withLastError(Object error) {
    this.lastError = error;
    return this;
  }

  /** Suppresses the {@code setIcon} completion callback, as Chrome does when the tab is gone. */
  public FakeChrome withoutIconCallback() {
    this.invokeIconCallback = false;
    return this;
  }

  @Override
  public void webRequestOnCompletedAddListener(
      Consumer<RequestDetails> listener, WebRequestFilter filter, List<String> extraInfoSpec) {
    this.webRequestListener = listener;
    this.registeredFilter = filter;
    this.registeredExtraInfoSpec = extraInfoSpec;
  }

  @Override
  public void webNavigationOnDomContentLoadedAddListener(Consumer<NavigationDetails> listener) {
    this.navigationListener = listener;
  }

  @Override
  public void runtimeOnMessageAddListener(MessageListener listener) {
    this.messageListener = listener;
  }

  @Override
  public void runtimeSendMessage(Object message, Consumer<Object> responseCallback) {
    sentRuntimeMessages.add(message);
    responseCallback.accept(null);
  }

  @Override
  public void tabsOnReplacedAddListener(TabReplacedListener listener) {
    this.tabReplacedListener = listener;
  }

  @Override
  public void tabsOnRemovedAddListener(Consumer<Integer> listener) {
    this.tabRemovedListener = listener;
  }

  @Override
  public void tabsSendMessage(int tabId, Object message, Consumer<Object> responseCallback) {
    if (tabsSendMessageFailure != null) {
      throw tabsSendMessageFailure;
    }
    sentTabMessages.add(message);
    responseCallback.accept(tabMessageReply);
  }

  @Override
  public void tabsQuery(TabQueryInfo queryInfo, Consumer<List<Tab>> callback) {
    tabQueries.add(queryInfo);
    callback.accept(queryResult);
  }

  @Override
  public void pageActionSetIcon(int tabId, Map<Integer, String> path, Runnable callback) {
    iconCalls.add(new IconCall(tabId, Map.copyOf(path)));
    if (invokeIconCallback) {
      callback.run();
    }
  }

  @Override
  public void pageActionSetPopup(int tabId, String popup) {
    popups.put(tabId, popup);
  }

  @Override
  public void pageActionShow(int tabId) {
    if (pageActionShowFailure != null) {
      throw pageActionShowFailure;
    }
    shownTabs.add(tabId);
  }

  @Override
  public BackgroundPage extensionGetBackgroundPage() {
    return backgroundPage;
  }

  @Override
  public Object extensionLastError() {
    return lastError;
  }

  /** Fires {@code chrome.webRequest.onCompleted}. */
  public void fireRequestCompleted(RequestDetails details) {
    webRequestListener.accept(details);
  }

  /** Fires {@code chrome.webNavigation.onDOMContentLoaded}. */
  public void fireDomContentLoaded(NavigationDetails details) {
    navigationListener.accept(details);
  }

  /** Fires {@code chrome.runtime.onMessage}. */
  public void fireMessage(Object message, MessageSender sender, Consumer<Object> sendResponse) {
    messageListener.onMessage(message, sender, sendResponse);
  }

  /** Fires {@code chrome.tabs.onReplaced}. */
  public void fireTabReplaced(int addedTabId, int removedTabId) {
    tabReplacedListener.onReplaced(addedTabId, removedTabId);
  }

  /** Fires {@code chrome.tabs.onRemoved}. */
  public void fireTabRemoved(int tabId) {
    tabRemovedListener.accept(tabId);
  }

  public boolean hasWebRequestListener() {
    return webRequestListener != null;
  }

  public WebRequestFilter registeredFilter() {
    return registeredFilter;
  }

  public List<String> registeredExtraInfoSpec() {
    return registeredExtraInfoSpec;
  }

  @SuppressWarnings("unchecked")
  public List<IconCall> iconCalls() {
    return (List<IconCall>) (List<?>) List.copyOf(iconCalls);
  }

  public List<Integer> shownTabs() {
    return List.copyOf(shownTabs);
  }

  public Map<Integer, String> popups() {
    return Map.copyOf(popups);
  }

  public List<Object> sentRuntimeMessages() {
    return List.copyOf(sentRuntimeMessages);
  }

  public List<Object> sentTabMessages() {
    return List.copyOf(sentTabMessages);
  }

  public List<TabQueryInfo> tabQueries() {
    return List.copyOf(tabQueries);
  }
}
