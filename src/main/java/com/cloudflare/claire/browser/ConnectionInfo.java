package com.cloudflare.claire.browser;

/**
 * The reply the content script sends back for {@code check_connection_info}.
 *
 * <p>{@code type} mirrors {@code PerformanceNavigationTiming.nextHopProtocol} and is nullable:
 * the browser leaves it empty when the protocol is unknown, and the source stores that as-is.
 */
public record ConnectionInfo(String type) {
}
