package com.cloudflare.claire.dom;

/**
 * A {@code PerformanceNavigationTiming} entry.
 *
 * <p>{@code nextHopProtocol} is nullable: the browser reports an empty or absent protocol for
 * cross-origin responses without {@code Timing-Allow-Origin}.
 */
public record PerformanceEntry(String nextHopProtocol) {
}
