package com.cloudflare.claire.dom;

/** A DOM click event, narrowed to the members the extension's handlers use. */
public interface MouseEvent {

  /** {@code event.target}. */
  Element target();

  /** {@code event.preventDefault()}. */
  void preventDefault();

  /** {@code event.stopImmediatePropagation()}. */
  void stopImmediatePropagation();
}
