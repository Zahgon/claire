package com.cloudflare.claire.js;

import com.ibm.icu.text.IDNA;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The slice of the WHATWG {@code URL} object that {@code Request.getCloudFlareTrace} uses:
 * parse an absolute URL, replace its {@code pathname}, serialise it again.
 *
 * <p>{@code java.net.URI} is not a substitute. It re-encodes components, refuses some URLs that
 * browsers accept, and keeps default ports that {@code URL} drops &mdash; all of which are
 * externally observable in the trace link the popup renders.
 */
public final class JsUrl {

  private static final Pattern ABSOLUTE_URL = Pattern.compile(
      "^([a-zA-Z][a-zA-Z0-9+.\\-]*):(//)?([^/?#]*)([^?#]*)(\\?[^#]*)?(#.*)?$");

  /**
   * Host conversion configured the way the browser configures it: UTS-46, non-transitionally, so
   * the deviation characters {@code ß} and {@code ς} are preserved rather than folded to
   * {@code ss} and {@code σ}, with joiner context rules enforced.
   *
   * <p>The Bidi rule is enabled but is applied PER LABEL, which is what the URL parser does.
   * Evaluated across a whole name, RFC 5893 also rejects an ordinary numeric label such as the
   * {@code 127} in {@code میخواهم.127.de} once any other label is right-to-left; the browser
   * accepts that. It rejects only a label that breaks the rule on its own, so {@code 1עברית.com}
   * and {@code ٣عربي.com} are refused while {@code עברית1.com} and {@code عربي٣.com} are not.
   */
  private static final IDNA UTS46 = IDNA.getUTS46Instance(
      IDNA.NONTRANSITIONAL_TO_ASCII | IDNA.CHECK_CONTEXTJ | IDNA.CHECK_BIDI);

  /**
   * Conversion complaints the URL parser does not treat as fatal, because it runs the conversion
   * with DNS length verification and hyphen placement checks disabled.
   */
  private static final Set<IDNA.Error> IGNORED_HOST_ERRORS = Collections.unmodifiableSet(
      EnumSet.of(
          IDNA.Error.EMPTY_LABEL,
          IDNA.Error.LABEL_TOO_LONG,
          IDNA.Error.DOMAIN_NAME_TOO_LONG,
          IDNA.Error.LEADING_HYPHEN,
          IDNA.Error.TRAILING_HYPHEN,
          IDNA.Error.HYPHEN_3_4,
          // Only ever fatal per label; see the note on UTS46 and rejectPerLabelBidi below.
          IDNA.Error.BIDI));

  /** Ports the URL serialiser omits because they are the scheme's default. */
  private static final Map<String, String> DEFAULT_PORTS = Map.of(
      "http", "80",
      "https", "443",
      "ws", "80",
      "wss", "443",
      "ftp", "21");

  private final String protocol;
  private final String authority;
  private String pathname;
  private final String search;
  private final String hash;

  private JsUrl(String protocol, String authority, String pathname, String search, String hash) {
    this.protocol = protocol;
    this.authority = authority;
    this.pathname = pathname;
    this.search = search;
    this.hash = hash;
  }

  /**
   * {@code new URL(input)}.
   *
   * @throws InvalidUrlException when the input is not an absolute URL. JavaScript throws a
   *     {@code TypeError} here; Java has no such type, so the failure keeps its own name.
   */
  public static JsUrl parse(String input) {
    if (input == null) {
      throw new InvalidUrlException("undefined");
    }
    Matcher matcher = ABSOLUTE_URL.matcher(collapseAuthoritySlashes(stripIgnored(input)));
    if (!matcher.matches()) {
      throw new InvalidUrlException(input);
    }
    String protocol = matcher.group(1).toLowerCase(Locale.ROOT);
    boolean hierarchical = matcher.group(2) != null;
    if (!hierarchical) {
      throw new InvalidUrlException(input);
    }
    String authority = normaliseAuthority(protocol, matcher.group(3));
    String path = matcher.group(4);
    if (path.isEmpty()) {
      path = "/";
    }
    return new JsUrl(
        protocol,
        authority,
        path,
        matcher.group(5) == null ? "" : matcher.group(5),
        matcher.group(6) == null ? "" : matcher.group(6));
  }

  /** {@code url.pathname = value}. */
  public void setPathname(String value) {
    this.pathname = value.startsWith("/") ? value : "/" + value;
  }

  /** {@code url.pathname}. */
  public String getPathname() {
    return pathname;
  }

  /** {@code url.protocol}, without the trailing colon. */
  public String getProtocol() {
    return protocol;
  }

  /** {@code url.host}: hostname plus a non-default port, and any userinfo the input carried. */
  public String getAuthority() {
    return authority;
  }

  /** {@code url.search}, including the leading {@code ?} when non-empty. */
  public String getSearch() {
    return search;
  }

  /** {@code url.hash}, including the leading {@code #} when non-empty. */
  public String getHash() {
    return hash;
  }

  /** {@code url.toString()} / {@code url.href}. */
  @Override
  public String toString() {
    return protocol + "://" + authority + pathname + search + hash;
  }

  /**
   * Splits the authority into userinfo, host and port, normalises the host, and drops the port
   * when it is the scheme's default.
   *
   * <p>Only the host is normalised. Userinfo keeps its case, because the URL parser treats it as
   * opaque credentials rather than as a name to be canonicalised.
   */
  private static String normaliseAuthority(String protocol, String authority) {
    // For a special scheme a backslash terminates the authority exactly as a slash does, so
    // everything from the first one onwards belongs to the path.
    String bounded = authority;
    if (DEFAULT_PORTS.containsKey(protocol)) {
      int backslash = bounded.indexOf('\\');
      if (backslash >= 0) {
        bounded = bounded.substring(0, backslash);
      }
    }

    int hostStart = bounded.lastIndexOf('@') + 1;
    String userinfo = bounded.substring(0, hostStart);
    String hostAndPort = bounded.substring(hostStart);

    String host = hostAndPort;
    String port = "";
    int colon = hostAndPort.lastIndexOf(':');
    // A colon inside an IPv6 literal is part of the address, not a port separator.
    if (colon >= 0 && hostAndPort.indexOf(']') < colon) {
      host = hostAndPort.substring(0, colon);
      port = hostAndPort.substring(colon + 1);
    }

    host = normaliseHost(host);
    port = normalisePort(port, authority);

    if (port.isEmpty() || port.equals(DEFAULT_PORTS.get(protocol))) {
      return userinfo + host;
    }
    return userinfo + host + ":" + port;
  }

  /**
   * Host canonicalisation, which the URL parser performs and a naive copy of the authority does
   * not: the host is case-folded, and an internationalised host is converted to its ASCII
   * (punycode) form. Both are externally observable — the result is written into a link.
   *
   * <p>Case-folding pins {@link Locale#ROOT}: under a Turkish default locale the ASCII
   * {@code I} would fold to a dotless {@code ı} and change the host.
   *
   * <p>An IPv6 literal is bracketed and is not a domain name, so it is only case-folded.
   */
  private static String normaliseHost(String host) {
    if (host.startsWith("[") || host.endsWith("]")) {
      if (!host.startsWith("[") || !host.endsWith("]") || host.length() < 3) {
        throw new InvalidUrlException(host);
      }
      return "[" + serialiseIpv6(parseIpv6(host.substring(1, host.length() - 1), host)) + "]";
    }
    if (host.isEmpty()) {
      return "";
    }
    String decoded = percentDecode(host);
    rejectForbiddenHostCodePoints(decoded, host);

    String ascii;
    // An all-ASCII host is case-folded and otherwise passed through, without being validated.
    // That is the browser's behaviour and it is observable: a malformed ACE label such as
    // "xn--zz" survives here, while the same label inside a host that also carries a non-ASCII
    // label is rejected, because that host takes the conversion path below.
    if (isAscii(decoded)) {
      ascii = decoded.toLowerCase(Locale.ROOT);
    } else {
      StringBuilder converted = new StringBuilder();
      IDNA.Info info = new IDNA.Info();
      UTS46.nameToASCII(decoded, converted, info);
      for (IDNA.Error error : info.getErrors()) {
        if (!IGNORED_HOST_ERRORS.contains(error)) {
          throw new InvalidUrlException(host);
        }
      }
      if (info.getErrors().contains(IDNA.Error.BIDI)) {
        rejectPerLabelBidi(decoded, host);
      }
      ascii = converted.toString().toLowerCase(Locale.ROOT);
    }

    // A host whose final label parses as a number is an IPv4 address, not a domain, and is
    // rewritten to dotted-decimal form. This is why "0x7f.1" is a link to 127.0.0.1 and why
    // "a.1" is not a URL at all.
    return endsInNumber(ascii) ? serialiseIpv4(parseIpv4(ascii, host)) : ascii;
  }

  /**
   * Code points a host may never contain. Tab, newline and carriage return are already gone by
   * this point; the rest can still arrive percent-encoded, which is why the check runs after
   * decoding rather than before.
   */
  private static final String FORBIDDEN_HOST_CODE_POINTS =
      "\u0000\t\n\r #/:<>?@[\\]^|";

  /**
   * A port is decimal digits and nothing else, and must fit in 16 bits. Leading zeroes are
   * dropped, so {@code :080} and {@code :80} are the same port and both vanish on http.
   */
  private static String normalisePort(String port, String authority) {
    if (port.isEmpty()) {
      return "";
    }
    long value = 0;
    for (int i = 0; i < port.length(); i++) {
      char c = port.charAt(i);
      if (c < '0' || c > '9') {
        throw new InvalidUrlException(authority);
      }
      value = Math.min(value * 10 + (c - '0'), 1L << 20);
    }
    if (value > 65535) {
      throw new InvalidUrlException(authority);
    }
    return String.valueOf(value);
  }

  /**
   * Re-runs the Bidi check one label at a time and fails only if a label breaks the rule by
   * itself. Across a whole name RFC 5893 condemns every label once any of them is right-to-left,
   * including plain numeric ones the browser is happy with.
   */
  private static void rejectPerLabelBidi(String decoded, String original) {
    for (String label : decoded.split("\\.", -1)) {
      if (label.isEmpty()) {
        continue;
      }
      IDNA.Info labelInfo = new IDNA.Info();
      UTS46.nameToASCII(label, new StringBuilder(), labelInfo);
      if (labelInfo.getErrors().contains(IDNA.Error.BIDI)) {
        throw new InvalidUrlException(original);
      }
    }
  }

  private static void rejectForbiddenHostCodePoints(String decoded, String original) {
    for (int i = 0; i < decoded.length(); i++) {
      if (FORBIDDEN_HOST_CODE_POINTS.indexOf(decoded.charAt(i)) >= 0) {
        throw new InvalidUrlException(original);
      }
    }
  }

  /**
   * Run-on slashes after a special scheme are skipped rather than treated as an empty authority,
   * so {@code https:///x} has {@code x} for a host and no path.
   */
  private static final Pattern AUTHORITY_SLASHES =
      Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*):[/\\\\]{2,}");

  private static String collapseAuthoritySlashes(String input) {
    Matcher matcher = AUTHORITY_SLASHES.matcher(input);
    if (!matcher.find()
        || !DEFAULT_PORTS.containsKey(matcher.group(1).toLowerCase(Locale.ROOT))) {
      return input;
    }
    return matcher.group(1) + "://" + input.substring(matcher.end());
  }

  /** Strips the characters the URL parser discards before it looks at the input at all. */
  private static String stripIgnored(String input) {
    int start = 0;
    int end = input.length();
    while (start < end && input.charAt(start) <= ' ') {
      start++;
    }
    while (end > start && input.charAt(end - 1) <= ' ') {
      end--;
    }
    StringBuilder sb = new StringBuilder(end - start);
    for (int i = start; i < end; i++) {
      char c = input.charAt(i);
      if (c != '\t' && c != '\n' && c != '\r') {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Whether the host's last label parses as a number, which is what makes the URL parser treat
   * the whole host as an IPv4 address. A single trailing dot is ignored for the purpose of
   * finding that label.
   */
  private static boolean endsInNumber(String host) {
    String[] parts = host.split("\\.", -1);
    int lastIndex = parts.length - 1;
    if (parts[lastIndex].isEmpty() && parts.length > 1) {
      lastIndex--;
    }
    String last = parts[lastIndex];
    if (last.isEmpty()) {
      return false;
    }
    boolean allDigits = true;
    for (int i = 0; i < last.length(); i++) {
      if (last.charAt(i) < '0' || last.charAt(i) > '9') {
        allDigits = false;
        break;
      }
    }
    return allDigits || parseIpv4Number(last) >= 0;
  }

  /**
   * The URL specification's IPv4 parser, as a 32-bit value.
   *
   * <p>Each part may be decimal, octal with a leading {@code 0}, or hexadecimal with a leading
   * {@code 0x}, and a host with fewer than four parts lets its last part cover the remaining
   * bytes &mdash; which is how {@code 127.1} becomes {@code 127.0.0.1}.
   */
  private static long parseIpv4(String host, String original) {
    String[] split = host.split("\\.", -1);
    int count = split.length;
    if (split[count - 1].isEmpty() && count > 1) {
      count--;
    }
    if (count > 4) {
      throw new InvalidUrlException(original);
    }

    long[] numbers = new long[count];
    for (int i = 0; i < count; i++) {
      long value = parseIpv4Number(split[i]);
      if (value < 0) {
        throw new InvalidUrlException(original);
      }
      numbers[i] = value;
    }

    for (int i = 0; i < count - 1; i++) {
      if (numbers[i] > 255) {
        throw new InvalidUrlException(original);
      }
    }
    long last = numbers[count - 1];
    long ceiling = 1L << (8 * (5 - count));
    if (last >= ceiling) {
      throw new InvalidUrlException(original);
    }

    long address = last;
    for (int i = 0; i < count - 1; i++) {
      address += numbers[i] << (8 * (3 - i));
    }
    return address;
  }

  /** One IPv4 part, or {@code -1} when it is not a number in any accepted radix. */
  private static long parseIpv4Number(String input) {
    if (input.isEmpty()) {
      return -1;
    }
    String digits = input;
    int radix = 10;
    if (digits.length() >= 2 && (digits.startsWith("0x") || digits.startsWith("0X"))) {
      radix = 16;
      digits = digits.substring(2);
    } else if (digits.length() >= 2 && digits.charAt(0) == '0') {
      radix = 8;
      digits = digits.substring(1);
    }
    if (digits.isEmpty()) {
      return 0;
    }
    long value = 0;
    for (int i = 0; i < digits.length(); i++) {
      int digit = Character.digit(digits.charAt(i), radix);
      if (digit < 0) {
        return -1;
      }
      // Saturate rather than fail: a part larger than the address space is still a NUMBER, so the
      // host is still an IPv4 host. It is the range check in parseIpv4 that rejects it, which is
      // why "0x100000000" is an invalid URL and not a domain name.
      if (value <= 0xFFFFFFFFL) {
        value = value * radix + digit;
      }
    }
    return value;
  }

  /**
   * The URL specification's IPv6 parser, as eight 16-bit pieces.
   *
   * <p>Handles {@code ::} compression, which may appear once, and a trailing dotted-quad, which
   * fills the last two pieces &mdash; the form that makes {@code ::ffff:127.0.0.1} the same
   * address as {@code ::ffff:7f00:1}.
   */
  private static int[] parseIpv6(String input, String original) {
    int[] address = new int[8];
    int pieceIndex = 0;
    int compress = -1;
    int pointer = 0;
    int length = input.length();

    if (pointer < length && input.charAt(pointer) == ':') {
      if (pointer + 1 >= length || input.charAt(pointer + 1) != ':') {
        throw new InvalidUrlException(original);
      }
      pointer += 2;
      compress = ++pieceIndex;
    }

    while (pointer < length) {
      if (pieceIndex == 8) {
        throw new InvalidUrlException(original);
      }
      if (input.charAt(pointer) == ':') {
        if (compress >= 0) {
          throw new InvalidUrlException(original);
        }
        pointer++;
        compress = ++pieceIndex;
        continue;
      }

      int value = 0;
      int digits = 0;
      while (digits < 4 && pointer < length
          && Character.digit(input.charAt(pointer), 16) >= 0) {
        value = value * 16 + Character.digit(input.charAt(pointer), 16);
        pointer++;
        digits++;
      }

      if (pointer < length && input.charAt(pointer) == '.') {
        if (digits == 0 || pieceIndex > 6) {
          throw new InvalidUrlException(original);
        }
        pointer -= digits;
        int numbersSeen = 0;
        while (pointer < length) {
          int ipv4Piece = -1;
          if (numbersSeen > 0) {
            if (input.charAt(pointer) == '.' && numbersSeen < 4) {
              pointer++;
            } else {
              throw new InvalidUrlException(original);
            }
          }
          if (pointer >= length || !Character.isDigit(input.charAt(pointer))) {
            throw new InvalidUrlException(original);
          }
          while (pointer < length && Character.isDigit(input.charAt(pointer))) {
            int number = input.charAt(pointer) - '0';
            if (ipv4Piece < 0) {
              ipv4Piece = number;
            } else if (ipv4Piece == 0) {
              throw new InvalidUrlException(original);
            } else {
              ipv4Piece = ipv4Piece * 10 + number;
            }
            if (ipv4Piece > 255) {
              throw new InvalidUrlException(original);
            }
            pointer++;
          }
          address[pieceIndex] = address[pieceIndex] * 0x100 + ipv4Piece;
          numbersSeen++;
          if (numbersSeen == 2 || numbersSeen == 4) {
            pieceIndex++;
          }
        }
        if (numbersSeen != 4) {
          throw new InvalidUrlException(original);
        }
        break;
      }

      if (pointer < length && input.charAt(pointer) == ':') {
        pointer++;
        if (pointer >= length) {
          throw new InvalidUrlException(original);
        }
      } else if (pointer < length) {
        throw new InvalidUrlException(original);
      }
      address[pieceIndex++] = value;
    }

    if (compress >= 0) {
      int swaps = pieceIndex - compress;
      pieceIndex = 7;
      while (pieceIndex != 0 && swaps > 0) {
        int temp = address[pieceIndex];
        address[pieceIndex] = address[compress + swaps - 1];
        address[compress + swaps - 1] = temp;
        pieceIndex--;
        swaps--;
      }
    } else if (pieceIndex != 8) {
      throw new InvalidUrlException(original);
    }
    return address;
  }

  /** Serialises an address, compressing the longest run of two or more zero pieces. */
  private static String serialiseIpv6(int[] address) {
    int compress = -1;
    int longest = 1;
    int runStart = -1;
    int runLength = 0;
    for (int i = 0; i < 8; i++) {
      if (address[i] == 0) {
        if (runStart < 0) {
          runStart = i;
          runLength = 0;
        }
        runLength++;
        if (runLength > longest) {
          longest = runLength;
          compress = runStart;
        }
      } else {
        runStart = -1;
      }
    }

    StringBuilder out = new StringBuilder();
    boolean ignoreZero = false;
    for (int i = 0; i < 8; i++) {
      if (ignoreZero && address[i] == 0) {
        continue;
      }
      ignoreZero = false;
      if (compress == i) {
        out.append(i == 0 ? "::" : ":");
        ignoreZero = true;
        continue;
      }
      out.append(Integer.toHexString(address[i]));
      if (i != 7) {
        out.append(':');
      }
    }
    return out.toString();
  }

  private static String serialiseIpv4(long address) {
    return (address >> 24 & 0xFF) + "." + (address >> 16 & 0xFF) + "."
        + (address >> 8 & 0xFF) + "." + (address & 0xFF);
  }

  private static boolean isAscii(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) > 0x7F) {
        return false;
      }
    }
    return true;
  }

  /**
   * Percent-decoding runs on the host before the ASCII conversion, so an escaped host resolves to
   * the same name as its unescaped spelling. A decoded {@code %2E} therefore separates labels, and
   * an escape that is not two hex digits makes the whole URL invalid.
   *
   * <p>Decoding operates on bytes rather than characters so that a multi-byte UTF-8 sequence
   * split across several escapes reassembles correctly.
   */
  private static String percentDecode(String host) {
    if (host.indexOf('%') < 0) {
      return host;
    }
    byte[] raw = host.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream decoded = new ByteArrayOutputStream(raw.length);
    for (int i = 0; i < raw.length; ) {
      if (raw[i] == '%') {
        if (i + 2 >= raw.length) {
          throw new InvalidUrlException(host);
        }
        int high = Character.digit((char) (raw[i + 1] & 0xFF), 16);
        int low = Character.digit((char) (raw[i + 2] & 0xFF), 16);
        if (high < 0 || low < 0) {
          throw new InvalidUrlException(host);
        }
        decoded.write((high << 4) | low);
        i += 3;
      } else {
        decoded.write(raw[i]);
        i++;
      }
    }
    return decoded.toString(StandardCharsets.UTF_8);
  }

  /** Raised where JavaScript's {@code new URL()} raises a {@code TypeError}. */
  public static final class InvalidUrlException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    InvalidUrlException(String input) {
      super("Invalid URL: " + input);
    }
  }
}
