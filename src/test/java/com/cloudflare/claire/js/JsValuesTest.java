package com.cloudflare.claire.js;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the JavaScript coercion rules the migration depends on.
 *
 * <p>Every expectation here was read off the Node runtime the source targets, not inferred, so a
 * failure means Java has drifted from JavaScript rather than that a rule was mis-stated.
 */
class JsValuesTest {

  @Nested
  @DisplayName("truthiness")
  class Truthiness {

    @Test
    @DisplayName("treats the seven falsy values as false")
    void treatsTheSevenFalsyValuesAsFalse() {
      assertFalse(JsValues.isTruthy(null));
      assertFalse(JsValues.isTruthy(false));
      assertFalse(JsValues.isTruthy(""));
      assertFalse(JsValues.isTruthy(0));
      assertFalse(JsValues.isTruthy(0.0d));
      assertFalse(JsValues.isTruthy(-0.0d));
      assertFalse(JsValues.isTruthy(Double.NaN));
      assertFalse(JsValues.isTruthy(0.0f));
      assertFalse(JsValues.isTruthy(Float.NaN));
      assertFalse(JsValues.isTruthy('\0'));
    }

    @Test
    @DisplayName("treats whitespace, empty containers and objects as true")
    void treatsWhitespaceEmptyContainersAndObjectsAsTrue() {
      assertTrue(JsValues.isTruthy(" "));
      assertTrue(JsValues.isTruthy(List.of()));
      assertTrue(JsValues.isTruthy(Map.of()));
      assertTrue(JsValues.isTruthy(new Object()));
      assertTrue(JsValues.isTruthy(true));
      assertTrue(JsValues.isTruthy(1));
      assertTrue(JsValues.isTruthy(-1.5d));
      assertTrue(JsValues.isTruthy(1.0f));
      assertTrue(JsValues.isTruthy('a'));
      assertTrue(JsValues.isTruthy(java.math.BigInteger.ONE));
    }
  }

  @Nested
  @DisplayName("parseInt with radix 10")
  class ParseIntRadix10 {

    @Test
    @DisplayName("yields NaN where JavaScript yields NaN")
    void yieldsNanWhereJavaScriptYieldsNan() {
      assertTrue(Double.isNaN(JsValues.parseIntRadix10(null)));
      assertTrue(Double.isNaN(JsValues.parseIntRadix10("")));
      assertTrue(Double.isNaN(JsValues.parseIntRadix10("foobar")));
      assertTrue(Double.isNaN(JsValues.parseIntRadix10("  ")));
      assertTrue(Double.isNaN(JsValues.parseIntRadix10("+")));
    }

    @Test
    @DisplayName("consumes the leading digit run and ignores the rest")
    void consumesTheLeadingDigitRunAndIgnoresTheRest() {
      assertEquals(96.0d, JsValues.parseIntRadix10("96"));
      assertEquals(96.0d, JsValues.parseIntRadix10("  96  "));
      assertEquals(12.0d, JsValues.parseIntRadix10("12abc"));
      assertEquals(-7.0d, JsValues.parseIntRadix10("-7"));
      assertEquals(7.0d, JsValues.parseIntRadix10("+7"));
      assertEquals(96.0d, JsValues.parseIntRadix10("96.9"));
    }
  }

  @Nested
  @DisplayName("ToNumber")
  class ToNumber {

    @Test
    @DisplayName("reads decimal, radix and Infinity literals")
    void readsDecimalRadixAndInfinityLiterals() {
      assertEquals(46.0d, JsValues.toNumber("46"));
      assertEquals(46.5d, JsValues.toNumber(" 46.5 "));
      assertEquals(0.5d, JsValues.toNumber(".5"));
      assertEquals(1500.0d, JsValues.toNumber("1.5e3"));
      assertEquals(255.0d, JsValues.toNumber("0xFF"));
      assertEquals(8.0d, JsValues.toNumber("0o10"));
      assertEquals(5.0d, JsValues.toNumber("0b101"));
      assertEquals(Double.POSITIVE_INFINITY, JsValues.toNumber("Infinity"));
      assertEquals(Double.NEGATIVE_INFINITY, JsValues.toNumber("-Infinity"));
    }

    @Test
    @DisplayName("treats an absent value as NaN and a blank one as zero")
    void treatsAnAbsentValueAsNanAndABlankOneAsZero() {
      assertTrue(Double.isNaN(JsValues.toNumber(null)));
      assertTrue(Double.isNaN(JsValues.toNumber("foobar")));
      assertTrue(Double.isNaN(JsValues.toNumber("0xZZ")));
      assertEquals(0.0d, JsValues.toNumber(""));
      assertEquals(0.0d, JsValues.toNumber("   "));
    }

    @Test
    @DisplayName("rejects the numeric literals Java accepts but JavaScript does not")
    void rejectsTheNumericLiteralsJavaAcceptsButJavaScriptDoesNot() {
      assertTrue(Double.isNaN(JsValues.toNumber("1d")));
      assertTrue(Double.isNaN(JsValues.toNumber("1f")));
      assertTrue(Double.isNaN(JsValues.toNumber("0x1p3")));
    }
  }

  @Nested
  @DisplayName("Number to string")
  class NumberToString {

    @Test
    @DisplayName("drops the decimal point on integral values")
    void dropsTheDecimalPointOnIntegralValues() {
      assertEquals("54", JsValues.numberToString(54.0d));
      assertEquals("0", JsValues.numberToString(0.0d));
      assertEquals("0", JsValues.numberToString(-0.0d));
      assertEquals("-4", JsValues.numberToString(-4.0d));
      assertEquals("53.5", JsValues.numberToString(53.5d));
    }

    @Test
    @DisplayName("spells out the non-finite values")
    void spellsOutTheNonFiniteValues() {
      assertEquals("NaN", JsValues.numberToString(Double.NaN));
      assertEquals("Infinity", JsValues.numberToString(Double.POSITIVE_INFINITY));
      assertEquals("-Infinity", JsValues.numberToString(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("switches to exponential notation outside the plain range")
    void switchesToExponentialNotationOutsideThePlainRange() {
      assertEquals("0.000001", JsValues.numberToString(0.000001d));
      assertEquals("1e-7", JsValues.numberToString(0.0000001d));
      assertEquals("1e+21", JsValues.numberToString(1.0e21d));
      assertEquals("-1e-7", JsValues.numberToString(-0.0000001d));
    }
  }

  @Nested
  @DisplayName("ToInt32")
  class ToInt32 {

    @Test
    @DisplayName("turns non-finite operands into zero so masking still works")
    void turnsNonFiniteOperandsIntoZeroSoMaskingStillWorks() {
      assertEquals(0, JsValues.toInt32(Double.NaN));
      assertEquals(0, JsValues.toInt32(Double.POSITIVE_INFINITY));
      assertEquals(0, JsValues.toInt32(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("truncates toward zero and wraps at 32 bits")
    void truncatesTowardZeroAndWrapsAt32Bits() {
      assertEquals(96, JsValues.toInt32(96.0d));
      assertEquals(96, JsValues.toInt32(96.9d));
      assertEquals(-96, JsValues.toInt32(-96.9d));
      assertEquals(1, JsValues.toInt32(4294967297.0d));
      assertEquals(-1, JsValues.toInt32(-1.0d));
    }
  }

  @Nested
  @DisplayName("string and array helpers")
  class StringAndArrayHelpers {

    @Test
    @DisplayName("renders an absent operand as the word undefined")
    void rendersAnAbsentOperandAsTheWordUndefined() {
      assertEquals("undefinedsec", JsValues.concat(null, "sec"));
      assertEquals("700sec", JsValues.concat("700", "sec"));
      assertEquals("undefined", JsValues.orUndefined(null));
      assertEquals("x", JsValues.orUndefined("x"));
    }

    @Test
    @DisplayName("renders an absent operand as empty for nullable DOM attributes")
    void rendersAnAbsentOperandAsEmptyForNullableDomAttributes() {
      assertEquals("", JsValues.orEmpty(null));
      assertEquals("x", JsValues.orEmpty("x"));
    }

    @Test
    @DisplayName("returns undefined for an out-of-range index instead of throwing")
    void returnsUndefinedForAnOutOfRangeIndexInsteadOfThrowing() {
      String[] parts = {"a", "b"};
      assertEquals("a", JsValues.at(parts, 0));
      assertEquals("b", JsValues.at(parts, 1));
      assertNull(JsValues.at(parts, 2));
      assertNull(JsValues.at(parts, -1));
      assertNull(JsValues.at(null, 0));
    }

    @Test
    @DisplayName("keeps trailing empty strings when splitting")
    void keepsTrailingEmptyStringsWhenSplitting() {
      assertArrayEquals(new String[] {"a", ""}, JsValues.split("a ", " "));
      assertArrayEquals(new String[] {"", "a"}, JsValues.split(" a", " "));
      assertArrayEquals(new String[] {"deadbeef", "SFO"}, JsValues.split("deadbeef-SFO", "-"));
      assertArrayEquals(new String[] {"deadbeef"}, JsValues.split("deadbeef", "-"));
    }

    @Test
    @DisplayName("upper-cases without consulting the default locale")
    void upperCasesWithoutConsultingTheDefaultLocale() {
      Locale original = Locale.getDefault();
      try {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertEquals("CF-RAILGUN", JsValues.toUpperCase("cf-railgun"));
      } finally {
        Locale.setDefault(original);
      }
    }
  }
}
