package com.cloudflare.claire.browser;

/** The {@code queryInfo} argument of {@code chrome.tabs.query}. */
public record TabQueryInfo(boolean active, int windowId) {
}
