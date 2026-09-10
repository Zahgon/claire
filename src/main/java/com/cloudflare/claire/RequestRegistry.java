package com.cloudflare.claire;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code window.requests} &mdash; the background page's map of tab id to the last completed
 * {@link Request} for that tab.
 *
 * <p>In the source this is a property hung off the background page's {@code window}, written by
 * {@code claire.js} and read by {@code page-action-popup.js} through
 * {@code chrome.extension.getBackgroundPage()}. Java has no shared global, so the map is an
 * object the background page owns and hands out &mdash; a type (A) difference with the same
 * observable lifetime, and one that keeps the state out of a static field where tests could
 * leak it into one another.
 *
 * <p>{@code LinkedHashMap} rather than {@code HashMap}: {@code for...in} over the source object
 * is insertion-ordered for these keys, and a stable order keeps debug output reproducible.
 */
public final class RequestRegistry {

  private final Map<Integer, Request> byTabId = new LinkedHashMap<>();

  /** {@code window.requests[tabId]} &mdash; {@code null} when the tab has no recorded request. */
  public Request get(int tabId) {
    return byTabId.get(tabId);
  }

  /** {@code window.requests[tabId] = request}. */
  public void put(int tabId, Request request) {
    byTabId.put(tabId, request);
  }

  /** {@code tabId in window.requests}. */
  public boolean has(int tabId) {
    return byTabId.containsKey(tabId);
  }

  /** {@code delete window.requests[tabId]}. */
  public void remove(int tabId) {
    byTabId.remove(tabId);
  }

  /** How many tabs currently have a recorded request. */
  public int size() {
    return byTabId.size();
  }
}
