package com.cloudflare.claire.browser;

import com.cloudflare.claire.RequestRegistry;

/**
 * The background page's {@code window}, as handed to the popup by
 * {@code chrome.extension.getBackgroundPage()}.
 *
 * <p>The only member the popup touches is {@code window.requests}.
 */
public interface BackgroundPage {

  /** {@code window.requests} &mdash; the tab-id to {@code Request} map. */
  RequestRegistry requests();
}
