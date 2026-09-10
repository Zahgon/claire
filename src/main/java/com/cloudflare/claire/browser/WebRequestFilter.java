package com.cloudflare.claire.browser;

import java.util.List;

/** The {@code filter} argument of {@code chrome.webRequest.onCompleted.addListener}. */
public record WebRequestFilter(List<String> urls, List<String> types) {

  public WebRequestFilter {
    urls = List.copyOf(urls);
    types = List.copyOf(types);
  }
}
