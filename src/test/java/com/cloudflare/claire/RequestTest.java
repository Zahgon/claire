package com.cloudflare.claire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cloudflare.claire.browser.Header;
import com.cloudflare.claire.browser.RequestDetails;
import com.cloudflare.claire.testing.TestEnvironment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Migrated 1:1 from {@code source/request.test.js}.
 *
 * <p>Test names, nesting and assertions follow the source file so each case can be matched back
 * to the one it replaces.
 */
class RequestTest {

  private static Request request(Header... headers) {
    return new Request(TestEnvironment.create(), RequestDetails.withHeaders(headers).build());
  }

  @Test
  @DisplayName("preprocesses headers")
  void preprocessesHeaders() {
    Request r = request(new Header("server", "cloudflare"));

    assertEquals("cloudflare", r.headers().get("SERVER"));
  }

  @Nested
  @DisplayName("Railgun")
  class Railgun {

    @Test
    @DisplayName("processes Railgun header v1")
    void processesRailgunHeaderV1() {
      Request r = request(new Header("cf-railgun", "normal 96 foobar 9001"));

      RailgunMetaData metaData = r.getRailgunMetaData();
      assertEquals(Boolean.TRUE, metaData.normal());
      assertEquals("normal", metaData.id());
      assertEquals("9001", metaData.version());
      assertEquals(0x60, metaData.flags());
      assertEquals(
          List.of("Dictionary found in memcache", "Restarted broken origin connection"),
          metaData.messages());
    }

    @Test
    @DisplayName("processes Railgun header v2")
    void processesRailgunHeaderV2() {
      Request r = request(new Header("cf-railgun", "foobar 46 700 96 9001"));

      RailgunMetaData metaData = r.getRailgunMetaData();
      assertEquals(Boolean.FALSE, metaData.normal());
      assertEquals("foobar", metaData.id());
      assertEquals("9001", metaData.version());
      assertEquals("54%", metaData.compression());
      assertEquals("700sec", metaData.time());
      assertEquals(0x60, metaData.flags());
      assertEquals(
          List.of("Dictionary found in memcache", "Restarted broken origin connection"),
          metaData.messages());
    }

    @Test
    @DisplayName("determines Railgun")
    void determinesRailgun() {
      Request rRailgun = request(new Header("cf-railgun", "normal"));
      Request rNoRailgun = request();

      assertTrue(rRailgun.servedByRailgun());
      assertFalse(rNoRailgun.servedByRailgun());
    }
  }

  @Test
  @DisplayName("determines Cloudflare-ness")
  void determinesCloudflareNess() {
    Request rCloudflare = request(new Header("server", "cloudflare-fl"));
    Request rNotCloudflare = request(new Header("server", "nginx"));

    assertTrue(rCloudflare.servedByCloudFlare());
    assertFalse(rNotCloudflare.servedByCloudFlare());
  }

  @Test
  @DisplayName("IPv6")
  void iPv6() {
    Request rv4 = new Request(
        TestEnvironment.create(), RequestDetails.withHeaders().ip("8.8.8.8").build());
    Request rv6 = new Request(
        TestEnvironment.create(),
        RequestDetails.withHeaders().ip("2001:4860:4860::8888").build());

    assertEquals("8.8.8.8", rv4.getServerIP());
    assertFalse(rv4.isv6IP());

    assertEquals("2001:4860:4860::8888", rv6.getServerIP());
    assertTrue(rv6.isv6IP());
  }

  @Nested
  @DisplayName("Ray ID")
  class RayId {

    @Test
    @DisplayName("get Ray ID")
    void getRayId() {
      Request rCloudflare = request(new Header("CF-Ray", "deadbeef-SFO"));
      Request rNotCloudflare = request();

      assertEquals("deadbeef", rCloudflare.getRayID());
      assertNull(rNotCloudflare.getRayID());
    }

    @Test
    @DisplayName("get location code")
    void getLocationCode() {
      Request rCloudflare = request(new Header("CF-Ray", "deadbeef-SFO"));
      Request rNotCloudflare = request();

      assertEquals("SFO", rCloudflare.getCloudFlareLocationCode());
      assertNull(rNotCloudflare.getCloudFlareLocationCode());
    }

    @Test
    @DisplayName("location data")
    void locationData() {
      Request r = request(new Header("cf-ray", "deadbeef-SFO"));

      Airport airport = r.getCloudFlareLocationData();
      assertNotNull(airport);
      assertInstanceOf(String.class, airport.latitude());
      assertInstanceOf(String.class, airport.longitude());
      assertEquals("San Francisco", airport.city());
      assertEquals("United States", airport.country());
    }

    @Test
    @DisplayName("location name")
    void locationName() {
      Request r = request(new Header("cf-ray", "deadbeef-SFO"));

      assertEquals("San Francisco, United States", r.getCloudFlareLocationName());
    }
  }

  @Nested
  @DisplayName("header pre-processing edge cases")
  class HeaderPreProcessing {

    @Test
    @DisplayName("keeps only the last of repeated headers")
    void keepsOnlyTheLastOfRepeatedHeaders() {
      Request r = request(new Header("server", "first"), new Header("Server", "last"));

      assertEquals(Map.of("SERVER", "last"), r.headers());
    }

    @Test
    @DisplayName("upper-cases header names independently of the default locale")
    void upperCasesHeaderNamesIndependentlyOfTheDefaultLocale() {
      java.util.Locale original = java.util.Locale.getDefault();
      try {
        // Turkish maps 'i' to a dotted capital, which would corrupt "CF-RAILGUN".
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
        Request r = request(new Header("cf-railgun", "normal"));

        assertTrue(r.headers().containsKey("CF-RAILGUN"));
        assertTrue(r.servedByRailgun());
      } finally {
        java.util.Locale.setDefault(original);
      }
    }

    @Test
    @DisplayName("exposes the raw headers untouched")
    void exposesTheRawHeadersUntouched() {
      Header raw = new Header("server", "cloudflare");
      Request r = request(raw);

      assertEquals(List.of(raw), r.headersRaw());
    }
  }

  @Nested
  @DisplayName("Server header matching")
  class ServerHeaderMatching {

    @Test
    @DisplayName("matches cloudflare case-insensitively at the start only")
    void matchesCloudflareCaseInsensitivelyAtTheStartOnly() {
      assertTrue(request(new Header("server", "CLOUDFLARE")).servedByCloudFlare());
      assertTrue(request(new Header("server", "cloudflare-nginx")).servedByCloudFlare());
      assertFalse(request(new Header("server", "xcloudflare")).servedByCloudFlare());
    }

    @Test
    @DisplayName("treats a valueless Server header as not Cloudflare")
    void treatsAValuelessServerHeaderAsNotCloudflare() {
      Request r = request(Header.withoutValue("server"));

      assertFalse(r.servedByCloudFlare());
    }
  }
}
