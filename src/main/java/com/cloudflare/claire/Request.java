package com.cloudflare.claire;

import com.cloudflare.claire.browser.ChromeApi;
import com.cloudflare.claire.browser.ConnectionInfo;
import com.cloudflare.claire.browser.ContentScriptMessage;
import com.cloudflare.claire.browser.Header;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.dom.LocalStorage;
import com.cloudflare.claire.js.JsUrl;
import com.cloudflare.claire.js.JsValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Information about one completed web request, migrated from {@code source/request.js}.
 *
 * <p>Header names are upper-cased on the way in and only the last value of a repeated header
 * survives &mdash; the source's comment calls that out as a known limitation, and it is preserved
 * rather than repaired.
 */
public final class Request {

  private static final String RAILGUN_HEADER = "CF-RAILGUN";
  private static final String RAY_HEADER = "CF-RAY";
  private static final String SERVER_HEADER = "SERVER";

  /** {@code /^cloudflare/i} &mdash; anchored at the start of the value, case-insensitive. */
  private static final java.util.regex.Pattern CLOUDFLARE_SERVER =
      java.util.regex.Pattern.compile("^cloudflare", java.util.regex.Pattern.CASE_INSENSITIVE);

  private static final String TRACE_PATH = "/cdn-cgi/trace";
  private static final String POPUP_PAGE = "page-action-popup.html";
  private static final String PAGE_ACTION_BASE_PATH = "images/claire-3-";
  private static final String POPUP_BASE_PATH = "images/claire-3-popup-";
  private static final String HTTP2 = "h2";

  private final ExtensionEnvironment env;
  private final RequestDetails details;
  private final List<Header> headersRaw;
  private final Map<String, String> headers = new LinkedHashMap<>();

  /**
   * Whether the content script has reported how this request connected. Only reachable through
   * message passing from the extension to the page, so it starts out false.
   */
  private boolean hasConnectionInfo;

  private String connectionType;
  private RailgunMetaData railgunMetaData;

  /**
   * Builds a request from the {@code details} object {@code chrome.webRequest.onCompleted}
   * delivered, and immediately pre-processes its headers.
   *
   * <p>The environment is a constructor argument because JavaScript reads {@code chrome},
   * {@code console} and {@code localStorage} off the global object, which Java cannot do.
   */
  public Request(ExtensionEnvironment env, RequestDetails details) {
    this.env = Objects.requireNonNull(env, "env");
    this.details = Objects.requireNonNull(details, "details");
    this.headersRaw = details.responseHeaders();
    this.connectionType = null;
    preProcessHeaders();
  }

  /**
   * Converts the raw header array into a name-to-value map with upper-cased names.
   *
   * <p>Warning, carried over from the source: only the last of several headers sharing a name is
   * kept.
   */
  private void preProcessHeaders() {
    for (Header header : headersRaw) {
      headers.put(JsValues.toUpperCase(header.name()), header.value());
    }
    if (headers.containsKey(RAILGUN_HEADER)) {
      processRailgunHeader();
    }
  }

  private void processRailgunHeader() {
    String railgunHeader = headers.get(RAILGUN_HEADER);

    railgunMetaData = new RailgunMetaData();

    // `typeof railgunHeader !== 'string'`: the WebRequest API omits `value` for headers it
    // delivers as bytes, which leaves the metadata object created but empty.
    if (railgunHeader == null) {
      return;
    }

    // The Railgun header comes in one of two formats; one of them contains the word "normal".
    boolean railgunNormal = railgunHeader.contains("normal");

    String[] parts = JsValues.split(railgunHeader, " ");

    double flagsBitset;

    railgunMetaData.setNormal(railgunNormal);
    railgunMetaData.setId(JsValues.at(parts, 0));
    if (railgunNormal) {
      flagsBitset = JsValues.parseIntRadix10(JsValues.at(parts, 1));
      railgunMetaData.setVersion(JsValues.at(parts, 3));
    } else {
      double compressionPercent = 100 - JsValues.toNumber(JsValues.at(parts, 1));
      railgunMetaData.setCompression(JsValues.numberToString(compressionPercent) + "%");
      railgunMetaData.setTime(JsValues.concat(JsValues.at(parts, 2), "sec"));
      flagsBitset = JsValues.parseIntRadix10(JsValues.at(parts, 3));
      railgunMetaData.setVersion(JsValues.at(parts, 4));
    }

    // Decode the flags bitset. A header with no numeric field leaves `flagsBitset` NaN, which
    // ToInt32 turns into 0, so no message is emitted rather than the decode failing.
    int mask = JsValues.toInt32(flagsBitset);
    List<String> messages = new ArrayList<>();
    for (RailgunFlag flag : RailgunFlag.values()) {
      if ((mask & flag.position()) != 0) {
        messages.add(flag.message());
      }
    }

    railgunMetaData.setFlags(flagsBitset);
    railgunMetaData.setMessages(messages);
  }

  /**
   * Asks the tab's content script how it connected, then paints the page action.
   *
   * <p>When the answer is already known the icon is painted straight away.
   */
  public void queryConnectionInfoAndSetIcon() {
    int tabId = details.tabId();
    if (hasConnectionInfo) {
      setPageActionIconAndPopup();
      return;
    }

    ContentScriptMessage csMessageData = ContentScriptMessage.checkConnectionInfo();
    java.util.function.Consumer<Object> csMessageCallback = csMsgResponse -> {
      // Stop and return if we don't get a response: that happens with background tabs, or when
      // the next hop information is unavailable.
      if (csMsgResponse == null) {
        return;
      }

      // Re-read from the registry rather than using `this`, exactly as the source does: by the
      // time the reply arrives the tab may have been rebound to a newer request.
      Request request = env.requests().get(tabId);
      request.setConnectionInfo((ConnectionInfo) csMsgResponse);
      request.setPageActionIconAndPopup();
    };

    try {
      env.chrome().tabsSendMessage(details.tabId(), csMessageData, csMessageCallback);
    } catch (RuntimeException err) {
      env.console().log("caught exception when sending message to content script");
      env.console().log(env.chrome().extensionLastError());
      env.console().log(err);
    }
  }

  /** Whether the {@code Server} header starts with {@code cloudflare}. */
  public boolean servedByCloudFlare() {
    return headers.containsKey(SERVER_HEADER)
        && CLOUDFLARE_SERVER.matcher(JsValues.orUndefined(headers.get(SERVER_HEADER))).find();
  }

  /** Whether the response carried a {@code CF-RAILGUN} header. */
  public boolean servedByRailgun() {
    return headers.containsKey(RAILGUN_HEADER);
  }

  /** Whether the content script reported an HTTP/2 connection. */
  public boolean servedOverH2() {
    return HTTP2.equals(connectionType);
  }

  /**
   * {@code details.fromCache} &mdash; {@code null} when the browser did not report it.
   *
   * <p>Renamed from the source's {@code ServedFromBrowserCache} to Java's method-naming
   * convention; a type (A) difference with no behavioural effect.
   */
  public Boolean servedFromBrowserCache() {
    return details.fromCache();
  }

  /**
   * The Ray ID, or {@code null} when the response carried no usable {@code CF-RAY} header.
   *
   * <p>Header format: {@code CF-RAY:f694c6892660106-DFW}. An empty header value is falsy in the
   * source and so yields nothing here either.
   */
  public String getRayID() {
    String ray = headers.get(RAY_HEADER);
    if (JsValues.isTruthy(ray)) {
      return JsValues.at(JsValues.split(ray, "-"), 0);
    }
    return null;
  }

  /** The Cloudflare colo code from the {@code CF-RAY} header, or {@code null}. */
  public String getCloudFlareLocationCode() {
    String ray = headers.get(RAY_HEADER);
    if (JsValues.isTruthy(ray)) {
      return JsValues.at(JsValues.split(ray, "-"), 1);
    }
    return null;
  }

  /** The airport record for this request's colo, or {@code null} when the code is unknown. */
  public Airport getCloudFlareLocationData() {
    return Airports.lookup(getCloudFlareLocationCode());
  }

  /**
   * A human-readable colo location.
   *
   * <p>Falls back to the bare colo code when the airport table has no entry for it, which is what
   * makes a newly opened Cloudflare data centre show as e.g. {@code "ZZZ"} rather than blank.
   */
  public String getCloudFlareLocationName() {
    Airport airportData = getCloudFlareLocationData();
    if (airportData != null) {
      return JsValues.orUndefined(airportData.city()) + ", "
          + JsValues.orUndefined(airportData.country());
    }
    return getCloudFlareLocationCode();
  }

  /** This request's URL with its path replaced by {@code /cdn-cgi/trace}. */
  public String getCloudFlareTrace() {
    JsUrl traceUrl = JsUrl.parse(details.url());
    traceUrl.setPathname(TRACE_PATH);
    return traceUrl.toString();
  }

  /** The id of the tab this request belongs to. */
  public int getTabID() {
    return details.tabId();
  }

  /** The URL that was requested. */
  public String getRequestURL() {
    return details.url();
  }

  /** The decoded Railgun header, or {@code null} when there was none. */
  public RailgunMetaData getRailgunMetaData() {
    return railgunMetaData;
  }

  /** The origin server's IP, or the empty string when the browser did not report one. */
  public String getServerIP() {
    return JsValues.isTruthy(details.ip()) ? details.ip() : "";
  }

  /** Whether the origin server's IP is IPv6. */
  public boolean isv6IP() {
    return getServerIP().indexOf(':') != -1;
  }

  /** The page-action icon for the features detected in this request, without a file extension. */
  public String getPageActionPath() {
    return getImagePath(PAGE_ACTION_BASE_PATH);
  }

  /** The popup illustration for the features detected in this request, without an extension. */
  public String getPopupPath() {
    return getImagePath(POPUP_BASE_PATH);
  }

  /** Builds an image path by appending one suffix per detected feature, in a fixed order. */
  public String getImagePath(String basePath) {
    List<String> iconPathParts = new ArrayList<>();

    if (servedByCloudFlare()) {
      iconPathParts.add("on");
    } else {
      iconPathParts.add("off");
    }

    if (servedOverH2()) {
      iconPathParts.add(HTTP2);
    }

    if (isv6IP()) {
      iconPathParts.add("ipv6");
    }

    if (servedByRailgun()) {
      iconPathParts.add("rg");
    }

    return basePath + String.join("-", iconPathParts);
  }

  /** Records the content script's answer about how this request connected. */
  public void setConnectionInfo(ConnectionInfo connectionInfo) {
    hasConnectionInfo = true;
    connectionType = connectionInfo.type();
  }

  /** Paints the page action icon, attaches the popup, and reveals the icon. */
  public void setPageActionIconAndPopup() {
    String iconPath = getPageActionPath();
    int tabId = details.tabId();
    ChromeApi chrome = env.chrome();
    chrome.pageActionSetIcon(
        tabId,
        Map.of(19, iconPath + ".png", 38, iconPath + "@2x.png"),
        () -> {
          try {
            chrome.pageActionSetPopup(tabId, POPUP_PAGE);
            chrome.pageActionShow(tabId);
          } catch (RuntimeException err) {
            env.console().log("Exception on page action show for tab with ID: ", tabId, err);
          }
        });
  }

  /** Dumps this request to the console, but only while the debug logging option is on. */
  public void logToConsole() {
    if (!LocalStorage.YES.equals(env.localStorage().getItem(LocalStorage.DEBUG_LOGGING))) {
      return;
    }

    env.console().log("\n");
    env.console().log(details.url(), details.ip(), "CF - " + servedByCloudFlare());
    env.console().log("Request - ", details);
    if (servedByCloudFlare()) {
      env.console().log("Ray ID - ", getRayID());
    }
    if (servedByRailgun()) {
      RailgunMetaData metaData = getRailgunMetaData();
      env.console().log("Railgun - ", metaData.id(), String.join("; ", metaData.messages()));
    }
  }

  /** The {@code details} object this request was built from. */
  public RequestDetails details() {
    return details;
  }

  /** The pre-processed headers: upper-cased names, last value wins. */
  public Map<String, String> headers() {
    return Collections.unmodifiableMap(headers);
  }

  /** The response headers exactly as the browser delivered them. */
  public List<Header> headersRaw() {
    return headersRaw;
  }

  /** Whether the content script has reported this request's connection type. */
  public boolean hasConnectionInfo() {
    return hasConnectionInfo;
  }

  /** The connection type the content script reported, or {@code null} if it has not. */
  public String connectionType() {
    return connectionType;
  }
}
