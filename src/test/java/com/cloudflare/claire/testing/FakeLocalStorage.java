package com.cloudflare.claire.testing;

import com.cloudflare.claire.dom.LocalStorage;
import java.util.LinkedHashMap;
import java.util.Map;

/** An in-memory {@link LocalStorage}, standing in for {@code global.localStorage = {}}. */
public final class FakeLocalStorage implements LocalStorage {

  private final Map<String, String> entries = new LinkedHashMap<>();

  public static FakeLocalStorage empty() {
    return new FakeLocalStorage();
  }

  public static FakeLocalStorage of(String key, String value) {
    FakeLocalStorage storage = new FakeLocalStorage();
    storage.setItem(key, value);
    return storage;
  }

  @Override
  public String getItem(String key) {
    return entries.get(key);
  }

  @Override
  public void setItem(String key, String value) {
    entries.put(key, value);
  }
}
