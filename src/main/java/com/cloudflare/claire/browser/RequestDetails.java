package com.cloudflare.claire.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The {@code details} object delivered to {@code chrome.webRequest.onCompleted}.
 *
 * <p>Only the five members the extension reads are modelled. {@code url}, {@code ip} and
 * {@code fromCache} are nullable because Chrome omits them for some requests and the source
 * relies on the resulting {@code undefined} being falsy.
 */
public record RequestDetails(
    int tabId,
    String url,
    String ip,
    Boolean fromCache,
    List<Header> responseHeaders) {

  public RequestDetails {
    responseHeaders = responseHeaders == null
        ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(responseHeaders));
  }

  /** Starts a {@code details} object with the given response headers and nothing else set. */
  public static Builder withHeaders(Header... headers) {
    return new Builder(List.of(headers));
  }

  /** Starts a {@code details} object with the given response headers and nothing else set. */
  public static Builder withHeaders(List<Header> headers) {
    return new Builder(headers);
  }

  /** Incremental construction of a {@code details} object. */
  public static final class Builder {

    private final List<Header> responseHeaders;
    private int tabId;
    private String url;
    private String ip;
    private Boolean fromCache;

    private Builder(List<Header> responseHeaders) {
      this.responseHeaders = responseHeaders;
    }

    public Builder tabId(int value) {
      this.tabId = value;
      return this;
    }

    public Builder url(String value) {
      this.url = value;
      return this;
    }

    public Builder ip(String value) {
      this.ip = value;
      return this;
    }

    public Builder fromCache(Boolean value) {
      this.fromCache = value;
      return this;
    }

    public RequestDetails build() {
      return new RequestDetails(tabId, url, ip, fromCache, responseHeaders);
    }
  }
}
