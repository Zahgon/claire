package com.cloudflare.claire.browser;

/**
 * A message exchanged between the background page and the content script.
 *
 * <p>The source builds {@code {action: 'check_connection_info'}} inline; the action name is
 * pinned here so both ends of the conversation refer to the same constant.
 */
public record ContentScriptMessage(String action) {

  /** {@code {action: 'check_connection_info'}}. */
  public static final String CHECK_CONNECTION_INFO = "check_connection_info";

  /** The request the background page sends to ask a tab how it connected. */
  public static ContentScriptMessage checkConnectionInfo() {
    return new ContentScriptMessage(CHECK_CONNECTION_INFO);
  }
}
