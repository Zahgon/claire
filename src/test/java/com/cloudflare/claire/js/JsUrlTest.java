package com.cloudflare.claire.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the {@code URL} behaviour the trace link depends on, as observed in the browser. */
class JsUrlTest {

  private static String trace(String input) {
    JsUrl url = JsUrl.parse(input);
    url.setPathname("/cdn-cgi/trace");
    return url.toString();
  }

  @Test
  @DisplayName("replaces the path and keeps the query and fragment")
  void replacesThePathAndKeepsTheQueryAndFragment() {
    assertEquals("https://example.com/cdn-cgi/trace", trace("https://example.com/"));
    assertEquals("https://example.com/cdn-cgi/trace", trace("https://example.com"));
    assertEquals("https://example.com/cdn-cgi/trace?x=1#frag", trace("https://example.com/a/b?x=1#frag"));
  }

  @Test
  @DisplayName("drops a default port but keeps an explicit one")
  void dropsADefaultPortButKeepsAnExplicitOne() {
    assertEquals("https://example.com:8443/cdn-cgi/trace", trace("https://example.com:8443/a"));
    assertEquals("https://example.com/cdn-cgi/trace", trace("https://example.com:443/a"));
    assertEquals("http://example.com/cdn-cgi/trace", trace("http://example.com:80/a"));
    assertEquals("ws://example.com/cdn-cgi/trace", trace("ws://example.com:80/a"));
    assertEquals("wss://example.com/cdn-cgi/trace", trace("wss://example.com:443/a"));
    assertEquals("ftp://example.com/cdn-cgi/trace", trace("ftp://example.com:21/a"));
    assertEquals("http://example.com/cdn-cgi/trace", trace("http://example.com:/a"));
  }

  @Test
  @DisplayName("keeps userinfo and IPv6 hosts intact")
  void keepsUserinfoAndIpv6HostsIntact() {
    assertEquals("https://u:p@example.com/cdn-cgi/trace", trace("https://u:p@example.com/a"));
    assertEquals("https://[2001:db8::1]/cdn-cgi/trace", trace("https://[2001:db8::1]/a"));
    assertEquals("https://[2001:db8::1]:8443/cdn-cgi/trace", trace("https://[2001:db8::1]:8443/a"));
  }

  @Test
  @DisplayName("case-folds the host but not the userinfo")
  void caseFoldsTheHostButNotTheUserinfo() {
    assertEquals("https://example.com/cdn-cgi/trace", trace("https://EXAMPLE.COM/x"));
    assertEquals("https://example.com:8443/cdn-cgi/trace", trace("https://Example.COM:8443/x"));
    assertEquals("https://[2001:db8::1]/cdn-cgi/trace", trace("https://[2001:DB8::1]/x"));
    assertEquals("https://u:P@example.com/cdn-cgi/trace", trace("https://u:P@EXAMPLE.COM/x"));
  }

  @Test
  @DisplayName("case-folds the host independently of the default locale")
  void caseFoldsTheHostIndependentlyOfTheDefaultLocale() {
    java.util.Locale original = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
      assertEquals("https://iii.example/cdn-cgi/trace", trace("https://III.example/x"));
    } finally {
      java.util.Locale.setDefault(original);
    }
  }

  @Test
  @DisplayName("converts an internationalised host to its ASCII form")
  void convertsAnInternationalisedHostToItsAsciiForm() {
    assertEquals("https://xn--r8jz45g.jp/cdn-cgi/trace", trace("https://例え.jp/x"));
    assertEquals("https://xn--r8jz45g.jp/cdn-cgi/trace", trace("https://XN--R8JZ45G.JP/x"));
    assertEquals("https://xn--wxaikc6b.gr/cdn-cgi/trace", trace("https://ΣΌΛΟΣ.GR/x"));
  }

  @Test
  @DisplayName("percent-decodes the host before converting it")
  void percentDecodesTheHostBeforeConvertingIt() {
    assertEquals("https://example.com/cdn-cgi/trace", trace("https://ex%41mple.com/x"));
    assertEquals("https://xn--r8jz45g.jp/cdn-cgi/trace", trace("https://%E4%BE%8B%E3%81%88.jp/x"));
    assertEquals("https://a.b.example/cdn-cgi/trace", trace("https://a%2Eb.example/x"));
  }

  @Test
  @DisplayName("rejects a host carrying a malformed percent escape")
  void rejectsAHostCarryingAMalformedPercentEscape() {
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://%zz.example/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://ab%4.example/x"));
  }

  @Test
  @DisplayName("preserves the IDNA deviation characters instead of folding them")
  void preservesTheIdnaDeviationCharactersInsteadOfFoldingThem() {
    assertEquals("https://xn--strae-oqa.de/cdn-cgi/trace", trace("https://straße.de/x"));
    assertEquals("https://xn--fa-hia.de/cdn-cgi/trace", trace("https://faß.de/x"));
    assertEquals("https://xn--zca.example/cdn-cgi/trace", trace("https://ß.example/x"));
    assertEquals("https://xn--wxaikc6b.gr/cdn-cgi/trace", trace("https://ΣΌΛΟΣ.GR/x"));
    assertEquals("https://xn--strae-oqa.de/cdn-cgi/trace", trace("https://xn--strae-oqa.de/x"));
  }

  @Test
  @DisplayName("rejects a joiner used outside a valid context but accepts a valid one")
  void rejectsAJoinerUsedOutsideAValidContextButAcceptsAValidOne() {
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a\u200db.example/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a\u200cb.example/x"));

    // The same joiner is legitimate in Persian, where it follows a right-joining letter.
    assertEquals(
        "https://xn--mgbn2ecje63gr19l.ir/cdn-cgi/trace",
        trace("https://\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645.ir/x"));
  }

  @Test
  @DisplayName("accepts a right-to-left label beside an ASCII or numeric one")
  void acceptsARightToLeftLabelBesideAnAsciiOrNumericOne() {
    // The Bidi rule would reject these; the browser does not apply it, so neither does this.
    assertEquals(
        "https://xn--mgbn2ecje63gr19l.127.de/cdn-cgi/trace",
        trace("https://\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645.127.de/x"));
    assertEquals(
        "https://-ab.xn--mgbn2ecje63gr19l.xn--tda/cdn-cgi/trace",
        trace("https://-ab.\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645.xn--tda/x"));
  }

  @Test
  @DisplayName("passes an all-ASCII host through without validating its ACE labels")
  void passesAnAllAsciiHostThroughWithoutValidatingItsAceLabels() {
    // Load-bearing, not an optimisation: the browser skips conversion for an all-ASCII host, so a
    // malformed ACE label survives. The same label alongside a non-ASCII one is rejected, because
    // that host is converted as a whole.
    assertEquals("https://xn--zz.example/cdn-cgi/trace", trace("https://xn--zz.example/x"));
    assertEquals("https://xn--a.example/cdn-cgi/trace", trace("https://XN--A.example/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://xn--zz.straße.de/x"));
  }

  @Test
  @DisplayName("exposes the parsed components")
  void exposesTheParsedComponents() {
    JsUrl url = JsUrl.parse("HTTPS://example.com:8443/a/b?x=1#frag");

    assertEquals("https", url.getProtocol());
    assertEquals("example.com:8443", url.getAuthority());
    assertEquals("/a/b", url.getPathname());
    assertEquals("?x=1", url.getSearch());
    assertEquals("#frag", url.getHash());
    assertEquals("HTTPS://example.com:8443/a/b?x=1#frag".replace("HTTPS", "https"), url.toString());
  }

  @Test
  @DisplayName("adds the leading slash a bare pathname omits")
  void addsTheLeadingSlashABarePathnameOmits() {
    JsUrl url = JsUrl.parse("https://example.com/a");
    url.setPathname("cdn-cgi/trace");

    assertEquals("/cdn-cgi/trace", url.getPathname());
    assertEquals("https://example.com/cdn-cgi/trace", url.toString());
  }

  @Test
  @DisplayName("rejects anything that is not an absolute hierarchical URL")
  void rejectsAnythingThatIsNotAnAbsoluteHierarchicalUrl() {
    assertThrows(JsUrl.InvalidUrlException.class, () -> JsUrl.parse(null));
    assertThrows(JsUrl.InvalidUrlException.class, () -> JsUrl.parse("/just/a/path"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> JsUrl.parse("mailto:someone@example.com"));
  }

  @Test
  @DisplayName("rewrites a host whose last label is a number into dotted-decimal form")
  void rewritesAHostWhoseLastLabelIsANumberIntoDottedDecimalForm() {
    assertEquals("https://127.0.0.1/cdn-cgi/trace", trace("https://0x7f.1/x"));
    assertEquals("https://127.0.0.1/cdn-cgi/trace", trace("https://127.1/x"));
    assertEquals("https://127.0.0.1/cdn-cgi/trace", trace("https://0177.0.0.1/x"));
    assertEquals("https://127.0.0.1/cdn-cgi/trace", trace("https://127.0.0.0x1/x"));
    assertEquals("https://0.0.0.1/cdn-cgi/trace", trace("https://1/x"));
    assertEquals("https://255.255.255.255/cdn-cgi/trace", trace("https://0xffffffff/x"));
    assertEquals("https://1.2.3.4/cdn-cgi/trace", trace("https://1.2.3.04/x"));

    // Only the LAST label decides. "1.2.3.4.com" ends in a name, so it stays a domain.
    assertEquals("https://1.2.3.4.com/cdn-cgi/trace", trace("https://1.2.3.4.com/x"));
  }

  @Test
  @DisplayName("rejects a numeric host that is out of range or malformed")
  void rejectsANumericHostThatIsOutOfRangeOrMalformed() {
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://256.256.256.256/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://1.2.3.4.5/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://4294967296/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://999999999999/x"));
    // A part too large to be an address is still a number, so this is a broken IPv4 host and
    // not a domain name.
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://0x100000000/x"));
    // The last label is a number, so every earlier label must be one too.
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a.1/x"));
  }

  @Test
  @DisplayName("applies the Bidi rule per label, not across the whole name")
  void appliesTheBidiRulePerLabelNotAcrossTheWholeName() {
    // A right-to-left label may not begin with a European or Arabic-Indic digit.
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://1\u05e2\u05d1\u05e8\u05d9\u05ea.com/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://\u0663\u0639\u0631\u0628\u064a.com/x"));

    // Ending with one is fine, and a plain numeric label alongside an RTL one is not the RTL
    // label's problem.
    assertEquals("https://xn--1-1hcy8a5an.com/cdn-cgi/trace",
        trace("https://\u05e2\u05d1\u05e8\u05d9\u05ea1.com/x"));
    assertEquals("https://127.xn--5dbqzzl.com/cdn-cgi/trace",
        trace("https://127.\u05e2\u05d1\u05e8\u05d9\u05ea.com/x"));
    assertEquals("https://xn--ngbrx4e4f.com/cdn-cgi/trace",
        trace("https://\u0639\u0631\u0628\u064a\u0663.com/x"));
  }

  @Test
  @DisplayName("rejects a host containing a forbidden code point")
  void rejectsAHostContainingAForbiddenCodePoint() {
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a b.com/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a<b.com/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a>b.com/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a^b.com/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a|b.com/x"));
    // Percent-encoded, so it only appears once the host has been decoded.
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a%00b.com/x"));
  }

  @Test
  @DisplayName("discards tab and newline anywhere in the input")
  void discardsTabAndNewlineAnywhereInTheInput() {
    assertEquals("https://ab.com/cdn-cgi/trace", trace("https://a\tb.com/x"));
    assertEquals("https://ab.com/cdn-cgi/trace", trace("https://a\nb.com/x"));
    assertEquals("https://ab.com/cdn-cgi/trace", trace("https://a\rb.com/x"));
  }

  @Test
  @DisplayName("treats a backslash as the end of the authority")
  void treatsABackslashAsTheEndOfTheAuthority() {
    assertEquals("https://a/cdn-cgi/trace", trace("https://a\\b.com/x"));
    assertEquals("https://a/cdn-cgi/trace?q=1", trace("https://a\\b.com?q=1"));
  }

  @Test
  @DisplayName("canonicalises an IPv6 literal and rejects a malformed one")
  void canonicalisesAnIpv6LiteralAndRejectsAMalformedOne() {
    assertEquals("https://[2001:db8::1]/cdn-cgi/trace", trace("https://[2001:DB8::1]/x"));
    // An embedded dotted-quad collapses into the two pieces it represents.
    assertEquals("https://[::ffff:7f00:1]/cdn-cgi/trace", trace("https://[::FFFF:127.0.0.1]/x"));
    assertEquals("https://[::1]:8443/cdn-cgi/trace", trace("https://[::1]:8443/x"));

    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://[]/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://[1/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://1]/x"));
  }

  @Test
  @DisplayName("requires the port to be a number that fits in 16 bits")
  void requiresThePortToBeANumberThatFitsIn16Bits() {
    assertEquals("https://example.com:80/cdn-cgi/trace", trace("https://example.com:080/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://a:b.com/x"));
    assertThrows(JsUrl.InvalidUrlException.class, () -> trace("https://example.com:65536/x"));
  }

  @Test
  @DisplayName("skips run-on slashes after a special scheme")
  void skipsRunOnSlashesAfterASpecialScheme() {
    assertEquals("https://x/cdn-cgi/trace", trace("https:///x"));
    assertEquals("https://example.com/cdn-cgi/trace", trace("https:////example.com/a"));
  }
}
