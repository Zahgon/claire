package com.cloudflare.claire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Everything {@code Request.processRailgunHeader} decodes out of the {@code CF-RAILGUN} header.
 *
 * <p>Mutable and partially populated on purpose. The source creates
 * {@code this.railgunMetaData = {}} and then returns early when the header value is not a string,
 * leaving an object with no properties at all; and even on the happy path {@code version} is
 * whatever {@code parts[3]} held, which may be {@code undefined}. Both states are reachable and
 * both are observable from the popup, so they are represented rather than validated away.
 *
 * <p>{@code flags} is a {@code double} rather than an {@code int} because it comes from
 * {@code parseInt}, which yields {@code NaN} for a header with no numeric field &mdash; and the
 * popup can display that {@code NaN}.
 */
public final class RailgunMetaData {

  private Boolean normal;
  private String id;
  private String version;
  private String compression;
  private String time;

  // Both start unset, and unset is distinct from every value they can later hold. The source
  // creates this object as an empty literal and returns early when the header carries no string,
  // leaving the properties absent; reading `messages` in that state throws, and the extension's
  // request handler aborts. Defaulting these to NaN and an empty list swallowed that failure.
  private Double flags;
  private List<String> messages;

  /** {@code railgunMetaData.normal} &mdash; {@code null} while the object is still empty. */
  public Boolean normal() {
    return normal;
  }

  void setNormal(boolean value) {
    this.normal = value;
  }

  /** {@code railgunMetaData.id} &mdash; the sender identifier, {@code parts[0]}. */
  public String id() {
    return id;
  }

  void setId(String value) {
    this.id = value;
  }

  /** {@code railgunMetaData.version} &mdash; {@code null} when the header omitted it. */
  public String version() {
    return version;
  }

  void setVersion(String value) {
    this.version = value;
  }

  /** {@code railgunMetaData.compression}, e.g. {@code "54%"}. Only set in the non-normal form. */
  public String compression() {
    return compression;
  }

  void setCompression(String value) {
    this.compression = value;
  }

  /** {@code railgunMetaData.time}, e.g. {@code "700sec"}. Only set in the non-normal form. */
  public String time() {
    return time;
  }

  void setTime(String value) {
    this.time = value;
  }

  /**
   * {@code railgunMetaData.flags} &mdash; {@code null} when the header carried no value at all and
   * the property was never assigned, {@code NaN} when it was assigned from a field that held no
   * number. The two are different states and the source distinguishes them.
   */
  public Double flags() {
    return flags;
  }

  void setFlags(double value) {
    this.flags = value;
  }

  /**
   * {@code railgunMetaData.messages} &mdash; one entry per set flag, in bit order, or {@code null}
   * when the property was never assigned. Callers that use it unguarded fail on {@code null},
   * which is what the source does.
   */
  public List<String> messages() {
    return messages;
  }

  void setMessages(List<String> value) {
    this.messages = Collections.unmodifiableList(new ArrayList<>(value));
  }

  @Override
  public String toString() {
    return "RailgunMetaData{normal=" + normal
        + ", id=" + id
        + ", version=" + version
        + ", compression=" + compression
        + ", time=" + time
        + ", flags=" + flags
        + ", messages=" + messages
        + '}';
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RailgunMetaData that)) {
      return false;
    }
    return Objects.equals(normal, that.normal)
        && Objects.equals(id, that.id)
        && Objects.equals(version, that.version)
        && Objects.equals(compression, that.compression)
        && Objects.equals(time, that.time)
        && Objects.equals(flags, that.flags)
        && Objects.equals(messages, that.messages);
  }

  @Override
  public int hashCode() {
    return Objects.hash(normal, id, version, compression, time, flags, messages);
  }
}
