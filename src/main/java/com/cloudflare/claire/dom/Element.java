package com.cloudflare.claire.dom;

import java.util.function.Consumer;

/**
 * A DOM element, narrowed to the members {@code options.js} and {@code page-action-popup.js}
 * read or write.
 */
public interface Element {

  /** {@code element.checked} on a checkbox input. */
  boolean isChecked();

  /** {@code element.checked = value}. */
  void setChecked(boolean value);

  /** {@code element.value} on a form control. */
  String getValue();

  /** {@code element.value = text}. */
  void setValue(String text);

  /** {@code element.textContent = text}. */
  void setTextContent(String text);

  /** {@code element.textContent}. */
  String getTextContent();

  /** {@code element.src = url} on an image. */
  void setSrc(String url);

  /** {@code element.href = url} on an anchor. */
  void setHref(String url);

  /** {@code element.classList.add(className)}. */
  void addClass(String className);

  /** {@code element.classList.contains(className)}. */
  boolean hasClass(String className);

  /** {@code element.onclick = handler}. */
  void setOnClick(Consumer<MouseEvent> handler);

  /** {@code element.matches(selector)}. */
  boolean matches(String selector);

  /** {@code element.dataset[name]}, or {@code null} when the attribute is absent. */
  String dataset(String name);

  /** {@code element.select()} &mdash; selects the control's text ahead of a copy command. */
  void select();
}
