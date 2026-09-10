package com.cloudflare.claire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of the airport table.
 *
 * <p>Every field is a string because every value in {@code airports.js} is a string &mdash;
 * including {@code latitude}, {@code longitude}, {@code altitude} and {@code timezone}. The
 * source never converts them, and {@code request.test.js} asserts that latitude and longitude are
 * strings, so they are not "corrected" to numbers here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Airport(
    String name,
    String city,
    String country,
    String iata,
    String icao,
    String latitude,
    String longitude,
    String altitude,
    String timezone,
    String dst) {
}
