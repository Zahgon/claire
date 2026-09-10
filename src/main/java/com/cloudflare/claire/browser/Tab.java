package com.cloudflare.claire.browser;

/** A browser tab, as returned by {@code chrome.tabs.query}. */
public record Tab(int id) {
}
