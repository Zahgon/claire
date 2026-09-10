package com.cloudflare.claire;

import com.cloudflare.claire.browser.ChromeApi;
import com.cloudflare.claire.browser.Tab;
import com.cloudflare.claire.browser.TabQueryInfo;
import com.cloudflare.claire.dom.Document;
import com.cloudflare.claire.dom.Element;
import com.cloudflare.claire.dom.LocalStorage;
import com.cloudflare.claire.dom.MouseEvent;
import com.cloudflare.claire.js.JsValues;
import java.util.List;
import java.util.Objects;

/**
 * The page-action popup, migrated from {@code source/page-action-popup.js}.
 *
 * <p>Reads the active tab's {@link Request} back off the background page and renders it, hiding
 * whichever cards do not apply to the request in hand.
 */
public final class PageActionPopup {

  private static final String HIDDEN = "hidden";
  private static final String COPY_BUTTON_SELECTOR = ".copy-button";
  private static final String COPY_ID_DATASET = "copyId";
  private static final String COPY_COMMAND = "copy";

  private final Document document;
  private final ExtensionEnvironment env;

  public PageActionPopup(Document document, ExtensionEnvironment env) {
    this.document = Objects.requireNonNull(document, "document");
    this.env = Objects.requireNonNull(env, "env");
  }

  /** Runs the popup's load-time script: honour the guide preference, wire copy, then render. */
  public static PageActionPopup start(Document document, ExtensionEnvironment env) {
    PageActionPopup popup = new PageActionPopup(document, env);

    if (LocalStorage.YES.equals(env.localStorage().getItem(LocalStorage.HIDE_GUIDE))) {
      document.getElementById("claireInfoImage").addClass(HIDDEN);
    }

    document.addClickListener(popup::onClick);

    // Get the current tab's ID and extract its request info from the extension object.
    TabQueryInfo queryInfo = new TabQueryInfo(true, ChromeApi.WINDOW_ID_CURRENT);
    env.chrome().tabsQuery(queryInfo, popup::render);

    return popup;
  }

  /** Copies a field's text when its adjacent copy button is clicked. */
  void onClick(MouseEvent event) {
    Element target = event.target();

    if (target.matches(COPY_BUTTON_SELECTOR)) {
      String copyId = target.dataset(COPY_ID_DATASET);
      Element copyElement = document.getElementById(copyId);

      copyElement.select();
      document.execCommand(COPY_COMMAND);

      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }

  /** Renders the request belonging to the queried tab. */
  void render(List<Tab> tabs) {
    int tabId = tabs.get(0).id();
    Request request = env.chrome().extensionGetBackgroundPage().requests().get(tabId);

    document.getElementById("ip").setValue(request.getServerIP());
    document.querySelector("#claireInfoImage img").setSrc(request.getPopupPath() + ".png");

    // Show the Ray ID & location.
    if (request.servedByCloudFlare()) {
      document.getElementById("rayID").setValue(JsValues.orUndefined(request.getRayID()));
      document.getElementById("locationCode")
          .setTextContent(JsValues.orEmpty(request.getCloudFlareLocationCode()));
      document.getElementById("locationName")
          .setTextContent(JsValues.orEmpty(request.getCloudFlareLocationName()));
      document.getElementById("traceURL").setHref(request.getCloudFlareTrace());
    } else {
      document.getElementById("ray").addClass(HIDDEN);
      document.getElementById("loc").addClass(HIDDEN);
      document.getElementById("actions").addClass(HIDDEN);
    }

    // Show Railgun related info.
    if (request.servedByRailgun()) {
      RailgunMetaData railgunMetaData = request.getRailgunMetaData();
      document.getElementById("railgunID")
          .setTextContent(JsValues.orEmpty(railgunMetaData.id()));
      if (!Boolean.TRUE.equals(railgunMetaData.normal())) {
        document.getElementById("railgunCompression")
            .setTextContent(JsValues.orEmpty(railgunMetaData.compression()));
        document.getElementById("railgunTime")
            .setTextContent(JsValues.orEmpty(railgunMetaData.time()));
      }
    } else {
      document.getElementById("railgun").addClass(HIDDEN);
    }
  }
}
