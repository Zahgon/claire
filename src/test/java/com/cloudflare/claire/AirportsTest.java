package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves the airport table survived the move out of {@code airports.js} intact. */
class AirportsTest {

  @Test
  @DisplayName("carries over every airport from the source table")
  void carriesOverEveryAirportFromTheSourceTable() {
    assertEquals(5763, Airports.size());
  }

  @Test
  @DisplayName("looks an airport up by its IATA code")
  void looksAnAirportUpByItsIataCode() {
    Airport sfo = Airports.lookup("SFO");

    assertNotNull(sfo);
    assertEquals("San Francisco Intl", sfo.name());
    assertEquals("San Francisco", sfo.city());
    assertEquals("United States", sfo.country());
    assertEquals("SFO", sfo.iata());
    assertEquals("KSFO", sfo.icao());
    assertEquals("37.618972", sfo.latitude());
    assertEquals("-122.374889", sfo.longitude());
    assertEquals("13", sfo.altitude());
    assertEquals("-8", sfo.timezone());
    assertEquals("A", sfo.dst());
  }

  @Test
  @DisplayName("preserves the first entry of the source table")
  void preservesTheFirstEntryOfTheSourceTable() {
    Airport gka = Airports.lookup("GKA");

    assertNotNull(gka);
    assertEquals("Goroka", gka.name());
    assertEquals("Papua New Guinea", gka.country());
    assertEquals("AYGA", gka.icao());
  }

  @Test
  @DisplayName("yields nothing for an unknown or absent code")
  void yieldsNothingForAnUnknownOrAbsentCode() {
    assertNull(Airports.lookup("ZZZ"));
    assertNull(Airports.lookup(null));
  }

  @Test
  @DisplayName("keeps the upstream entry filed under a blank IATA code")
  void keepsTheUpstreamEntryFiledUnderABlankIataCode() {
    // The source table really does hold a record whose IATA code is the empty string, so
    // airports[''] resolves rather than missing. Carried over rather than cleaned up: a
    // CF-RAY of "deadbeef-" resolves to it in the original too.
    Airport blank = Airports.lookup("");

    assertNotNull(blank);
    assertEquals("Kvarkeno", blank.city());
    assertEquals("Russia", blank.country());
    assertEquals("", blank.iata());
  }
}
