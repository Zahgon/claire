package com.cloudflare.claire.browser;

/** The {@code details} object delivered to {@code chrome.webNavigation.onDOMContentLoaded}. */
public record NavigationDetails(int tabId, int frameId) {
}
