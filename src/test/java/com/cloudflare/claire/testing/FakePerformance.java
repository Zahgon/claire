package com.cloudflare.claire.testing;

import com.cloudflare.claire.dom.Performance;
import com.cloudflare.claire.dom.PerformanceEntry;
import java.util.List;

/** A {@link Performance} whose navigation entries a test supplies. */
public record FakePerformance(List<PerformanceEntry> navigationEntries) implements Performance {

  /** A page that reports the given first-hop protocol. */
  public static FakePerformance withProtocol(String protocol) {
    return new FakePerformance(List.of(new PerformanceEntry(protocol)));
  }

  /** A page whose Performance Timeline holds no navigation entry. */
  public static FakePerformance withoutNavigationEntry() {
    return new FakePerformance(List.of());
  }

  @Override
  public List<PerformanceEntry> getEntriesByType(String type) {
    return Performance.NAVIGATION.equals(type) ? navigationEntries : List.of();
  }
}
