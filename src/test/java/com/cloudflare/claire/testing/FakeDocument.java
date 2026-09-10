package com.cloudflare.claire.testing;

import com.cloudflare.claire.dom.Document;
import com.cloudflare.claire.dom.Element;
import com.cloudflare.claire.dom.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A document stand-in that hands out {@link FakeElement}s on demand.
 *
 * <p>Unknown ids are created rather than rejected, which mirrors a page whose markup already
 * contains every element the script asks for and keeps a test from having to enumerate them.
 */
public final class FakeDocument implements Document {

  private final Map<String, FakeElement> byId = new LinkedHashMap<>();
  private final Map<String, FakeElement> bySelector = new LinkedHashMap<>();
  private final List<String> executedCommands = new ArrayList<>();
  private Consumer<MouseEvent> clickListener;

  @Override
  public Element getElementById(String id) {
    return element(id);
  }

  /** The element with this id, creating it on first mention. */
  public FakeElement element(String id) {
    return byId.computeIfAbsent(id, FakeElement::new);
  }

  @Override
  public Element querySelector(String selector) {
    return bySelector.computeIfAbsent(selector, FakeElement::new);
  }

  /** The element a {@code querySelector} call would return, creating it on first mention. */
  public FakeElement selected(String selector) {
    return bySelector.computeIfAbsent(selector, FakeElement::new);
  }

  @Override
  public void addClickListener(Consumer<MouseEvent> handler) {
    this.clickListener = handler;
  }

  /** Dispatches a click at the given element, as the browser's bubbling listener would. */
  public void dispatchClick(FakeMouseEvent event) {
    if (clickListener != null) {
      clickListener.accept(event);
    }
  }

  @Override
  public boolean execCommand(String command) {
    executedCommands.add(command);
    return true;
  }

  public List<String> executedCommands() {
    return List.copyOf(executedCommands);
  }
}
