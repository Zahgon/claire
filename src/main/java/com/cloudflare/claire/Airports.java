package com.cloudflare.claire;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * The IATA-code to airport lookup table, migrated from {@code source/airports.js}.
 *
 * <p>{@code airports.js} is a CommonJS module whose entire body is {@code module.exports = { ...
 * }} &mdash; data, not code. It is carried over verbatim as {@code airports.json} on the
 * classpath, so the table itself is byte-identical to the source's and cannot drift from it.
 *
 * <p>The source module is a plain object indexed as {@code airports[code]}, which yields
 * {@code undefined} for an unknown code and never throws. {@link #lookup(String)} matches that by
 * returning {@code null}, including for a {@code null} code.
 */
public final class Airports {

  private static final String RESOURCE = "/airports.json";

  private static final Map<String, Airport> TABLE = load();

  private Airports() {
  }

  /** {@code airports[code]} &mdash; {@code null} when the code is unknown or absent. */
  public static Airport lookup(String code) {
    if (code == null) {
      return null;
    }
    return TABLE.get(code);
  }

  /** The number of airports in the table; lets a test prove the whole file was carried over. */
  public static int size() {
    return TABLE.size();
  }

  private static Map<String, Airport> load() {
    try (InputStream stream = Airports.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException("airport table missing from the classpath: " + RESOURCE);
      }
      return Map.copyOf(
          new ObjectMapper().readValue(stream, new TypeReference<Map<String, Airport>>() {
          }));
    } catch (IOException cause) {
      throw new UncheckedIOException("airport table could not be read: " + RESOURCE, cause);
    }
  }
}
