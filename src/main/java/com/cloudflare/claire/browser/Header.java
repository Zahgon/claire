package com.cloudflare.claire.browser;

/**
 * One entry of {@code details.responseHeaders}.
 *
 * <p>{@code value} is nullable because the WebRequest API omits it for headers delivered as
 * binary, and {@code Request.processRailgunHeader} has an explicit branch for that case.
 */
public record Header(String name, String value) {

  public Header {
    if (name == null) {
      throw new IllegalArgumentException("header name is required");
    }
  }

  /** A header whose value the browser did not supply as text. */
  public static Header withoutValue(String name) {
    return new Header(name, null);
  }
}
