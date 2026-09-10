package com.cloudflare.claire.testing;

import com.cloudflare.claire.dom.Element;
import com.cloudflare.claire.dom.MouseEvent;

/** A click event whose suppression calls a test can assert were made. */
public final class FakeMouseEvent implements MouseEvent {

  private final Element target;
  private boolean defaultPrevented;
  private boolean propagationStopped;

  public FakeMouseEvent(Element target) {
    this.target = target;
  }

  @Override
  public Element target() {
    return target;
  }

  @Override
  public void preventDefault() {
    defaultPrevented = true;
  }

  @Override
  public void stopImmediatePropagation() {
    propagationStopped = true;
  }

  public boolean defaultPrevented() {
    return defaultPrevented;
  }

  public boolean propagationStopped() {
    return propagationStopped;
  }
}
