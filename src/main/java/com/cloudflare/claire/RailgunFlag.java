package com.cloudflare.claire;

/**
 * The bits of the Railgun flags bitset, migrated from the {@code railgunFlags} object literal in
 * {@code request.js}.
 *
 * <p>Declaration order is load-bearing. The source walks the object with {@code for...in}; every
 * key there is a non-numeric string, so the traversal is in insertion order, and the resulting
 * message list is ordered by ascending bit position. An enum preserves that order by
 * construction, where a {@code HashMap} would not.
 */
public enum RailgunFlag {

  FLAG_DOMAIN_MAP_USED(0x01, "map.file used to change IP"),
  FLAG_DEFAULT_IP_USED(0x02, "map.file default IP used"),
  FLAG_HOST_CHANGE(0x04, "Host name change"),
  FLAG_REUSED_CONNECTION(0x08, "Existing connection reused"),
  FLAG_HAD_DICTIONARY(0x10, "Railgun sender sent dictionary"),
  FLAG_WAS_CACHED(0x20, "Dictionary found in memcache"),
  FLAG_RESTART_CONNECTION(0x40, "Restarted broken origin connection");

  private final int position;
  private final String message;

  RailgunFlag(int position, String message) {
    this.position = position;
    this.message = message;
  }

  /** The bit this flag occupies in the bitset. */
  public int position() {
    return position;
  }

  /** The human-readable text the popup and the debug log show for this flag. */
  public String message() {
    return message;
  }
}
