package com.cloudflare.claire.testing;

import com.cloudflare.claire.browser.Console;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** A {@link Console} that records what was logged so a test can assert on it. */
public final class FakeConsole implements Console {

  private final List<List<Object>> calls = new ArrayList<>();

  @Override
  public void log(Object... args) {
    calls.add(Arrays.asList(args));
  }

  public List<List<Object>> calls() {
    return List.copyOf(calls);
  }

  public List<String> lines() {
    return calls.stream()
        .map(call -> call.stream().map(String::valueOf).collect(Collectors.joining(" ")))
        .toList();
  }
}
