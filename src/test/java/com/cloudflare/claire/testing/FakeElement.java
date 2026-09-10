package com.cloudflare.claire.testing;

import com.cloudflare.claire.dom.Element;
import com.cloudflare.claire.dom.MouseEvent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** A DOM element stand-in that records every write the extension makes to it. */
public final class FakeElement implements Element {

  private final String id;
  private final Set<String> classes = new LinkedHashSet<>();
  private final Set<String> selectors = new LinkedHashSet<>();
  private final Map<String, String> dataset = new LinkedHashMap<>();

  private boolean checked;
  private String value = "";
  private String textContent = "";
  private String src;
  private String href;
  private Consumer<MouseEvent> onClick;
  private int selectCount;

  public FakeElement(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  /** Declares that this element answers true to {@code element.matches(selector)}. */
  public FakeElement matching(String selector) {
    selectors.add(selector);
    return this;
  }

  /** Sets a {@code data-*} attribute, reachable as {@code element.dataset[name]}. */
  public FakeElement withData(String name, String dataValue) {
    dataset.put(name, dataValue);
    return this;
  }

  /** Simulates a user click, which is what the options page's handler reacts to. */
  public void click() {
    checked = !checked;
    if (onClick != null) {
      onClick.accept(new FakeMouseEvent(this));
    }
  }

  @Override
  public boolean isChecked() {
    return checked;
  }

  @Override
  public void setChecked(boolean newValue) {
    this.checked = newValue;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public void setValue(String text) {
    this.value = text;
  }

  @Override
  public void setTextContent(String text) {
    this.textContent = text;
  }

  @Override
  public String getTextContent() {
    return textContent;
  }

  @Override
  public void setSrc(String url) {
    this.src = url;
  }

  public String src() {
    return src;
  }

  @Override
  public void setHref(String url) {
    this.href = url;
  }

  public String href() {
    return href;
  }

  @Override
  public void addClass(String className) {
    classes.add(className);
  }

  @Override
  public boolean hasClass(String className) {
    return classes.contains(className);
  }

  @Override
  public void setOnClick(Consumer<MouseEvent> handler) {
    this.onClick = handler;
  }

  @Override
  public boolean matches(String selector) {
    return selectors.contains(selector);
  }

  @Override
  public String dataset(String name) {
    return dataset.get(name);
  }

  @Override
  public void select() {
    selectCount++;
  }

  public int selectCount() {
    return selectCount;
  }
}
