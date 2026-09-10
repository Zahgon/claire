package com.cloudflare.claire.browser;

/** The {@code sender} argument of a {@code chrome.runtime.onMessage} listener. */
public record MessageSender(Tab tab) {
}
