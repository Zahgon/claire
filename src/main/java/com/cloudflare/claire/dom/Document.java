package com.cloudflare.claire.dom;

import java.util.function.Consumer;

/** The {@code document} global, narrowed to the members the extension pages use. */
public interface Document {

  /** {@code document.getElementById(id)}, or {@code null} when nothing matches. */
  Element getElementById(String id);

  /** {@code document.querySelector(selector)}, or {@code null} when nothing matches. */
  Element querySelector(String selector);

  /** {@code document.addEventListener('click', handler)}. */
  void addClickListener(Consumer<MouseEvent> handler);

  /** {@code document.execCommand(command)}. */
  boolean execCommand(String command);
}
