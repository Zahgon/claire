package com.cloudflare.claire.js;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

/**
 * JavaScript value semantics that Java does not provide.
 *
 * <p>Every one of these exists because a line of the original source depends on a
 * JavaScript coercion rule with no Java analogue. They are collected here rather than
 * inlined so that the rule is stated once and pinned by its own tests.
 *
 * <p>Mapping back to the source:
 * <ul>
 *   <li>{@link #isTruthy} &mdash; {@code if (ray)}, {@code details.ip ? details.ip : ''}</li>
 *   <li>{@link #parseIntRadix10} &mdash; {@code parseInt(parts[1], 10)}</li>
 *   <li>{@link #toNumber} / {@link #numberToString} &mdash; {@code (100 - parts[1]) + '%'}</li>
 *   <li>{@link #toInt32} &mdash; {@code flagsBitset & flag.position}</li>
 *   <li>{@link #concat} &mdash; {@code parts[2] + 'sec'} where {@code parts[2]} may be undefined</li>
 *   <li>{@link #at} &mdash; out-of-range array indexing yields {@code undefined}, never an error</li>
 *   <li>{@link #split} &mdash; {@code String.prototype.split} keeps trailing empty strings</li>
 *   <li>{@link #toUpperCase} &mdash; {@code String.prototype.toUpperCase} is locale-independent</li>
 * </ul>
 */
public final class JsValues {

  private JsValues() {
  }

  /**
   * The JavaScript falsy set is exactly {@code false, 0, -0, 0n, "", null, undefined, NaN}.
   * Everything else &mdash; including {@code " "}, {@code {}} and {@code []} &mdash; is truthy.
   *
   * <p>Never substitute {@code != null} for this: the source relies on {@code ''} and {@code 0}
   * being falsy (an empty {@code CF-RAY} header must behave as an absent one).
   */
  public static boolean isTruthy(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean b -> b;
      case String s -> !s.isEmpty();
      case Double d -> !(d == 0.0d || Double.isNaN(d));
      case Float f -> !(f == 0.0f || Float.isNaN(f));
      case Character c -> c != '\0';
      case Number n -> n.doubleValue() != 0.0d;
      default -> true;
    };
  }

  /**
   * {@code parseInt(value, 10)}: skip leading whitespace, accept an optional sign, then consume
   * the longest run of decimal digits. Returns {@code NaN} when no digit is available, which is
   * what {@code parseInt(undefined, 10)} produces.
   *
   * <p>Deliberately a {@code double}, not an {@code int}: {@code Integer.parseInt} throws where
   * JavaScript yields {@code NaN}, and the {@code NaN} is observable downstream.
   */
  public static double parseIntRadix10(String value) {
    if (value == null) {
      return Double.NaN;
    }
    int i = 0;
    int n = value.length();
    while (i < n && isJsWhitespace(value.charAt(i))) {
      i++;
    }
    boolean negative = false;
    if (i < n && (value.charAt(i) == '+' || value.charAt(i) == '-')) {
      negative = value.charAt(i) == '-';
      i++;
    }
    int digitsStart = i;
    while (i < n && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
      i++;
    }
    if (i == digitsStart) {
      return Double.NaN;
    }
    double magnitude = new BigDecimal(value.substring(digitsStart, i)).doubleValue();
    return negative ? -magnitude : magnitude;
  }

  /**
   * {@code ToNumber} for the values this code base can produce: {@code undefined} yields
   * {@code NaN}, a blank string yields {@code 0}, otherwise the trimmed string is read as a
   * JavaScript numeric literal (decimal, hex, octal, binary or {@code Infinity}).
   */
  public static double toNumber(String value) {
    if (value == null) {
      return Double.NaN;
    }
    String trimmed = trimJsWhitespace(value);
    if (trimmed.isEmpty()) {
      return 0.0d;
    }
    Double radixed = parseRadixLiteral(trimmed);
    if (radixed != null) {
      return radixed;
    }
    String unsigned = trimmed;
    double sign = 1.0d;
    if (unsigned.startsWith("+") || unsigned.startsWith("-")) {
      sign = unsigned.charAt(0) == '-' ? -1.0d : 1.0d;
      unsigned = unsigned.substring(1);
    }
    if ("Infinity".equals(unsigned)) {
      return sign * Double.POSITIVE_INFINITY;
    }
    if (!isDecimalLiteral(unsigned)) {
      return Double.NaN;
    }
    return sign * Double.parseDouble(unsigned);
  }

  /**
   * {@code Number.prototype.toString()} with no radix: no trailing {@code .0}, {@code NaN} and
   * {@code Infinity} spelled out, and plain (non-exponential) notation for
   * {@code 1e-6 <= |x| < 1e21}.
   */
  public static String numberToString(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (value == Double.POSITIVE_INFINITY) {
      return "Infinity";
    }
    if (value == Double.NEGATIVE_INFINITY) {
      return "-Infinity";
    }
    if (value == 0.0d) {
      return "0";
    }
    double magnitude = Math.abs(value);
    BigDecimal shortest = new BigDecimal(Double.toString(value));
    if (magnitude >= 1.0e-6d && magnitude < 1.0e21d) {
      if (value == Math.rint(value)) {
        return shortest.toBigInteger().toString();
      }
      return shortest.stripTrailingZeros().toPlainString();
    }
    return exponentialForm(shortest.stripTrailingZeros());
  }

  /** {@code ToInt32}: the coercion the {@code &} operator applies to its operands. */
  public static int toInt32(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0;
    }
    double truncated = value < 0 ? Math.ceil(value) : Math.floor(value);
    double wrapped = truncated % 4294967296.0d;
    if (wrapped < 0) {
      wrapped += 4294967296.0d;
    }
    long unsigned = (long) wrapped;
    return (int) unsigned;
  }

  /**
   * String concatenation with an operand that may be {@code undefined}. JavaScript renders the
   * absent value as the literal text {@code "undefined"} rather than failing, which is how
   * {@code 'foobar 46'} produces a Railgun time of {@code "undefinedsec"}.
   */
  public static String concat(String left, String right) {
    return orUndefined(left) + orUndefined(right);
  }

  /**
   * Renders a possibly-absent string the way JavaScript's string conversion does.
   *
   * <p>This is also the rule for assigning to a non-nullable DOM {@code DOMString} attribute such
   * as {@code input.value} or {@code a.href}: {@code el.value = undefined} leaves the literal
   * text {@code "undefined"} on screen.
   */
  public static String orUndefined(String value) {
    return value == null ? "undefined" : value;
  }

  /**
   * The conversion a <em>nullable</em> DOM {@code DOMString?} attribute applies, which is a
   * different rule from {@link #orUndefined}: {@code el.textContent = undefined} clears the
   * element instead of writing the word "undefined" into it.
   */
  public static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  /** Array indexing: out of range is {@code undefined}, not an exception. */
  public static String at(String[] values, int index) {
    if (values == null || index < 0 || index >= values.length) {
      return null;
    }
    return values[index];
  }

  /**
   * {@code String.prototype.split}: unlike Java's one-argument {@code split}, JavaScript keeps
   * trailing empty strings, so the limit must be negative.
   */
  public static String[] split(String value, String separator) {
    return value.split(java.util.regex.Pattern.quote(separator), -1);
  }

  /**
   * {@code String.prototype.toUpperCase} is defined by Unicode Default Case Conversion and does
   * not consult a locale. Java's no-argument {@code toUpperCase()} uses the default locale, which
   * maps {@code i} to {@code İ} under {@code tr-TR} and would corrupt header names.
   */
  public static String toUpperCase(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  private static boolean isJsWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\u000B'
        || c == '\u00A0' || c == '\uFEFF' || Character.getType(c) == Character.SPACE_SEPARATOR;
  }

  private static String trimJsWhitespace(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isJsWhitespace(value.charAt(start))) {
      start++;
    }
    while (end > start && isJsWhitespace(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static Double parseRadixLiteral(String trimmed) {
    if (trimmed.length() < 3 || trimmed.charAt(0) != '0') {
      return null;
    }
    int radix = switch (Character.toLowerCase(trimmed.charAt(1))) {
      case 'x' -> 16;
      case 'o' -> 8;
      case 'b' -> 2;
      default -> 0;
    };
    if (radix == 0) {
      return null;
    }
    try {
      return new BigInteger(trimmed.substring(2), radix).doubleValue();
    } catch (NumberFormatException ignored) {
      return Double.NaN;
    }
  }

  private static boolean isDecimalLiteral(String text) {
    if (text.isEmpty()) {
      return false;
    }
    // Java accepts trailing type suffixes ("1d", "0xAp0") and leading whitespace that
    // JavaScript rejects, so the literal is validated before Double.parseDouble sees it.
    return text.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
  }

  private static String exponentialForm(BigDecimal value) {
    BigDecimal magnitude = value.abs();
    int exponent = magnitude.precision() - magnitude.scale() - 1;
    BigDecimal mantissa = value.movePointLeft(exponent).stripTrailingZeros();
    String digits = mantissa.toPlainString();
    return digits + "e" + (exponent < 0 ? "-" : "+") + Math.abs(exponent);
  }
}
